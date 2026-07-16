export default {
  async fetch(request, env) {
    // Handle CORS preflight
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "https://reality.neubofy.in",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization"
        }
      });
    }

    const corsHeaders = {
      "Access-Control-Allow-Origin": "https://reality.neubofy.in"
    };

    // Handle GET request to retrieve allowed models dynamically
    if (request.method === "GET") {
      const models = [
        "@cf/openai/gpt-oss-120b",
        "@cf/openai/gpt-oss-20b"
      ];
      return new Response(JSON.stringify({ models: models }), {
        status: 200,
        headers: { 
          "Content-Type": "application/json",
          ...corsHeaders
        },
      });
    }

    // Only accept POST requests
    if (request.method !== "POST") {
      return new Response(JSON.stringify({ error: "Send a POST request" }), {
        status: 405,
        headers: { "Content-Type": "application/json", ...corsHeaders },
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
            headers: { "Content-Type": "application/json", ...corsHeaders },
        });
      }

      const isAuthorized = await this.verifyAuth(userId, password, env.APP_SECRET_PEPPER);
      if (!isAuthorized) {
        return new Response(JSON.stringify({ error: "Unauthorized. Invalid credentials." }), {
            status: 401,
            headers: { "Content-Type": "application/json", ...corsHeaders },
        });
      }

      // Allowed models list (strictly gpt-oss-120b and gpt-oss-20b)
      const allowedModels = [
        "@cf/openai/gpt-oss-120b",
        "@cf/openai/gpt-oss-20b"
      ];

      const requestedModel = body.model;
      const modelToUse = allowedModels.includes(requestedModel) ? requestedModel : "@cf/openai/gpt-oss-120b";
      const requestCost = modelToUse === "@cf/openai/gpt-oss-120b" ? 2 : 1;

      // ============================================================
      // SERVER-SIDE RATE LIMITING
      // Uses Cloudflare KV to track daily request counts per userId.
      // Requires KV namespace binding "RATE_LIMIT" in wrangler.toml.
      // Falls back to allowing requests if KV is unavailable.
      // ============================================================
      const DAILY_LIMIT = 25;
      if (env.RATE_LIMIT) {
        try {
          const today = new Date().toISOString().split("T")[0]; // YYYY-MM-DD
          const rateLimitKey = `ai_rate:${userId}:${today}`;
          const currentCountStr = await env.RATE_LIMIT.get(rateLimitKey);
          const currentCount = currentCountStr ? parseInt(currentCountStr, 10) : 0;

          if (body.action === "get_usage") {
            return new Response(JSON.stringify({ usage: currentCount, limit: DAILY_LIMIT }), {
              status: 200,
              headers: { "Content-Type": "application/json", ...corsHeaders },
            });
          }

          if (currentCount + requestCost > DAILY_LIMIT) {
            return new Response(JSON.stringify({ error: `Daily limit of ${DAILY_LIMIT} AI requests reached.` }), {
              status: 429,
              headers: { "Content-Type": "application/json", ...corsHeaders },
            });
          }

          // Increment count with 24h TTL (auto-cleanup)
          await env.RATE_LIMIT.put(rateLimitKey, String(currentCount + requestCost), { expirationTtl: 86400 });
        } catch (e) {
          // KV error — allow request but log
          console.error("Rate limit KV error:", e.message);
        }
      } else if (body.action === "get_usage") {
          return new Response(JSON.stringify({ usage: 0, limit: DAILY_LIMIT }), {
            status: 200,
            headers: { "Content-Type": "application/json", ...corsHeaders },
          });
      }


      let messages = body.messages || [{ role: "user", content: "Hello!" }];

      // Handle reasoning models that might fail with system prompts
      // Both GPT-OSS models are reasoning-capable, so we merge system prompts into user content if needed
      let systemPrompt = "";
      messages = messages.filter(msg => {
          if (msg.role === "system") {
              systemPrompt += msg.content + "\n\n";
              return false;
          }
          return true;
      });

      if (systemPrompt.length > 0 && messages.length > 0) {
          messages[0].content = systemPrompt + messages[0].content;
      } else if (systemPrompt.length > 0) {
          messages.push({ role: "user", content: systemPrompt.trim() });
      }

      // Call Cloudflare AI with messages, tools, and optional parameters
      const options = {
        messages: messages,
        max_tokens: body.max_tokens || 1024,
        temperature: body.temperature || 0.7,
      };

      if (body.tools && body.tools.length > 0) {
        options.tools = body.tools;
      }

      // Call Cloudflare AI with messages, tools (if any), and optional parameters
      const response = await env.AI.run(modelToUse, options);

      return new Response(JSON.stringify(response), {
        headers: { "Content-Type": "application/json", ...corsHeaders },
      });
    } catch (error) {
      return new Response(JSON.stringify({ error: "AI inference failed", details: error.message }), {
        status: 500,
        headers: { "Content-Type": "application/json", ...corsHeaders },
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
