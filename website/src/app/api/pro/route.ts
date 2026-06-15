import { NextResponse } from 'next/server';

export async function POST(request: Request) {
  try {
    const { userId } = await request.json();
    const apiUrl = process.env.NEXT_PUBLIC_LICENSE_API_URL;

    if (!apiUrl) {
      return NextResponse.json({ error: 'API URL not configured' }, { status: 500 });
    }

    // Server-side fetch proxy to handle cross-origin constraints
    const response = await fetch(apiUrl, {
      method: 'POST',
      body: JSON.stringify({ userId }),
      headers: {
        'Content-Type': 'application/json'
      }
    });

    const text = await response.text();
    let data;
    try {
      data = JSON.parse(text);
    } catch {
      console.error("Failed to parse JSON from license server:", text);
      return NextResponse.json({ error: 'Invalid response from license server' }, { status: 500 });
    }

    return NextResponse.json(data);
  } catch (error: unknown) {
    console.error("API proxy error:", error);
    return NextResponse.json({ error: (error as Error).message }, { status: 500 });
  }
}
