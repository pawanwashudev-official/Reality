import { cookies } from 'next/headers';

const ALGORITHM = 'AES-GCM';
const COOKIE_NAME = 'reality_google_auth';
const STATE_COOKIE_NAME = 'reality_oauth_state';

async function getCryptoKey(secret: string) {
    const encoder = new TextEncoder();
    const keyData = encoder.encode(secret.padEnd(32, '0').slice(0, 32));
    return await crypto.subtle.importKey(
        'raw',
        keyData,
        { name: ALGORITHM },
        false,
        ['encrypt', 'decrypt']
    );
}

export async function encryptToken(payload: Record<string, unknown>): Promise<string> {
    const secret = process.env.TOKEN_ENCRYPTION_SECRET;
    if (!secret) throw new Error('TOKEN_ENCRYPTION_SECRET is missing');
    const key = await getCryptoKey(secret);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const encodedPayload = new TextEncoder().encode(JSON.stringify(payload));

    const encryptedContent = await crypto.subtle.encrypt(
        { name: ALGORITHM, iv },
        key,
        encodedPayload
    );

    const encryptedBuffer = new Uint8Array(encryptedContent);
    const combined = new Uint8Array(iv.length + encryptedBuffer.length);
    combined.set(iv, 0);
    combined.set(encryptedBuffer, iv.length);

    return Buffer.from(combined).toString('base64');
}

export async function decryptToken(encryptedText: string): Promise<Record<string, unknown> | null> {
    try {
        const secret = process.env.TOKEN_ENCRYPTION_SECRET;
        if (!secret) throw new Error('TOKEN_ENCRYPTION_SECRET is missing');
        const key = await getCryptoKey(secret);
        const combined = new Uint8Array(Buffer.from(encryptedText, 'base64'));

        const iv = combined.slice(0, 12);
        const encryptedBuffer = combined.slice(12);

        const decryptedContent = await crypto.subtle.decrypt(
            { name: ALGORITHM, iv },
            key,
            encryptedBuffer
        );

        const decodedPayload = new TextDecoder().decode(decryptedContent);
        return JSON.parse(decodedPayload);
    } catch (e) {
        console.error('Failed to decrypt token:', e);
        return null;
    }
}

export async function setTokenCookie(payload: Record<string, unknown>) {
    const encrypted = await encryptToken(payload);
    const cookieStore = await cookies();
    cookieStore.set({
        name: COOKIE_NAME,
        value: encrypted,
        httpOnly: true,
        secure: process.env.NODE_ENV === 'production',
        sameSite: 'lax',
        path: '/',
        maxAge: 30 * 24 * 60 * 60,
    });
}

export async function getTokenCookie() {
    const cookieStore = await cookies();
    const cookie = cookieStore.get(COOKIE_NAME);
    if (!cookie?.value) return null;
    return await decryptToken(cookie.value);
}

export async function clearTokenCookie() {
    const cookieStore = await cookies();
    cookieStore.delete(COOKIE_NAME);
}

export async function setStateCookie(state: string) {
    const cookieStore = await cookies();
    cookieStore.set({
        name: STATE_COOKIE_NAME,
        value: state,
        httpOnly: true,
        secure: process.env.NODE_ENV === 'production',
        sameSite: 'lax',
        path: '/',
        maxAge: 15 * 60,
    });
}

export async function getStateCookie() {
    const cookieStore = await cookies();
    return cookieStore.get(STATE_COOKIE_NAME)?.value;
}

export async function clearStateCookie() {
    const cookieStore = await cookies();
    cookieStore.delete(STATE_COOKIE_NAME);
}
