export default {
  async fetch(request, env) {
    const CORS_ORIGIN = "https://reality.neubofy.in";

    // Handle CORS preflight requests universally
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": CORS_ORIGIN,
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, x-worker-secret",
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
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
          });
        }

        const idToken = incomingData.idToken;
        if (!idToken || typeof idToken !== "string") {
          return new Response(JSON.stringify({ error: "idToken string is required" }), {
            status: 400,
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
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
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
          });
        }

        if (!env.APP_SECRET_PEPPER) {
          return new Response(JSON.stringify({ error: "Server misconfiguration: missing secret pepper" }), {
            status: 500,
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
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

        let userDate = null;
        let userStatus = null;
        let userExpiryDate = null;
        let userTrialPlan = null;

        if (env.DB) {
          try {
            const existingRow = await env.DB.prepare(
              'SELECT date, status, expiryDate, trial_plan FROM "Reality Elite members management" WHERE userId = ?'
            ).bind(userId).first();

            const durationDays = 3;
            const currentUnix = Date.now();
            const durationMs = durationDays * 24 * 60 * 60 * 1000;
            const expiryUnix = currentUnix + durationMs;
            const autoTrialPlanStr = `${expiryUnix}-${durationDays}`;

            if (!existingRow) {
              const currentDate = new Date().toISOString();
              await env.DB.prepare(`
                INSERT INTO "Reality Elite members management" (userId, date, trial_plan)
                VALUES (?, ?, ?)
              `).bind(userId, currentDate, autoTrialPlanStr).run();
              userDate = currentDate;
              userTrialPlan = autoTrialPlanStr;
            } else {
              userDate = existingRow.date;
              userStatus = existingRow.status;
              userExpiryDate = existingRow.expiryDate;
              userTrialPlan = existingRow.trial_plan;

              if (!userTrialPlan) {
                await env.DB.prepare(`
                  UPDATE "Reality Elite members management"
                  SET trial_plan = ?
                  WHERE userId = ?
                `).bind(autoTrialPlanStr, userId).run();
                userTrialPlan = autoTrialPlanStr;
              }

              if (userExpiryDate) {
                const parts = userExpiryDate.split("-");
                let isExpired = false;
                if (parts.length === 2) {
                  const expiryUnix = parseInt(parts[0], 10);
                  if (expiryUnix < Date.now()) isExpired = true;
                } else if (parts.length === 4) {
                  const yyyy = parseInt(parts[0], 10);
                  const mm = parseInt(parts[1], 10) - 1;
                  const dd = parseInt(parts[2], 10);
                  const expiryDateObj = new Date(Date.UTC(yyyy, mm, dd));
                  if (expiryDateObj.getTime() < Date.now()) isExpired = true;
                }

                if (isExpired) {
                  await env.DB.prepare(`
                    UPDATE "Reality Elite members management"
                    SET status = NULL, expiryDate = NULL
                    WHERE userId = ?
                  `).bind(userId).run();
                  userStatus = null;
                  userExpiryDate = null;
                }
              }
            }
          } catch (e) {
            console.error("DB Error in generate-identity:", e);
          }
        }

        // Determine active subscription info state for deterministic password generation
        let activeExpiry = "0";
        let activeDuration = "0";
        let activeStatus = "N";

        let isPaidActive = false;
        if (userStatus === "V" && userExpiryDate) {
          const parts = userExpiryDate.split("-");
          if (parts.length === 2) {
            const expiryUnix = parseInt(parts[0], 10);
            if (expiryUnix > Date.now()) {
              activeExpiry = String(expiryUnix);
              activeDuration = parts[1];
              activeStatus = "V";
              isPaidActive = true;
            }
          }
        }

        if (!isPaidActive && userTrialPlan) {
          const parts = userTrialPlan.split("-");
          if (parts.length === 2) {
            const expiryUnix = parseInt(parts[0], 10);
            if (expiryUnix > Date.now()) {
              activeExpiry = String(expiryUnix);
              activeDuration = parts[1];
              activeStatus = "V";
            }
          }
        }

        // Generate backupPassword based on active subscription details
        const backupPassword = await this.generatePassword(
          userId,
          activeExpiry,
          activeDuration,
          activeStatus,
          env.APP_SECRET_PEPPER
        );

        return new Response(
          JSON.stringify({
            userId: userId,
            backupPassword: backupPassword,
            date: userDate,
            status: userStatus,
            expiryDate: userExpiryDate,
            trial_plan: userTrialPlan,
            activeExpiry: activeExpiry,
            activeDuration: activeDuration,
            activeStatus: activeStatus
          }),
          { 
            status: 200, 
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN } 
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
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
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
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
          });
        }

        // Fetch only safe, shareable details from D1
        const { results } = await env.DB.prepare(
          'SELECT userId, date, status, expiryDate, trial_plan FROM "Reality Elite members management" ORDER BY date DESC'
        ).all();

        const totalMembers = results.length;

        return new Response(
          JSON.stringify({ totalMembers, members: results }),
          {
            status: 200,
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
          }
        );
      }

      // ============================================================
      // ROUTE: NATIVE SUBSCRIPTION MANAGEMENT
      // ============================================================
      if (url.pathname === "/license") {
        
        // Handles verification (GET) - Step 3 (Stateless, Database-free verification)
        if (request.method === "GET") {
          const userId = url.searchParams.get("userId");
          const password = url.searchParams.get("password");
          const activeExpiry = url.searchParams.get("activeExpiry") || "0";
          const activeDuration = url.searchParams.get("activeDuration") || "0";
          const activeStatus = url.searchParams.get("activeStatus") || "N";

          if (!userId || !password) {
            return new Response(JSON.stringify({ status: "INVALID" }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN } });
          }

          const isAuthorized = await this.verifyAuth(userId, password, activeExpiry, activeDuration, activeStatus, env.APP_SECRET_PEPPER);
          if (!isAuthorized) {
            return new Response(JSON.stringify({ status: "UNAUTHORIZED" }), { status: 401, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN } });
          }

          if (activeStatus === "V" && parseInt(activeExpiry, 10) > Date.now()) {
            return new Response(JSON.stringify({ status: "SUCCESS", expiryDate: `${activeExpiry}-${activeDuration}` }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN } });
          } else {
            return new Response(JSON.stringify({ status: "EXPIRED" }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN } });
          }
        }

        // Handles subscription verification & submission (POST)
        if (request.method === "POST") {
          let incomingData = {};
          try {
            incomingData = await request.json();
          } catch (e) {
            return new Response(JSON.stringify({ status: "ERROR", message: "Invalid JSON payload" }), {
              status: 400,
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
            });
          }

          const userId = incomingData.userId;
          const password = incomingData.password;
          const activeExpiry = incomingData.activeExpiry || "0";
          const activeDuration = incomingData.activeDuration || "0";
          const activeStatus = incomingData.activeStatus || "N";

          if (!userId || !password) {
             return new Response(JSON.stringify({ status: "ERROR", message: "Missing auth fields" }), {
              status: 400,
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
            });
          }

          const isAuthorized = await this.verifyAuth(userId, password, activeExpiry, activeDuration, activeStatus, env.APP_SECRET_PEPPER);
          if (!isAuthorized) {
            return new Response(JSON.stringify({ status: "ERROR", message: "Unauthorized" }), {
              status: 401,
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
            });
          }

          // Case 1: action === "verify" (checks latest DB status, updates client's signature)
          if (incomingData.action === "verify") {
            const existingRow = await env.DB.prepare(
              'SELECT status, expiryDate, trial_plan FROM "Reality Elite members management" WHERE userId = ?'
            ).bind(userId).first();

            if (!existingRow) {
              return new Response(JSON.stringify({ status: "NOT_FOUND" }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN } });
            }

            let latestStatus = existingRow.status;
            let latestExpiryDate = existingRow.expiryDate;
            let latestTrialPlan = existingRow.trial_plan;

            // Handle expiry checking and reset in DB
            if (latestExpiryDate) {
              const parts = latestExpiryDate.split("-");
              let isExpired = false;
              if (parts.length === 2) {
                const expiryUnix = parseInt(parts[0], 10);
                if (expiryUnix < Date.now()) isExpired = true;
              } else if (parts.length === 4) {
                const yyyy = parseInt(parts[0], 10);
                const mm = parseInt(parts[1], 10) - 1;
                const dd = parseInt(parts[2], 10);
                const expiryDateObj = new Date(Date.UTC(yyyy, mm, dd));
                if (expiryDateObj.getTime() < Date.now()) isExpired = true;
              }

              if (isExpired) {
                await env.DB.prepare(`
                  UPDATE "Reality Elite members management"
                  SET status = NULL, expiryDate = NULL
                  WHERE userId = ?
                `).bind(userId).run();
                latestStatus = null;
                latestExpiryDate = null;
              }
            }

            // Determine current active subscription details
            let curActiveExpiry = "0";
            let curActiveDuration = "0";
            let curActiveStatus = "N";

            let isPaidActive = false;
            if (latestStatus === "V" && latestExpiryDate) {
              const parts = latestExpiryDate.split("-");
              if (parts.length === 2) {
                const expiryUnix = parseInt(parts[0], 10);
                if (expiryUnix > Date.now()) {
                  curActiveExpiry = String(expiryUnix);
                  curActiveDuration = parts[1];
                  curActiveStatus = "V";
                  isPaidActive = true;
                }
              }
            }

            if (!isPaidActive && latestTrialPlan) {
              const parts = latestTrialPlan.split("-");
              if (parts.length === 2) {
                const expiryUnix = parseInt(parts[0], 10);
                if (expiryUnix > Date.now()) {
                  curActiveExpiry = String(expiryUnix);
                  curActiveDuration = parts[1];
                  curActiveStatus = "V";
                }
              }
            }

            const newPassword = await this.generatePassword(userId, curActiveExpiry, curActiveDuration, curActiveStatus, env.APP_SECRET_PEPPER);

            if (curActiveStatus === "V") {
              return new Response(
                JSON.stringify({
                  status: "SUCCESS",
                  verificationCode: "REGISTERED",
                  password: newPassword,
                  activeExpiry: curActiveExpiry,
                  activeDuration: curActiveDuration,
                  activeStatus: curActiveStatus,
                  expiryDate: latestExpiryDate || `${curActiveExpiry}-${curActiveDuration}`
                }),
                { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN } }
              );
            } else if (latestStatus === "P") {
              return new Response(JSON.stringify({ status: "PENDING" }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN } });
            } else {
              return new Response(JSON.stringify({ status: "EXPIRED" }), { headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN } });
            }
          }

          // Case 2: Purchase submission
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
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
            });
          }

          if (customNote.length > 200) customNote = customNote.substring(0, 200);

          const existingRow = await env.DB.prepare(
            'SELECT status, expiryDate FROM "Reality Elite members management" WHERE userId = ?'
          ).bind(userId).first();

          const durationMs = Math.floor(365 / 12) * months * 24 * 60 * 60 * 1000;
          let currentExpiry = Date.now();

          // Extend Plan logic: if active paid subscription exists in the DB, append new duration to it
          if (existingRow && existingRow.status === "V" && existingRow.expiryDate) {
            const parts = existingRow.expiryDate.split("-");
            if (parts.length === 2) {
              const expiryUnix = parseInt(parts[0], 10);
              if (expiryUnix > Date.now()) {
                currentExpiry = expiryUnix;
              }
            }
          }

          const newExpiryUnix = currentExpiry + durationMs;
          const expiryDateStr = `${newExpiryUnix}-${months}`;

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

          // Compute the new signature password
          const newActiveExpiry = String(newExpiryUnix);
          const newActiveDuration = String(months);
          const newActiveStatus = "V";

          const newPassword = await this.generatePassword(
            userId,
            newActiveExpiry,
            newActiveDuration,
            newActiveStatus,
            env.APP_SECRET_PEPPER
          );

          return new Response(
            JSON.stringify({
              status: "SUCCESS",
              verificationCode: "REGISTERED",
              password: newPassword,
              activeExpiry: newActiveExpiry,
              activeDuration: newActiveDuration,
              activeStatus: newActiveStatus,
              expiryDate: expiryDateStr
            }),
            { 
              status: 200, 
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN } 
            }
          );
        }
      }
      return new Response("Not Found", { status: 404 });

    } catch (error) {
      return new Response(JSON.stringify({ error: "Proxy failure", details: error.message }), { 
        status: 500,
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
      });
    }

  },

  async generatePassword(userId, expiry, duration, status, secretPepper) {
    if (!userId || !secretPepper) return "";
    const expiryStr = String(expiry || "0");
    const durationStr = String(duration || "0");
    const statusStr = String(status || "N");
    
    const encoder = new TextEncoder();
    const secretKeyData = encoder.encode(secretPepper);
    const cryptoKey = await crypto.subtle.importKey(
      "raw", secretKeyData, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
    );
    const msg = `${userId}:${expiryStr}:${durationStr}:${statusStr}`;
    const signature = await crypto.subtle.sign("HMAC", cryptoKey, encoder.encode(msg));
    return Array.from(new Uint8Array(signature))
      .map(b => b.toString(16).padStart(2, '0'))
      .join('').substring(0, 32);
  },

  async verifyAuth(userId, providedPassword, expiry, duration, status, secretPepper) {
    if (!userId || !providedPassword || !secretPepper) return false;
    const expectedPassword = await this.generatePassword(userId, expiry, duration, status, secretPepper);
    const matched = providedPassword === expectedPassword;
    if (!matched) {
      console.warn(`[SECURITY] Unauthorized access detected: Hashed credentials mismatch! User ID: ${userId}, Expiry: ${expiry}, Duration: ${duration}, Status: ${status}. Attempts to bypass subscription verification logic may result in account termination and legal action.`);
    }
    return matched;
  }
};
