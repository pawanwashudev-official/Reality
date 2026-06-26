import { NextRequest, NextResponse } from 'next/server';
import { getStateCookie, clearStateCookie, setTokenCookie, getTokenCookie } from '@/lib/tokenCookie';

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const code = searchParams.get('code');
  const state = searchParams.get('state');

  if (!code || !state) {
    return NextResponse.json({ error: 'Missing code or state' }, { status: 400 });
  }

  const savedState = await getStateCookie();
  if (state !== savedState) {
      return NextResponse.json({ error: 'Invalid state' }, { status: 403 });
  }

  await clearStateCookie();

  const clientId = process.env.CLIENT_ID;
  const clientSecret = process.env.CLIENT_SECRET;

  let baseUrl = process.env.NEXT_PUBLIC_APP_URL || req.nextUrl.origin;

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
    return NextResponse.json({ error: 'Failed to exchange code' }, { status: 500 });
  }

  let finalRefreshToken = tokenData.refresh_token;
  if (!finalRefreshToken) {
      // If user has reconnect without prompt=consent we might not get refresh token, check if we have one
      const oldToken = await getTokenCookie();
      if (oldToken && oldToken.refresh_token) {
          finalRefreshToken = oldToken.refresh_token;
      }
  }

  // Save the token securely
  await setTokenCookie({
      access_token: tokenData.access_token,
      refresh_token: finalRefreshToken,
      expires_in: tokenData.expires_in,
      expires_at: Date.now() + (tokenData.expires_in * 1000)
  });

  const redirectUrl = new URL('/tapashya?connected=1', req.url);

  return NextResponse.redirect(redirectUrl);
}
