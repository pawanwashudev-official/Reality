// ============================================================
// Reality Notification Bridge — Cloudflare Worker
// Routes:
//   POST /api/register-fcm-token  → App registers device FCM token
//   POST /webhook/calendar        → Google Calendar change webhook
//   POST /api/send-notification   → Send custom push notification
// ============================================================

const CORS_ORIGIN = "https://reality.neubofy.in";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": CORS_ORIGIN,
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

      const { userId, connectionSecret, backupPassword, fcmToken } = body;
      const finalSecret = connectionSecret || backupPassword;
      const activeExpiry = body.activeExpiry || "0";
      const activeDuration = body.activeDuration || "0";
      const activeStatus = body.activeStatus || "N";
      const planType = body.planType || "none";

      if (!userId || !finalSecret || !fcmToken) {
        return jsonError("Missing required fields: userId, connectionSecret, fcmToken", 400);
      }

      // Verify credentials using same HMAC logic as proxy worker
      const verified = await verifyUserCredentials(userId, finalSecret, activeExpiry, activeDuration, activeStatus, planType, env);
      if (!verified) {
        return jsonError("Invalid credentials", 401);
      }

      // Check user has active elite subscription
      if (activeStatus !== "V" || parseInt(activeExpiry, 10) < Date.now()) {
        return jsonError("Access Denied: Elite Member subscription is expired or inactive.", 403);
      }

      if (!env.DB) return jsonError("DB not configured", 500);

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
    // ROUTE: Send Custom Notification
    // POST /api/send-notification
    // Body: { notificationSecret, userId, title, message }
    // ============================================================
    if (url.pathname === "/api/send-notification" && request.method === "POST") {
      let body = {};
      try {
        body = await request.json();
      } catch {
        return jsonError("Invalid JSON body", 400);
      }

      const { notificationSecret, userId, title, message } = body;

      if (!notificationSecret || !userId || !title || !message) {
        return jsonError("Missing required fields: notificationSecret, userId, title, message", 400);
      }

      if (!env.NOTIFICATION_SECRET || notificationSecret !== env.NOTIFICATION_SECRET) {
        return jsonError("Unauthorized: Invalid notificationSecret", 401);
      }

      if (!env.DB) return jsonError("DB not configured", 500);

      let userRow;
      try {
        userRow = await env.DB.prepare(
          'SELECT fcmToken FROM "Reality Elite members management" WHERE userId = ?'
        ).bind(userId).first();
      } catch (e) {
        return jsonError("DB lookup error: " + e.message, 500);
      }

      if (!userRow || !userRow.fcmToken) {
        return jsonError("No FCM token found for this user", 404);
      }

      try {
        await sendFcmPush(userRow.fcmToken, { title: String(title), message: String(message) }, env);
        console.log(`Custom notification sent for userId: ${userId.substring(0, 8)}...`);
      } catch (e) {
        return jsonError("FCM send failed: " + e.message, 500);
      }

      return jsonResponse({ success: true, message: "Custom notification sent successfully" });
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



      // Send silent FCM push notification
      try {
        await sendFcmPush(userRow.fcmToken, { action: "SYNC_CALENDAR" }, env);
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
async function generateConnectionSecret(userId, expiry, duration, status, planType, env) {
  if (!userId || !env.APP_SECRET_PEPPER) return "";
  const expiryStr = String(expiry || "0");
  const durationStr = String(duration || "0");
  const statusStr = String(status || "N");
  const planTypeStr = String(planType || "none");
  
  const encoder = new TextEncoder();
  const secretKeyData = encoder.encode(env.APP_SECRET_PEPPER);
  const cryptoKey = await crypto.subtle.importKey(
    "raw", secretKeyData, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
  );
  const msg = `${userId}:${expiryStr}:${durationStr}:${statusStr}:${planTypeStr}`;
  const signature = await crypto.subtle.sign("HMAC", cryptoKey, encoder.encode(msg));
  return Array.from(new Uint8Array(signature))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('').substring(0, 32);
}

async function verifyUserCredentials(userId, connectionSecret, expiry, duration, status, planType, env) {
  if (!userId || !connectionSecret || !env.APP_SECRET_PEPPER) return false;
  const expectedSecret = await generateConnectionSecret(userId, expiry, duration, status, planType, env);
  const encoder = new TextEncoder();
  const a = encoder.encode(connectionSecret);
  const b = encoder.encode(expectedSecret);
  
  let matched = false;
  if (a.byteLength === b.byteLength) {
      matched = crypto.subtle.timingSafeEqual(a, b);
  }
  
  if (!matched) {
    console.warn(`[SECURITY] Unauthorized access detected: Hashed credentials mismatch! User ID: ${userId}, Expiry: ${expiry}, Duration: ${duration}, Status: ${status}, PlanType: ${planType}. Attempts to bypass subscription verification logic may result in account termination and legal action.`);
  }
  return matched;
}

// ============================================================
// HELPER: Send silent FCM push using Firebase HTTP v1 API
// ============================================================
async function sendFcmPush(fcmToken, dataPayload, env) {
  if (!env.FIREBASE_SERVICE_ACCOUNT) {
    throw new Error("FIREBASE_SERVICE_ACCOUNT secret is not configured in the Cloudflare Dashboard settings for this Worker.");
  }
  const accessToken = await getFirebaseAccessToken(env.FIREBASE_SERVICE_ACCOUNT);
  const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);
  const projectId = serviceAccount.project_id;

  const fcmUrl = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

  const payload = {
    message: {
      token: fcmToken,
      data: dataPayload,
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
    headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
  });
}

function jsonError(message, status = 400) {
  return new Response(JSON.stringify({ error: message }), {
    status,
    headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
  });
}
