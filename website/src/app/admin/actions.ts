'use server';

import { cookies } from 'next/headers';

export async function loginAction(formData: FormData) {
  const username = formData.get('username') as string;
  const password = formData.get('password') as string;

  const validUsername = process.env.ADMIN_USERNAME || '';
  const validPassword = process.env.ADMIN_PASSWORD || '';

  if (
    username &&
    password &&
    username === validUsername &&
    password === validPassword
  ) {
    // Basic session cookie
    const cookieStore = await cookies();
    cookieStore.set('admin_session', 'authenticated', {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      maxAge: 60 * 60 * 24, // 1 day
      path: '/',
    });
    return { success: true };
  } else {
    return { success: false, error: 'Invalid credentials' };
  }
}
