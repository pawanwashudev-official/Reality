// ============================================================
// Reality Elite Member Management — Cloudflare Worker
// Routes:
//   GET /api/pro-members
// ============================================================

const CORS_ORIGIN = "https://reality.neubofy.in";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": CORS_ORIGIN,
          "Access-Control-Allow-Methods": "GET, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, x-worker-secret",
        },
      });
    }

    if (url.pathname === "/") {
      return new Response("Reality Elite Member Worker is running.", { status: 200 });
    }

    if (url.pathname === "/api/pro-members" && request.method === "GET") {
      const secretHeader = request.headers.get("x-worker-secret");
      if (!secretHeader || secretHeader !== env.WORKER_CONNECTION_SECRET) {
        return new Response(JSON.stringify({ error: "Unauthorized access" }), {
          status: 401,
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
        });
      }

      const limit = parseInt(url.searchParams.get("limit") || "10", 10);
      const offset = parseInt(url.searchParams.get("offset") || "0", 10);
      const userId = url.searchParams.get("userId");

      try {
        // 1. Get total members count quickly
        const countResult = await env.DB.prepare(
          'SELECT COUNT(*) as count FROM "Reality Elite members management"'
        ).first();
        const totalMembers = countResult ? countResult.count : 0;

        // 2. Fetch data based on parameters
        let results = [];
        if (userId) {
          // If searching for a specific user, just return that user
          const res = await env.DB.prepare(
            'SELECT userId, date, status, expiryDate, trial_plan FROM "Reality Elite members management" WHERE userId = ?'
          ).bind(userId).all();
          results = res.results;
        } else {
          // Paginated list
          const res = await env.DB.prepare(
            'SELECT userId, date, status, expiryDate, trial_plan FROM "Reality Elite members management" ORDER BY date DESC LIMIT ? OFFSET ?'
          ).bind(limit, offset).all();
          results = res.results;
        }

        return new Response(
          JSON.stringify({ totalMembers, members: results }),
          {
            status: 200,
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
          }
        );
      } catch (err) {
        return new Response(JSON.stringify({ error: err.message }), {
          status: 500,
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": CORS_ORIGIN }
        });
      }
    }

    return new Response("Not found", { status: 404 });
  }
};
