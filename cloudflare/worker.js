export default {
  async fetch(request, env) {
    // Handle CORS preflight requests universally
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type",
        },
      });
    }

    const url = new URL(request.url);

    try {
      // ============================================================
      // ROUTE: NEW SECURE IDENTITY GENERATION (Deterministic Hashing)
      // ============================================================
      if (url.pathname === "/api/generate-identity" && request.method === "POST") {
        let incomingData = {};
        try {
          incomingData = await request.json();
        } catch (e) {
          return new Response(JSON.stringify({ error: "Invalid JSON payload" }), {
            status: 400,
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
          });
        }

        const email = incomingData.email;
        if (!email || typeof email !== "string") {
          return new Response(JSON.stringify({ error: "Email string is required" }), {
            status: 400,
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
          });
        }

        if (!env.APP_SECRET_PEPPER) {
          return new Response(JSON.stringify({ error: "Server misconfiguration: missing secret pepper" }), {
            status: 500,
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
          });
        }

        const encoder = new TextEncoder();
        const secretKeyData = encoder.encode(env.APP_SECRET_PEPPER);
        
        // Import runtime secret key as a usable WebCrypto HMAC key object
        const cryptoKey = await crypto.subtle.importKey(
          "raw",
          secretKeyData,
          { name: "HMAC", hash: "SHA-256" },
          false,
          ["sign"]
        );

        // 1. Normalize email strictly (ensures inputs generate identical hashes every time)
        const normalizedEmail = email.toLowerCase().trim();

        // 2. Compute the unique deterministic User ID (Fixed length 16 characters)
        const idSignature = await crypto.subtle.sign(
          "HMAC",
          cryptoKey,
          encoder.encode(normalizedEmail)
        );
        const idHashHex = Array.from(new Uint8Array(idSignature))
          .map(b => b.toString(16).padStart(2, '0'))
          .join('');
        const userId = idHashHex.substring(0, 16);

        // 3. Compute the unique deterministic Backup Password (Fixed length 32 characters)
        const pwSignature = await crypto.subtle.sign(
          "HMAC",
          cryptoKey,
          encoder.encode(userId)
        );
        const pwHashHex = Array.from(new Uint8Array(pwSignature))
          .map(b => b.toString(16).padStart(2, '0'))
          .join('');
        const backupPassword = pwHashHex.substring(0, 32);

        // Deliver both entities cleanly to preserve Cloudflare Workers execution limits
        return new Response(
          JSON.stringify({ userId: userId, backupPassword: backupPassword }), 
          { 
            status: 200, 
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } 
          }
        );
      }

      // ============================================================
      // ROUTE: GOOGLE OAUTH AUTH URL
      // ============================================================
      if (url.pathname === "/oauth/auth" && request.method === "GET") {
        const scopes = url.searchParams.get("scope") || "email profile";
        const redirectUri = url.searchParams.get("redirect_uri") || "http://127.0.0.1:8080/Callback";

        const authUrl = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${env.GCP_CLIENT_ID}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&scope=${encodeURIComponent(scopes)}&access_type=offline`;

        return Response.redirect(authUrl, 302);
      }

      // ============================================================
      // ROUTE: GOOGLE OAUTH TOKEN EXCHANGE
      // ============================================================
      if (url.pathname === "/oauth/token" && request.method === "POST") {
        let incomingData = {};
        const contentType = request.headers.get("content-type") || "";

        if (contentType.includes("application/x-www-form-urlencoded")) {
          const formData = await request.formData();
          for (const [key, value] of formData.entries()) {
            incomingData[key] = value;
          }
        } else {
          incomingData = await request.json();
        }
        
        const requestBody = {
          client_id: env.GCP_CLIENT_ID,
          client_secret: env.GCP_CLIENT_SECRET,
        };

        if (incomingData.grant_type === "refresh_token") {
            requestBody.grant_type = "refresh_token";
            requestBody.refresh_token = incomingData.refresh_token;
        } else {
            requestBody.grant_type = "authorization_code";
            requestBody.code = incomingData.code;
            requestBody.redirect_uri = incomingData.redirect_uri || "http://127.0.0.1:8080/Callback";
        }

        const googleResponse = await fetch("https://oauth2.googleapis.com/token", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(requestBody)
        });

        const tokens = await googleResponse.text();
        return new Response(tokens, {
          status: googleResponse.status,
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
        });
      }

      // ============================================================
      // ROUTE: SUBSCRIPTION VERIFICATION/SUBMISSION
      // ============================================================
      if (url.pathname === "/license") {
        const targetUrl = env.NEW_REALITY_LICENSE_URL;

        // Handles verification (GET)
        if (request.method === "GET") {
          const backendResponse = await fetch(`${targetUrl}${url.search}`, { method: "GET" });
          const responseData = await backendResponse.text();
          return new Response(responseData, { status: backendResponse.status, headers: { "Access-Control-Allow-Origin": "*" } });
        }

        // Handles subscription submission (POST)
        if (request.method === "POST") {
          const incomingData = await request.json();
          const backendResponse = await fetch(targetUrl, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              ...incomingData,
              client_id: env.GCP_CLIENT_ID,
              client_secret: env.GCP_CLIENT_SECRET
            })
          });
          const responseData = await backendResponse.text();
          return new Response(responseData, { status: backendResponse.status, headers: { "Access-Control-Allow-Origin": "*" } });
        }
      }

      return new Response("Not Found", { status: 404 });

    } catch (error) {
      return new Response(JSON.stringify({ error: "Proxy failure", details: error.message }), { status: 500 });
    }
  }
};
