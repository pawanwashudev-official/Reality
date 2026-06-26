import { NextRequest, NextResponse } from 'next/server';
import { setStateCookie } from '@/lib/tokenCookie';

export async function GET(req: NextRequest) {
  const clientId = process.env.CLIENT_ID;

  let baseUrl = process.env.NEXT_PUBLIC_APP_URL || req.nextUrl.origin;

  if (baseUrl.includes('reality.neubofy.in') && baseUrl.startsWith('http://')) {
      baseUrl = baseUrl.replace('http://', 'https://');
  }

  const redirectUri = `${baseUrl}/api/auth/callback/google`;

  if (!clientId) {
    return NextResponse.json({ error: 'Missing CLIENT_ID env var' }, { status: 500 });
  }

  const state = crypto.randomUUID();
  await setStateCookie(state);

  const scope = encodeURIComponent('https://www.googleapis.com/auth/calendar.readonly');
  const authUrl = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&scope=${scope}&access_type=offline&state=${encodeURIComponent(state)}&prompt=consent`;

  return NextResponse.redirect(authUrl);
}
