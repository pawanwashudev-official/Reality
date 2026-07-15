// ============================================================
// Reality Notification Bridge — Cloudflare Worker
// Routes:
//   POST /api/register-fcm-token  → App registers device FCM token
//   POST /webhook/calendar        → Google Calendar change webhook
// ============================================================

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type",
        },
      });
    }

    // Root health check
    if (url.pathname === "/") {
      return new Response("Reality Notification Bridge is running.", { status: 200 });
    }

    // ============================================================
    // ROUTE: App registers FCM token for the authenticated user
    // POST /api/register-fcm-token
    // Body: { userId, backupPassword, fcmToken }
    // ============================================================
    if (url.pathname === "/api/register-fcm-token" && request.method === "POST") {
      let body = {};
      try {
        body = await request.json();
      } catch {
        return jsonError("Invalid JSON body", 400);
      }

      const { userId, backupPassword, fcmToken } = body;

      if (!userId || !backupPassword || !fcmToken) {
        return jsonError("Missing required fields: userId, backupPassword, fcmToken", 400);
      }

      // Verify credentials using same HMAC logic as proxy worker
      const verified = await verifyUserCredentials(userId, backupPassword, env);
      if (!verified) {
        return jsonError("Invalid credentials", 401);
      }

      // Check user has active elite subscription
      if (!env.DB) return jsonError("DB not configured", 500);

      let userRow;
      try {
        userRow = await env.DB.prepare(
          'SELECT status, fcmToken FROM "Reality Elite members management" WHERE userId = ?'
        ).bind(userId).first();
      } catch (e) {
        return jsonError("DB error: " + e.message, 500);
      }

      if (!userRow) return jsonError("User not found", 404);
      if (userRow.status !== "elite") return jsonError("Active Elite subscription required for calendar sync", 403);

      // Store FCM token
      try {
        await env.DB.prepare(
          'UPDATE "Reality Elite members management" SET fcmToken = ? WHERE userId = ?'
        ).bind(fcmToken, userId).run();
      } catch (e) {
        return jsonError("Failed to store FCM token: " + e.message, 500);
      }

      console.log(`FCM token registered for user: ${userId.substring(0, 8)}...`);
      return jsonResponse({ success: true, message: "FCM token registered successfully" });
    }

    // ============================================================
    // ROUTE: Google Calendar Webhook
    // POST /webhook/calendar
    // Google sends x-goog-channel-token = userId
    // ============================================================
    if (url.pathname === "/webhook/calendar" && request.method === "POST") {
      const resourceState = request.headers.get("x-goog-resource-state");
      const channelToken = request.headers.get("x-goog-channel-token"); // This IS the userId
      const channelId = request.headers.get("x-goog-channel-id");

      console.log(`Calendar webhook received. State: ${resourceState}, Channel: ${channelId}`);

      // Google sends a "sync" ping when webhook is first registered — just acknowledge
      if (resourceState === "sync") {
        return new Response("Sync acknowledged", { status: 200 });
      }

      // Validate webhook token matches our secret prefix + userId
      if (!channelToken) {
        return new Response("Missing channel token", { status: 400 });
      }

      // channelToken format: "reality-{userId}"
      if (!channelToken.startsWith("reality-")) {
        return new Response("Invalid token format", { status: 403 });
      }

      const userId = channelToken.replace("reality-", "");
      if (!userId) return new Response("Could not extract userId", { status: 400 });

      // Look up user's FCM token from D1
      if (!env.DB) {
        console.error("DB binding missing");
        return new Response("DB not configured", { status: 500 });
      }

      let userRow;
      try {
        userRow = await env.DB.prepare(
          'SELECT fcmToken, status FROM "Reality Elite members management" WHERE userId = ?'
        ).bind(userId).first();
      } catch (e) {
        console.error("DB lookup error:", e.message);
        return new Response("DB error", { status: 500 });
      }

      if (!userRow || !userRow.fcmToken) {
        console.log(`No FCM token found for userId: ${userId.substring(0, 8)}... — skipping`);
        return new Response("No FCM token for user", { status: 200 }); // 200 so Google doesn't retry
      }

      if (userRow.status !== "elite") {
        console.log(`User no longer elite, skipping FCM push`);
        return new Response("User not elite", { status: 200 });
      }

      // Send silent FCM push notification
      try {
        await sendFcmPush(userRow.fcmToken, env);
        console.log(`FCM calendar sync push sent for userId: ${userId.substring(0, 8)}...`);
      } catch (e) {
        console.error("FCM send failed:", e.message);
        return new Response("FCM error: " + e.message, { status: 500 });
      }

      return new Response("Notification sent", { status: 200 });
    }

    return new Response("Not Found", { status: 404 });
  }
};

