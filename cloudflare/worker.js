export default {
  async fetch(request, env) {
    // 1. Handle CORS (allows the app to talk to the worker)
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
      // ROUTE: GOOGLE OAUTH TOKEN EXCHANGE
      if (url.pathname === "/oauth/token" && request.method === "POST") {
        const incomingData = await request.json(); 
        
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

      // ROUTE: SUBSCRIPTION VERIFICATION/SUBMISSION
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

      return new Response("Not Found", { status: 404 });

    } catch (error) {
      return new Response(JSON.stringify({ error: "Proxy failure", details: error.message }), { status: 500 });
    }
  }
};
