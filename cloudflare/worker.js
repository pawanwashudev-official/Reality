
async function verifyAuth(userId, providedPassword, env) {
  if (!userId || !providedPassword || typeof userId !== "string" || typeof providedPassword !== "string") {
    return false;
  }
  if (!env.APP_SECRET_PEPPER) {
    return false;
  }
  const encoder = new TextEncoder();
  const secretKeyData = encoder.encode(env.APP_SECRET_PEPPER);
  const cryptoKey = await crypto.subtle.importKey(
    "raw", secretKeyData, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
  );
  const pwSignature = await crypto.subtle.sign("HMAC", cryptoKey, encoder.encode(userId));
  const expectedPassword = Array.from(new Uint8Array(pwSignature))
    .map(b => b.toString(16).padStart(2, '0')).join('').substring(0, 32);
  return providedPassword === expectedPassword;
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
          const googleRes = await fetch(`https://oauth2.googleapis.com/tokeninfo?id_token=${idToken}`);
          if (!googleRes.ok) {
            return new Response(JSON.stringify({ error: "Invalid Google idToken" }), { status: 401, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          }
          const googleData = await googleRes.json();
          email = googleData.email;
          if (!email) {
            return new Response(JSON.stringify({ error: "Email not found in token" }), { status: 400, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          }
        } catch (e) {
          return new Response(JSON.stringify({ error: "Failed to verify token with Google" }), { status: 500, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
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
          "raw", secretKeyData, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
        );

        const normalizedEmail = email.toLowerCase().trim();

        const idSignature = await crypto.subtle.sign("HMAC", cryptoKey, encoder.encode(normalizedEmail));
        const idHashHex = Array.from(new Uint8Array(idSignature)).map(b => b.toString(16).padStart(2, '0')).join('');
        const userId = idHashHex.substring(0, 16);

        const pwSignature = await crypto.subtle.sign("HMAC", cryptoKey, encoder.encode(userId));
        const pwHashHex = Array.from(new Uint8Array(pwSignature)).map(b => b.toString(16).padStart(2, '0')).join('');
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
        const { results } = await env.DB.prepare(
          'SELECT userId, date, status FROM "Reality Elite members management" ORDER BY date DESC'
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
        if (request.method === "GET") {
          const userId = url.searchParams.get("userId");
          const password = url.searchParams.get("password");

          if (!userId || !password) {
            return new Response("INVALID", { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          }

          const isValid = await verifyAuth(userId, password, env);
          if (!isValid) {
            return new Response("UNAUTHORIZED", { status: 401, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          }

          const row = await env.DB.prepare(
            'SELECT userId, status, expiryDate FROM "Reality Elite members management" WHERE userId = ?'
          ).bind(userId).first();

          if (!row) {
            return new Response("NOT_FOUND", { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          }

          if (row.status === "V") {
            return new Response(JSON.stringify({ status: "SUCCESS", expiryDate: row.expiryDate }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          } else if (row.status === "P") {
            return new Response(JSON.stringify({ status: "PENDING" }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          } else {
            return new Response("INVALID", { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          }
        }

        if (request.method === "POST") {
          let incomingData = {};
          try {
            incomingData = await request.json();
          } catch (e) {
            return new Response(JSON.stringify({ status: "ERROR", message: "Invalid JSON" }), { status: 400, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          }

          const userId = incomingData.userId;
          const password = incomingData.password;
          
          if (!userId || !password) {
            return new Response(JSON.stringify({ status: "ERROR", message: "Missing fields" }), { status: 400, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          }

          const isValid = await verifyAuth(userId, password, env);
          if (!isValid) {
            return new Response(JSON.stringify({ status: "ERROR", message: "Unauthorized" }), { status: 401, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          }

          const status = incomingData.status;

          if (status === "P") {
            const dateJoined = new Date().toISOString();
            await env.DB.prepare(`
              INSERT INTO "Reality Elite members management" (userId, date, status)
              VALUES (?, ?, ?)
              ON CONFLICT(userId) DO NOTHING
            `).bind(userId, dateJoined, "P").run();
            return new Response(JSON.stringify({ status: "SUCCESS" }), { status: 200, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          }
          else if (status === "V") {
            const transactionId = incomingData.transactionId;
            const durationDays = incomingData.durationDays || 365;

            if (!transactionId) {
                return new Response(JSON.stringify({ status: "ERROR", message: "Missing transactionId" }), { status: 400, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
            }

            const expDate = new Date();
            expDate.setDate(expDate.getDate() + durationDays);
            const expiryDateString = expDate.toISOString().split('T')[0] + `-${durationDays}`;

            await env.DB.prepare(`
              UPDATE "Reality Elite members management"
              SET status = 'V', transactionId = ?, expiryDate = ?
              WHERE userId = ?
            `).bind(transactionId, expiryDateString, userId).run();

            return new Response(JSON.stringify({ status: "SUCCESS", expiryDate: expiryDateString }), { status: 200, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
          }
        }
      }

// ============================================================
      // ROUTE: AI PROXY WITH RATE LIMIT & SUBSCRIPTION CHECK
      // ============================================================
      if (url.pathname === "/api/ai" && request.method === "POST") {
        let incomingData = {};
        try {
          incomingData = await request.json();
        } catch (e) {
          return new Response(JSON.stringify({ error: "Invalid JSON payload" }), { status: 400, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
        }

        const userId = incomingData.userId;
        const password = incomingData.password;

        if (!userId || !password) {
          return new Response(JSON.stringify({ error: "Missing auth fields" }), { status: 400, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
        }

        const isValid = await verifyAuth(userId, password, env);
        if (!isValid) {
          return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
        }

        // Sub check
        const userRow = await env.DB.prepare(
          'SELECT status, expiryDate, ai_usage FROM "Reality Elite members management" WHERE userId = ?'
        ).bind(userId).first();

        if (!userRow || userRow.status !== "V") {
          return new Response(JSON.stringify({ error: "Active Reality Elite subscription required" }), { status: 403, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
        }

        if (userRow.expiryDate) {
            const expiryDateStr = userRow.expiryDate.substring(0, 10);
            if (new Date(expiryDateStr) < new Date()) {
                return new Response(JSON.stringify({ error: "Subscription expired" }), { status: 403, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
            }
        }

        // Rate limit 50/day
        const todayStr = new Date().toISOString().split('T')[0];
        let currentUsageStr = userRow.ai_usage || "";
        let newCount = 1;

        if (currentUsageStr.startsWith(todayStr)) {
            const parts = currentUsageStr.split('-');
            const currentCount = parseInt(parts[parts.length - 1], 10);
            if (currentCount >= 50) {
                return new Response(JSON.stringify({ error: "Daily limit reached (50/50)" }), { status: 429, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
            }
            newCount = currentCount + 1;
        }

        const newUsageStr = `${todayStr}-${newCount}`;
        await env.DB.prepare(
            'UPDATE "Reality Elite members management" SET ai_usage = ? WHERE userId = ?'
        ).bind(newUsageStr, userId).run();

        // Check if there are tool format requests and we need to pass tools to the LLM backend
        const aiPayload = {
            model: "gpt oss 20 b",
            messages: incomingData.messages,
        };
        if (incomingData.tools) {
            aiPayload.tools = incomingData.tools;
        }

        // Proxy to actual AI URL
        if (!env.AI_URL) {
            return new Response(JSON.stringify({ error: "Backend AI URL missing" }), { status: 500, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
        }

        try {
            const aiResponse = await fetch(env.AI_URL, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(aiPayload)
            });

            if (!aiResponse.ok) {
                return new Response(JSON.stringify({ error: "AI backend error" }), { status: aiResponse.status, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
            }

            const aiData = await aiResponse.json();

            // Structure response logic properly
            if (aiData.tool_calls) {
                return new Response(JSON.stringify({ tool_calls: aiData.tool_calls }), { status: 200, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
            }

            if (aiData.response) {
                return new Response(JSON.stringify({ response: aiData.response }), { status: 200, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
            }

            if (aiData.choices && aiData.choices[0]) {
                const choice = aiData.choices[0];
                if (choice.message.tool_calls) {
                    return new Response(JSON.stringify({ tool_calls: choice.message.tool_calls }), { status: 200, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
                }
                return new Response(JSON.stringify({ response: choice.message.content }), { status: 200, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
            }

            return new Response(JSON.stringify({ response: "AI responded but format is unknown." }), { status: 200, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });

        } catch (e) {
            return new Response(JSON.stringify({ error: "Error contacting AI server" }), { status: 500, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
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
