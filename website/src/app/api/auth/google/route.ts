import { NextRequest, NextResponse } from 'next/server';

export async function GET(req: NextRequest) {
  const clientId = process.env.CLIENT_ID;

  // Use Vercel production URL if available, otherwise fallback to the incoming request's origin
  // Note: VERCEL_URL is sometimes a preview branch URL. req.nextUrl.origin ensures it matches the browser.
  let baseUrl = process.env.NEXT_PUBLIC_APP_URL || req.nextUrl.origin;

  // Vercel sometimes forwards HTTP internally. Force HTTPS in production if it's not localhost.
  if (baseUrl.includes('reality.neubofy.in') && baseUrl.startsWith('http://')) {
      baseUrl = baseUrl.replace('http://', 'https://');
  }

  const redirectUri = `${baseUrl}/api/auth/callback/google`;

  if (!clientId) {
    return NextResponse.json({ error: 'Missing CLIENT_ID env var' }, { status: 500 });
  }

  const scope = encodeURIComponent('https://www.googleapis.com/auth/calendar.readonly');
  const authUrl = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&scope=${scope}&access_type=offline&prompt=consent`;

  return NextResponse.redirect(authUrl);
}
