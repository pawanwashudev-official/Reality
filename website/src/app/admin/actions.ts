'use server';

import { cookies } from 'next/headers';

export async function loginAction(formData: FormData) {
  const username = formData.get('username') as string;
  const password = formData.get('password') as string;

  const adminSecret = process.env.ADMIN || '';
  const expectedAdminUsername = adminSecret.substring(0, 16);
  const expectedAdminPassword = adminSecret.substring(16);

  if (
    username &&
    password &&
    username === expectedAdminUsername &&
    password === expectedAdminPassword
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