// ============================================================
// HELPER: Verify userId + backupPassword (same HMAC as proxy worker)
// ============================================================
async function verifyUserCredentials(userId, backupPassword, env) {
  if (!env.APP_SECRET_PEPPER) return false;

  try {
    const encoder = new TextEncoder();
    const secretKeyData = encoder.encode(env.APP_SECRET_PEPPER);

    const cryptoKey = await crypto.subtle.importKey(
      "raw",
      secretKeyData,
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["sign"]
    );

    // Re-derive expected backupPassword from userId using same logic as proxy
    const pwSignature = await crypto.subtle.sign(
      "HMAC",
      cryptoKey,
      encoder.encode(userId)
    );
    const pwHashHex = Array.from(new Uint8Array(pwSignature))
      .map(b => b.toString(16).padStart(2, "0"))
      .join("");
    const expectedPassword = pwHashHex.substring(0, 32);

    return backupPassword === expectedPassword;
  } catch {
    return false;
  }
}

// ============================================================
// HELPER: Send silent FCM push using Firebase HTTP v1 API
// ============================================================
async function sendFcmPush(fcmToken, env) {
  const accessToken = await getFirebaseAccessToken(env.FIREBASE_SERVICE_ACCOUNT);
  const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);
  const projectId = serviceAccount.project_id;

  const fcmUrl = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

  const payload = {
    message: {
      token: fcmToken,
      data: {
        action: "SYNC_CALENDAR"
      },
      android: {
        priority: "high"
      }
    }
  };

  const response = await fetch(fcmUrl, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${accessToken}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  const resJson = await response.json();
  if (!response.ok) {
    throw new Error(`FCM error: ${JSON.stringify(resJson)}`);
  }
}

// ============================================================
// HELPER: Get Firebase OAuth2 access token from service account
// Uses Web Crypto RS256 JWT — no external libraries needed
// ============================================================
async function getFirebaseAccessToken(serviceAccountJson) {
  const serviceAccount = JSON.parse(serviceAccountJson);
  const privateKeyPem = serviceAccount.private_key;

  const pemHeader = "-----BEGIN PRIVATE KEY-----";
  const pemFooter = "-----END PRIVATE KEY-----";
  const pemContents = privateKeyPem
    .replace(pemHeader, "")
    .replace(pemFooter, "")
    .replace(/\s/g, "");

  const binaryDer = base64ToArrayBuffer(pemContents);

  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    binaryDer,
    { name: "RSASSA-PKCS1-v1_5", hash: { name: "SHA-256" } },
    false,
    ["sign"]
  );

  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claim = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: serviceAccount.token_uri,
    exp: now + 3600,
    iat: now
  };

  const encodedHeader = base64UrlEncode(JSON.stringify(header));
  const encodedClaim = base64UrlEncode(JSON.stringify(claim));
  const tokenInput = `${encodedHeader}.${encodedClaim}`;

  const encoder = new TextEncoder();
  const signatureBuffer = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    encoder.encode(tokenInput)
  );

  const jwt = `${tokenInput}.${base64UrlEncodeFromBuffer(signatureBuffer)}`;

  const tokenResponse = await fetch(serviceAccount.token_uri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`
  });

  const tokenData = await tokenResponse.json();
  if (tokenData.error) {
    throw new Error(`Token exchange failed: ${tokenData.error_description || tokenData.error}`);
  }
  return tokenData.access_token;
}

// ============================================================
// HELPERS: Base64 utilities
// ============================================================
function base64UrlEncode(str) {
  return base64UrlEncodeFromBuffer(new TextEncoder().encode(str));
}

function base64UrlEncodeFromBuffer(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function base64ToArrayBuffer(b64) {
  const binaryString = atob(b64);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes.buffer;
}

// ============================================================
// HELPERS: Response builders
// ============================================================
function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
  });
}

function jsonError(message, status = 400) {
  return new Response(JSON.stringify({ error: message }), {
    status,
    headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
  });
}
