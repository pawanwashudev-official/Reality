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


      // Reasoning capable models (these might not support 'system' role or tool calling)
      const reasoningModels = [
        "@cf/deepseek-ai/deepseek-r1-distill-qwen-32b",
        "@cf/moonshotai/kimi-k2.7-code",
        "@cf/zai-org/glm-4.7-flash",
        "@cf/openai/gpt-oss-120b",
        "@cf/zai-org/glm-5.2",
        "@cf/moonshotai/kimi-k2.6",
        "@cf/google/gemma-4-26b-a4b-it",
        "@cf/nvidia/nemotron-3-120b-a12b",
        "@cf/openai/gpt-oss-20b",
        "@cf/qwen/qwen3-30b-a3b-fp8",
        "@cf/qwen/qwq-32b"
      ];

      // Tool calling compatible models
      const toolCallingModels = [
        "@cf/openai/gpt-oss-120b",
        "@cf/openai/gpt-oss-20b",
        "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
        "@cf/qwen/qwen2.5-coder-32b-instruct",
        "@cf/meta/llama-4-scout-17b-16e-instruct",
        "@cf/mistralai/mistral-small-3.1-24b-instruct",
        "@cf/moonshotai/kimi-k2.7-code",
        "@cf/google/gemma-4-26b-a4b-it",
        "@cf/ibm-granite/granite-4.0-h-micro",
        "@cf/zai-org/glm-4.7-flash",
        "@cf/zai-org/glm-5.2",
        "@cf/moonshotai/kimi-k2.6",
        "@cf/nvidia/nemotron-3-120b-a12b",
        "@cf/qwen/qwen3-30b-a3b-fp8"
      ];

      const allowedModels = [...new Set([...reasoningModels, ...toolCallingModels])];

      const requestedModel = body.model;
      const modelToUse = allowedModels.includes(requestedModel) ? requestedModel : "@cf/openai/gpt-oss-120b";

      let messages = body.messages || [{ role: "user", content: "Hello!" }];

      // Handle reasoning models that might fail with system prompts
      if (reasoningModels.includes(modelToUse)) {
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
