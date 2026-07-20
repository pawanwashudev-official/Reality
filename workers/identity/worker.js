const CORS_ORIGIN = "https://reality.neubofy.in";

export default {
  async fetch(request, env) {
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

        const identityData = await this.fetchUserIdentity(userId, env);

        return new Response(
          JSON.stringify(identityData),
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
      // ROUTE: NATIVE SUBSCRIPTION MANAGEMENT
      // ============================================================
      if (url.pathname === "/license") {
        
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
          const password = incomingData.password || incomingData.connectionSecret;
          const activeExpiry = incomingData.activeExpiry || "0";
          const activeDuration = incomingData.activeDuration || "0";
          const activeStatus = incomingData.activeStatus || "N";
          const planType = incomingData.planType || "none";

          if (!userId || !password) {
             return new Response(JSON.stringify({ status: "ERROR", message: "Missing auth fields" }), {
              status: 400,
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
            });
          }

          const isAuthorized = await this.verifyAuth(userId, password, activeExpiry, activeDuration, activeStatus, planType, env.APP_SECRET_PEPPER);
          if (!isAuthorized) {
            return new Response(JSON.stringify({ status: "ERROR", message: "Unauthorized" }), {
              status: 401,
              headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
            });
          }

          // Purchase submission
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
            'SELECT date, status, expiryDate, trial_plan FROM "Reality Elite members management" WHERE userId = ?'
          ).bind(userId).first();

          const durationMs = Math.floor(365 / 12) * months * 24 * 60 * 60 * 1000;
          let currentExpiry = Date.now();
          let totalMonths = months;

          let isCurrentlyActive = false;

          // Extend Plan logic: if active paid subscription exists in the DB, append new duration to it
          if (existingRow && existingRow.status === "V" && existingRow.expiryDate) {
            const parts = existingRow.expiryDate.split("-");
            if (parts.length === 2) {
              const expiryUnix = parseInt(parts[0], 10);
              const existingMonths = parseInt(parts[1], 10) || 0;
              if (expiryUnix > Date.now()) {
                currentExpiry = expiryUnix;
                totalMonths = existingMonths + months;
                isCurrentlyActive = true;
              }
            }
          }

          // If no active paid plan, check if there is an active trial plan to carry over remaining trial days
          if (!isCurrentlyActive && existingRow && existingRow.trial_plan) {
            const parts = existingRow.trial_plan.split("-");
            if (parts.length === 2) {
              const trialExpiryUnix = parseInt(parts[0], 10);
              if (trialExpiryUnix > Date.now()) {
                const trialRemainingMs = trialExpiryUnix - Date.now();
                currentExpiry = Date.now() + trialRemainingMs;
              }
            }
          }

          const newExpiryUnix = currentExpiry + durationMs;
          const expiryDateStr = `${newExpiryUnix}-${totalMonths}`;

          await env.DB.prepare(`
            INSERT INTO "Reality Elite members management" (userId, date, status, transactionId, customNote, expiryDate)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(userId) DO UPDATE SET 
              transactionId = excluded.transactionId,
              customNote = excluded.customNote,
              expiryDate = excluded.expiryDate,
              status = excluded.status
          `).bind(userId, new Date().toISOString(), "V", transactionId, customNote, expiryDateStr).run();

          // Retrieve the updated, fully-aligned subscription details using the helper
          const identityData = await this.fetchUserIdentity(userId, env);

          return new Response(
            JSON.stringify({
              ...identityData,
              status: "SUCCESS",
              verificationCode: "REGISTERED"
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

  async generateConnectionSecret(userId, expiry, duration, status, planType, secretPepper) {
    if (!userId || !secretPepper) return "";
    const expiryStr = String(expiry || "0");
    const durationStr = String(duration || "0");
    const statusStr = String(status || "N");
    const planTypeStr = String(planType || "none");
    
    const encoder = new TextEncoder();
    const secretKeyData = encoder.encode(secretPepper);
    const cryptoKey = await crypto.subtle.importKey(
      "raw", secretKeyData, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
    );
    const msg = `${userId}:${expiryStr}:${durationStr}:${statusStr}:${planTypeStr}`;
    const signature = await crypto.subtle.sign("HMAC", cryptoKey, encoder.encode(msg));
    return Array.from(new Uint8Array(signature))
      .map(b => b.toString(16).padStart(2, '0'))
      .join('').substring(0, 32);
  },

  async verifyAuth(userId, connectionSecret, expiry, duration, status, planType, secretPepper) {
    if (!userId || !connectionSecret || !secretPepper) return false;
    const expectedSecret = await this.generateConnectionSecret(userId, expiry, duration, status, planType, secretPepper);
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
  },

  async generateBackupPassword(userId, secretPepper) {
    if (!userId || !secretPepper) return "";
    const encoder = new TextEncoder();
    const secretKeyData = encoder.encode(secretPepper);
    const cryptoKey = await crypto.subtle.importKey(
      "raw", secretKeyData, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
    );
    // Use strictly the userId so it matches the legacy password from before the subscription update
    const msg = `${userId}`;
    const signature = await crypto.subtle.sign("HMAC", cryptoKey, encoder.encode(msg));
    return Array.from(new Uint8Array(signature))
      .map(b => b.toString(16).padStart(2, '0'))
      .join('').substring(0, 32);
  },

  async fetchUserIdentity(userId, env) {
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
        console.error("DB Error in fetchUserIdentity:", e);
      }
    }

    // Determine active subscription info state for deterministic password generation
    let activeExpiry = "0";
    let activeDuration = "0";
    let activeStatus = "N";
    let planType = "none";

    let isPaidActive = false;
    if (userStatus === "V" && userExpiryDate) {
      const parts = userExpiryDate.split("-");
      if (parts.length === 2) {
        const expiryUnix = parseInt(parts[0], 10);
        if (expiryUnix > Date.now()) {
          activeExpiry = String(expiryUnix);
          activeDuration = parts[1];
          activeStatus = "V";
          planType = "paid";
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
          planType = "trial";
        }
      }
    }

    // Generate connectionSecret based on active subscription details
    const connectionSecret = await this.generateConnectionSecret(
      userId,
      activeExpiry,
      activeDuration,
      activeStatus,
      planType,
      env.APP_SECRET_PEPPER
    );

    const backupPassword = await this.generateBackupPassword(userId, env.APP_SECRET_PEPPER);

    return {
      userId: userId,
      connectionSecret: connectionSecret,
      password: connectionSecret, // mapping compatibility for PaymentVerificationActivity
      backupPassword: backupPassword,
      backupPassword_legacy: connectionSecret, // legacy mapping
      backupKey: backupPassword, // legacy mapping
      date: userDate,
      status: userStatus,
      expiryDate: userExpiryDate,
      trial_plan: userTrialPlan,
      activeExpiry: activeExpiry,
      activeDuration: activeDuration,
      activeStatus: activeStatus,
      planType: planType
    };
  }
};
