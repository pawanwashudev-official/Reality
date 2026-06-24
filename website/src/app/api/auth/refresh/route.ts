import { NextRequest, NextResponse } from 'next/server';

export async function POST(req: NextRequest) {
  try {
    const { refresh_token } = await req.json();
    if (!refresh_token) return NextResponse.json({ error: 'Missing refresh token' }, { status: 400 });

    const clientId = process.env.CLIENT_ID;
    const clientSecret = process.env.CLIENT_SECRET;

    const response = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        client_id: clientId || '',
        client_secret: clientSecret || '',
        refresh_token: refresh_token,
        grant_type: 'refresh_token',
      }),
    });

    const data = await response.json();
    if (!response.ok) {
        return NextResponse.json({ error: 'Failed to refresh token', details: data }, { status: 500 });
    }

    return NextResponse.json(data);
  } catch (error) {
    return NextResponse.json({ error: 'Internal error' }, { status: 500 });
  }
}
