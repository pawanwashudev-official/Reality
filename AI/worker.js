export default {
  async fetch(request, env) {
    // Only accept POST requests
    if (request.method !== "POST") {
      return new Response(JSON.stringify({ error: "Send a POST request" }), {
        status: 405,
        headers: { "Content-Type": "application/json" },
      });
    }

    try {
      const body = await request.json();
      const userId = body.userId;
      const password = body.password;

      // Extract and verify userId and password
      if (!userId || !password) {
        return new Response(JSON.stringify({ error: "Missing authentication fields." }), {
            status: 401,
            headers: { "Content-Type": "application/json" },
        });
      }


      const isAuthorized = await this.verifyAuth(userId, password, env.APP_SECRET_PEPPER);
      if (!isAuthorized) {
        return new Response(JSON.stringify({ error: "Unauthorized. Invalid credentials." }), {
            status: 401,
            headers: { "Content-Type": "application/json" },
        });
      }


      const requestCount = body.requestCount || 0;
      if (requestCount >= 75) {
        return new Response(JSON.stringify({ error: "Daily limit of 75 AI requests reached." }), {
            status: 429,
            headers: { "Content-Type": "application/json" },
        });
      }


      const allowedModels = [
        "@cf/meta/llama-3.1-8b-instruct",
        "@cf/meta/llama-3.2-3b-instruct",
        "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
        "@cf/qwen/qwen1.5-14b-chat-awq",
        "@hf/thebloke/mistral-7b-instruct-v0.1-awq"
];

      const requestedModel = body.model;
      const modelToUse = allowedModels.includes(requestedModel) ? requestedModel : "@cf/meta/llama-3.1-8b-instruct";

      // Call Cloudflare AI with messages, tools, and optional parameters
      const response = await env.AI.run(modelToUse, {
        messages: body.messages || [{ role: "user", content: "Hello!" }],
        tools: body.tools || [],
        max_tokens: body.max_tokens || 1024,
        temperature: body.temperature || 0.7,
      });


      return new Response(JSON.stringify(response), {
        headers: { "Content-Type": "application/json" },
      });
    } catch (error) {
      return new Response(JSON.stringify({ error: "AI inference failed", details: error.message }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
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
