
      // Helper function to verify auth
      async function verifyAuth(userId, password, pepper) {
        if (!userId || !password || !pepper) return false;
        const encoder = new TextEncoder();
        const secretKeyData = encoder.encode(pepper);
        const cryptoKey = await crypto.subtle.importKey(
          "raw",
          secretKeyData,
          { name: "HMAC", hash: "SHA-256" },
          false,
          ["sign"]
        );
        const pwSignature = await crypto.subtle.sign(
          "HMAC",
          cryptoKey,
          encoder.encode(userId)
        );
        const pwHashHex = Array.from(new Uint8Array(pwSignature))
          .map(b => b.toString(16).padStart(2, '0'))
          .join('');
        const expectedPassword = pwHashHex.substring(0, 32);
        return password === expectedPassword;
      }

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

        const idToken = incomingData.idToken;
        if (!idToken || typeof idToken !== "string") {
          return new Response(JSON.stringify({ error: "idToken string is required" }), {
            status: 400,
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
          });
        }

        let email = "";
        try {
            const verifyRes = await fetch("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken);
            if (!verifyRes.ok) {
                return new Response(JSON.stringify({ error: "Invalid idToken" }), {
                    status: 400,
                    headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
                });
            }
            const tokenData = await verifyRes.json();
            email = tokenData.email;
            if (!email) {
                throw new Error("No email in token");
            }
        } catch (e) {
            return new Response(JSON.stringify({ error: "Failed to verify idToken or extract email" }), {
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
      // ROUTE: WEBSITE PUBLIC PRO MEMBERS LIST
      // ============================================================
      if (url.pathname === "/api/pro-members" && request.method === "GET") {
        // Fetch only safe, shareable details from D1
        const { results } = await env.DB.prepare(
          "SELECT userId, date, status FROM licenses ORDER BY vCode DESC"
        ).all();

        const totalMembers = results.length;

        return new Response(
          JSON.stringify({ totalMembers, members: results }),
          {
            status: 200,
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
          }
        );
      }

      // ============================================================
      // ROUTE: NATIVE SUBSCRIPTION MANAGEMENT
      // ============================================================
      if (url.pathname === "/license") {
        
        // Handles verification (GET)
        if (request.method === "GET") {
          const userId = url.searchParams.get("userId");
          const password = url.searchParams.get("password");

          if (!userId || !password) {
            return new Response("INVALID", { headers: { "Access-Control-Allow-Origin": "*" } });
          }

          if (!(await verifyAuth(userId, password, env.APP_SECRET_PEPPER))) {
            return new Response("UNAUTHORIZED", { status: 401, headers: { "Access-Control-Allow-Origin": "*" } });
          }

          const row = await env.DB.prepare(
            "SELECT userId, status, expiryDate FROM licenses WHERE userId = ?"
          ).bind(userId).first();

          if (!row) {
            return new Response("NOT_FOUND", { headers: { "Access-Control-Allow-Origin": "*" } });
          }

          if (row.status === "V") {
            return new Response(JSON.stringify({ status: "SUCCESS", expiryDate: row.expiryDate }), {
                headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
            });
          } else if (row.status === "P") {
            return new Response(JSON.stringify({ status: "PENDING" }), {
                headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
            });
          } else {
            return new Response("INVALID", { headers: { "Access-Control-Allow-Origin": "*" } });
          }
        }

        // Handles subscription submission (POST)
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
          const password = incomingData.password;
          const status = incomingData.status; // "P" for register, "V" for purchase
          
          if (!userId || !password || !status) {
            return new Response(JSON.stringify({ status: "ERROR", message: "Missing fields" }), {
              status: 400,
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
            });
          }

          if (!(await verifyAuth(userId, password, env.APP_SECRET_PEPPER))) {
             return new Response(JSON.stringify({ status: "ERROR", message: "Unauthorized" }), {
              status: 401,
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
            });
          }

          if (status === "P") {
              // Register
              await env.DB.prepare(`
                INSERT INTO licenses (date, userId, status)
                VALUES (?, ?, ?)
                ON CONFLICT(userId) DO UPDATE SET
                  status = excluded.status
              `).bind(new Date().getTime().toString(), userId, "P").run();

              return new Response(
                JSON.stringify({ status: "SUCCESS" }),
                { status: 200, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } }
              );
          } else if (status === "V") {
              // Purchase
              const transactionId = incomingData.transactionId;
              const durationDays = incomingData.durationDays;

              if (!transactionId || !durationDays) {
                 return new Response(JSON.stringify({ status: "ERROR", message: "Missing purchase fields" }), {
                  status: 400,
                  headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
                });
              }

              const currentDate = new Date();
              const durationMs = durationDays * 24 * 60 * 60 * 1000;
              const expiryDateObj = new Date(currentDate.getTime() + durationMs);
              const expiryDate = expiryDateObj.getTime().toString();

              await env.DB.prepare(`
                INSERT INTO licenses (date, userId, status, transactionId, expiryDate)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(userId) DO UPDATE SET
                  status = excluded.status,
                  transactionId = excluded.transactionId,
                  expiryDate = excluded.expiryDate
              `).bind(currentDate.getTime().toString(), userId, "V", transactionId, expiryDate).run();

              return new Response(
                JSON.stringify({ status: "SUCCESS" }),
                { status: 200, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } }
              );
          } else {
             return new Response(JSON.stringify({ status: "ERROR", message: "Invalid status" }), {
              status: 400,
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
            });
          }
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
