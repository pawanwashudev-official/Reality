export default {
  async fetch(request, env) {
    // 1. Handle CORS for standard web traffic safety
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type", 
        },
      });
    }

    // 2. SPAM PROTECTION: Strict IP Rate Limiting (10 requests per day)
    // Stops anyone from brute-forcing or spamming your endpoint
    const clientIP = request.headers.get("cf-connecting-ip") || "unknown";
    const todayKey = `limit:${clientIP}:${new Date().toISOString().split('T')[0]}`;
    
    if (env.LIMIT_STORE) {
      const currentCount = parseInt(await env.LIMIT_STORE.get(todayKey) || "0", 10);
      if (currentCount >= 10) {
        return new Response(JSON.stringify({ error: "Daily request limit exceeded." }), { 
          status: 429, headers: { "Content-Type": "application/json" } 
        });
      }
      await env.LIMIT_STORE.put(todayKey, (currentCount + 1).toString(), { expirationTtl: 90000 });
    }

    const url = new URL(request.url);

    try {
      // ---------------------------------------------------------
      // ROUTE 1: GOOGLE OAUTH LOGIN (The App Auth Manager)
      // ---------------------------------------------------------
      if (url.pathname === "/oauth/token" && request.method === "POST") {
        const incomingData = await request.json(); 
        
        // Cloudflare talks to Google securely, hiding your Secret
        const googleResponse = await fetch("https://oauth2.googleapis.com/token", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            code: incomingData.code,
            redirect_uri: incomingData.redirect_uri,
            client_id: env.GCP_CLIENT_ID,
            client_secret: env.GCP_CLIENT_SECRET,
            grant_type: "authorization_code"
          })
        });

        const tokens = await googleResponse.text();
        return new Response(tokens, {
          status: googleResponse.status,
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
        });
      }

      // ---------------------------------------------------------
      // ROUTE 2: SUBSCRIPTION VERIFICATION (The Google Apps Script)
      // ---------------------------------------------------------
      const targetUrl = env.NEW_REALITY_LICENSE_URL;
      if (!targetUrl) return new Response("Backend URL missing.", { status: 500 });
      
      // Handles the verifyCode (GET) request
      if (request.method === "GET") {
        const backendResponse = await fetch(`${targetUrl}${url.search}`, { method: "GET" });
        const responseData = await backendResponse.text();
        return new Response(responseData, { status: backendResponse.status, headers: { "Access-Control-Allow-Origin": "*" } });
      }

      // Handles the payment submission (POST) request
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

      return new Response("Not Found", { status: 404 });

    } catch (error) {
      return new Response(JSON.stringify({ error: "Proxy failure", details: error.message }), { status: 500 });
    }
  }
};
