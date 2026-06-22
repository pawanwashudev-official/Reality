import { NextResponse } from 'next/server';

export async function GET() {
  const clientId = process.env.CLIENT_ID;
  const redirectUri = process.env.NEXT_PUBLIC_APP_URL ? `${process.env.NEXT_PUBLIC_APP_URL}/api/auth/callback/google` : 'http://localhost:3000/api/auth/callback/google';

  if (!clientId) {
    return NextResponse.json({ error: 'Missing CLIENT_ID env var' }, { status: 500 });
  }

  const scope = encodeURIComponent('https://www.googleapis.com/auth/calendar.readonly');
  const authUrl = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&scope=${scope}&access_type=offline&prompt=consent`;

  return NextResponse.redirect(authUrl);
}
