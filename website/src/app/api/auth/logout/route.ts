import { NextResponse } from 'next/server';
import { clearTokenCookie, clearStateCookie } from '@/lib/tokenCookie';

export async function POST() {
    await clearTokenCookie();
    await clearStateCookie();
    return NextResponse.json({ success: true });
}
