import { NextResponse } from 'next/server';
import { getTokenCookie, setTokenCookie } from '@/lib/tokenCookie';

export async function GET() {
    let tokenData = await getTokenCookie();

    if (!tokenData || (!tokenData.access_token && !tokenData.refresh_token)) {
        return NextResponse.json({ error: 'Not connected' }, { status: 401 });
    }

    // Refresh access token if expired and we have a refresh token
    const now = Date.now();
    if (tokenData.refresh_token && (!tokenData.access_token || (tokenData.expires_at && now > (tokenData.expires_at as number) - 60000))) {
        const clientId = process.env.CLIENT_ID;
        const clientSecret = process.env.CLIENT_SECRET;

        try {
            const refreshRes = await fetch('https://oauth2.googleapis.com/token', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams({
                    client_id: clientId || '',
                    client_secret: clientSecret || '',
                    grant_type: 'refresh_token',
                    refresh_token: tokenData.refresh_token as string,
                }),
            });

            if (refreshRes.ok) {
                const refreshedData = await refreshRes.json();
                tokenData = {
                    ...tokenData,
                    access_token: refreshedData.access_token,
                    expires_in: refreshedData.expires_in,
                    expires_at: now + (refreshedData.expires_in * 1000)
                };
                if (refreshedData.refresh_token) {
                    tokenData.refresh_token = refreshedData.refresh_token;
                }
                await setTokenCookie(tokenData);
            } else {
                return NextResponse.json({ error: 'Failed to refresh token' }, { status: 401 });
            }
        } catch {
            return NextResponse.json({ error: 'Auth service unreachable' }, { status: 503 });
        }
    }

    if (!tokenData.access_token) {
        return NextResponse.json({ error: 'No access token available' }, { status: 401 });
    }

    try {
        const startOfDay = new Date();
        startOfDay.setHours(0, 0, 0, 0);
        const endOfDay = new Date();
        endOfDay.setHours(23, 59, 59, 999);

        // Fetch events with timeout controller
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 10000); // 10s timeout

        const res = await fetch(`https://www.googleapis.com/calendar/v3/calendars/primary/events?timeMin=${startOfDay.toISOString()}&timeMax=${endOfDay.toISOString()}&singleEvents=true&orderBy=startTime`, {
            headers: { Authorization: `Bearer ${tokenData.access_token}` },
            signal: controller.signal
        });

        clearTimeout(timeoutId);

        if (res.status === 401) {
            return NextResponse.json({ error: 'Token expired or invalid' }, { status: 401 });
        }

        if (!res.ok) {
            return NextResponse.json({ error: 'Failed to fetch calendar' }, { status: 500 });
        }

        const data = await res.json();
        const events = (data.items || [])
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            .filter((e: any) => e.start?.dateTime && e.end?.dateTime)
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            .map((e: any) => ({
                id: e.id,
                title: e.summary || 'Study Session',
                startTime: new Date(e.start.dateTime).getTime(),
                endTime: new Date(e.end.dateTime).getTime()
            }));

        return NextResponse.json({ events });

    } catch (err: unknown) {
        if (err instanceof Error && err.name === 'AbortError') {
            return NextResponse.json({ error: 'Request timeout' }, { status: 504 });
        }
        console.error("Calendar fetch failed"); // Safe log without token info
        return NextResponse.json({ error: 'Internal Server Error' }, { status: 500 });
    }
}
