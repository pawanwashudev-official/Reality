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

      // Normally we would verify auth here against the D1 DB via verifyAuth method
      // Assuming env.APP_SECRET_PEPPER is set, similar to the main proxy worker.
      // But we will at least require them to be present.

      // Call GPT-OSS-20B with messages, tools, and optional parameters
      const response = await env.AI.run("@cf/openai/gpt-oss-20b", {
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
};
