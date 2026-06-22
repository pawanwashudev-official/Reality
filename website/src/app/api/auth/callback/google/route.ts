import { NextRequest, NextResponse } from 'next/server';

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const code = searchParams.get('code');

  if (!code) {
    return NextResponse.json({ error: 'Missing code' }, { status: 400 });
  }

  const clientId = process.env.CLIENT_ID;
  const clientSecret = process.env.CLIENT_SECRET;

  let baseUrl = process.env.NEXT_PUBLIC_APP_URL || req.nextUrl.origin;

  // Vercel sometimes forwards HTTP internally. Force HTTPS in production if it's not localhost.
  if (baseUrl.includes('reality.neubofy.in') && baseUrl.startsWith('http://')) {
      baseUrl = baseUrl.replace('http://', 'https://');
  }

  const redirectUri = `${baseUrl}/api/auth/callback/google`;

  const tokenResponse = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      code,
      client_id: clientId || '',
      client_secret: clientSecret || '',
      redirect_uri: redirectUri,
      grant_type: 'authorization_code',
    }),
  });

  const tokenData = await tokenResponse.json();

  if (!tokenResponse.ok) {
    return NextResponse.json({ error: 'Failed to exchange code', details: tokenData }, { status: 500 });
  }

  const redirectUrl = new URL('/tapashya', req.url);
  redirectUrl.hash = `access_token=${tokenData.access_token}${tokenData.refresh_token ? `&refresh_token=${tokenData.refresh_token}` : ''}&expires_in=${tokenData.expires_in}`;

  return NextResponse.redirect(redirectUrl);
}
