'use server';


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
    return { success: true };
  } else {
    return { success: false, error: 'Invalid credentials' };
  }
}
