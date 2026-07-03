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
        
        const cryptoKey = await crypto.subtle.importKey(
          "raw",
          secretKeyData,
          { name: "HMAC", hash: "SHA-256" },
          false,
          ["sign"]
        );

        const normalizedEmail = email.toLowerCase().trim();

        const idSignature = await crypto.subtle.sign(
          "HMAC",
          cryptoKey,
          encoder.encode(normalizedEmail)
        );
        const idHashHex = Array.from(new Uint8Array(idSignature))
          .map(b => b.toString(16).padStart(2, '0'))
          .join('');
        const userId = idHashHex.substring(0, 16);

        const pwSignature = await crypto.subtle.sign(
          "HMAC",
          cryptoKey,
          encoder.encode(userId)
        );
        const pwHashHex = Array.from(new Uint8Array(pwSignature))
          .map(b => b.toString(16).padStart(2, '0'))
          .join('');
        const backupPassword = pwHashHex.substring(0, 32);

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
      // ROUTE: NATIVE SUBSCRIPTION MANAGEMENT (D1 Drop-in for GAS)
      // ============================================================
      if (url.pathname === "/license") {
        
        // Handles verification (GET) - Exactly matches your old GAS doGet()
        if (request.method === "GET") {
          const userId = url.searchParams.get("userId");
          const vCodeParam = url.searchParams.get("vCode");
          const vCode = parseInt(vCodeParam, 10);

          if (!userId || isNaN(vCode) || vCode <= 0) {
            return new Response("INVALID", { headers: { "Access-Control-Allow-Origin": "*" } });
          }

          // Fetch matching row from D1 database
          const row = await env.DB.prepare(
            "SELECT userId, status FROM licenses WHERE vCode = ?"
          ).bind(vCode).first();

          if (!row) {
            return new Response("NOT_FOUND", { headers: { "Access-Control-Allow-Origin": "*" } });
          }

          if (row.userId === userId && row.status === "V") {
            return new Response("SUCCESS", { headers: { "Access-Control-Allow-Origin": "*" } });
          } else {
            return new Response("INVALID", { headers: { "Access-Control-Allow-Origin": "*" } });
          }
        }

        // Handles subscription submission (POST) - Exactly matches your old GAS doPost()
        if (request.method === "POST") {
          let incomingData = {};
          try {
            incomingData = await request.json();
          } catch (e) {
            return new Response(JSON.stringify({ status: "ERROR", message: "Invalid JSON payload" }), {
              status: 400,
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
            });
          }

          const userId = incomingData.userId;
          const transactionId = incomingData.transactionId;
          let customNote = incomingData.customNote || "";

          if (!userId || !transactionId) {
            return new Response(JSON.stringify({ status: "ERROR", message: "Missing fields" }), {
              status: 400,
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
            });
          }

          if (customNote.length > 200) {
            customNote = customNote.substring(0, 200);
          }

          // Insert row or update status to verified if the user records another transaction
          const info = await env.DB.prepare(`
            INSERT INTO licenses (date, userId, status, transactionId, customNote) 
            VALUES (?, ?, 'V', ?, ?)
            ON CONFLICT(userId) DO UPDATE SET 
              date = excluded.date,
              transactionId = excluded.transactionId,
              customNote = excluded.customNote,
              status = 'V'
          `).bind(new Date().toISOString(), userId, transactionId, customNote).run();

          // Get primary key auto-increment ID to replicate sheet row indexing perfectly
          let verificationCode = info.meta.last_row_id;

          // If entry updated an existing row instead of adding a new index row
          if (verificationCode === 0 || !verificationCode) {
            const existingRow = await env.DB.prepare("SELECT vCode FROM licenses WHERE userId = ?").bind(userId).first();
            verificationCode = existingRow ? existingRow.vCode : 1;
          }

          return new Response(
            JSON.stringify({ status: "SUCCESS", verificationCode: verificationCode }),
            { 
              status: 200, 
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } 
            }
          );
        }
      }

      return new Response("Not Found", { status: 404 });

    } catch (error) {
      return new Response(JSON.stringify({ error: "Proxy failure", details: error.message }), { 
        status: 500,
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
      });
    }
  }
};
