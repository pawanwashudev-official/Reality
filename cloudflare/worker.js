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
          const googleResp = await fetch(`https://oauth2.googleapis.com/tokeninfo?id_token=${idToken}`);
          if (!googleResp.ok) throw new Error("Invalid idToken");
          const googleData = await googleResp.json();
          email = googleData.email;
          if (!email) throw new Error("No email in token");
        } catch (e) {
          return new Response(JSON.stringify({ error: "Failed to verify idToken", details: e.message }), {
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
        const secretHeader = request.headers.get("x-worker-secret");
        if (!secretHeader || secretHeader !== env.WORKER_CONNECTION_SECRET) {
          return new Response(JSON.stringify({ error: "Unauthorized access" }), {
            status: 401,
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
          });
        }

        // Fetch only safe, shareable details from D1
        const { results } = await env.DB.prepare(
          'SELECT userId, date, status FROM "Reality Elite members management" WHERE status = \'V\' ORDER BY date DESC'
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
        
        // Handles verification (GET) - Step 3
        if (request.method === "GET") {
          const userId = url.searchParams.get("userId");
          const password = url.searchParams.get("password");

          if (!userId || !password) {
            return new Response(JSON.stringify({ status: "INVALID" }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          }

          const isAuthorized = await this.verifyAuth(userId, password, env.APP_SECRET_PEPPER);
          if (!isAuthorized) {
            return new Response(JSON.stringify({ status: "UNAUTHORIZED" }), { status: 401, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          }

          const row = await env.DB.prepare(
            "SELECT userId, status, expiryDate FROM \"Reality Elite members management\" WHERE userId = ?"
          ).bind(userId).first();

          if (!row) {
            return new Response(JSON.stringify({ status: "NOT_FOUND" }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          }

          if (row.userId === userId && row.status === "V") {
            // Further verify if expiryDate is in the future
            if (row.expiryDate) {
              const parts = row.expiryDate.split("-");
              if (parts.length === 2) {
                const expiryUnix = parseInt(parts[0], 10);
                if (expiryUnix < Date.now()) {
                   return new Response(JSON.stringify({ status: "EXPIRED" }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
                }
                return new Response(JSON.stringify({ status: "SUCCESS", expiryDate: row.expiryDate }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
              } else if (parts.length === 4) {
                const yyyy = parseInt(parts[0], 10);
                const mm = parseInt(parts[1], 10) - 1; // JS months are 0-indexed
                const dd = parseInt(parts[2], 10);
                const expiryDateObj = new Date(Date.UTC(yyyy, mm, dd));

                if (expiryDateObj.getTime() < Date.now()) {
                   return new Response(JSON.stringify({ status: "EXPIRED" }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
                }
                return new Response(JSON.stringify({ status: "SUCCESS", expiryDate: row.expiryDate }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
              }
            }
            // fallback
            return new Response(JSON.stringify({ status: "SUCCESS" }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          } else if (row.userId === userId && row.status === "P") {
            return new Response(JSON.stringify({ status: "PENDING" }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          } else {
            return new Response(JSON.stringify({ status: "INVALID" }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          }
        }

        // Handles subscription submission (POST) - Step 1 & 2
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

          if (!userId || !password) {
             return new Response(JSON.stringify({ status: "ERROR", message: "Missing auth fields" }), {
              status: 400,
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
            });
          }

          const isAuthorized = await this.verifyAuth(userId, password, env.APP_SECRET_PEPPER);
          if (!isAuthorized) {
            return new Response(JSON.stringify({ status: "ERROR", message: "Unauthorized" }), {
              status: 401,
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
            });
          }

          const status = incomingData.status; // 'P' or 'V' (implied via presence of transactionId usually)

          // Check existing
          const existingRow = await env.DB.prepare(
            'SELECT status, expiryDate FROM "Reality Elite members management" WHERE userId = ?'
          ).bind(userId).first();

          // Step 1: Register
          if (status === "P") {
            if (existingRow) {
               return new Response(
                 JSON.stringify({ status: "ALREADY_REGISTERED" }),
                 { status: 200, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } }
               );
            }

            await env.DB.prepare(`
              INSERT INTO "Reality Elite members management" (userId, date, status)
              VALUES (?, ?, ?)
            `).bind(userId, new Date().toISOString(), "P").run();

             return new Response(
              JSON.stringify({ status: "SUCCESS" }),
              { status: 200, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } }
             );
          }

          // Step 2: Purchase
          if (existingRow && existingRow.status === "V" && existingRow.expiryDate) {
              const parts = existingRow.expiryDate.split("-");
              if (parts.length === 2) {
                const expiryUnix = parseInt(parts[0], 10);
                if (expiryUnix > Date.now()) {
                   return new Response(JSON.stringify({ status: "ACTIVE_SUBSCRIPTION", message: "You already have an active subscription.", code: "ACTIVE_SUBSCRIPTION" }), {
                      status: 200,
                      headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
                   });
                }
              } else if (parts.length === 4) {
                const yyyy = parseInt(parts[0], 10);
                const mm = parseInt(parts[1], 10) - 1; // JS months are 0-indexed
                const dd = parseInt(parts[2], 10);
                const expiryDateObj = new Date(Date.UTC(yyyy, mm, dd));
                if (expiryDateObj.getTime() > Date.now()) {
                   return new Response(JSON.stringify({ status: "ACTIVE_SUBSCRIPTION", message: "You already have an active subscription.", code: "ACTIVE_SUBSCRIPTION" }), {
                      status: 200,
                      headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
                   });
                }
              }
          }

          const transactionId = incomingData.transactionId;
          let customNote = incomingData.customNote || "";

          let months = incomingData.months;
          if (!months) {
             const durationDays = incomingData.durationDays || 365;
             months = Math.max(1, Math.round(durationDays / 30.416));
          }

          if (!transactionId) {
            return new Response(JSON.stringify({ status: "ERROR", message: "Missing transactionId for purchase" }), {
              status: 400,
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
            });
          }

          if (customNote.length > 200) customNote = customNote.substring(0, 200);

          const currentUnix = Date.now();
          // App uses durationMs = (365L / 12) * months * 24 * 60 * 60 * 1000
          // which is exactly what we should use here (Math.floor simulates integer division of 365/12 = 30)
          const durationMs = Math.floor(365 / 12) * months * 24 * 60 * 60 * 1000;
          const expiryUnix = currentUnix + durationMs;
          const expiryDateStr = `${expiryUnix}-${months}`;

          await env.DB.prepare(`
            INSERT INTO "Reality Elite members management" (userId, date, status, transactionId, customNote, expiryDate)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(userId) DO UPDATE SET 
              date = excluded.date,
              transactionId = excluded.transactionId,
              customNote = excluded.customNote,
              expiryDate = excluded.expiryDate,
              status = excluded.status
          `).bind(userId, new Date().toISOString(), "V", transactionId, customNote, expiryDateStr).run();

          return new Response(
            JSON.stringify({ status: "SUCCESS", verificationCode: "REGISTERED" }),
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

  },

  async verifyAuth(userId, providedPassword, secretPepper) {
    if (!userId || !providedPassword || !secretPepper) return false;
    const encoder = new TextEncoder();
    const secretKeyData = encoder.encode(secretPepper);
    const cryptoKey = await crypto.subtle.importKey(
      "raw", secretKeyData, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
    );
    const pwSignature = await crypto.subtle.sign("HMAC", cryptoKey, encoder.encode(userId));
    const expectedPassword = Array.from(new Uint8Array(pwSignature))
      .map(b => b.toString(16).padStart(2, '0'))
      .join('').substring(0, 32);
    return providedPassword === expectedPassword;
  }
};
