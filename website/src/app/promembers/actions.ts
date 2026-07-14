"use server";

export async function fetchSensitiveMemberData(
  userId?: string,
  adminUserId?: string,
  adminPassword?: string
) {
  const baseUrl = process.env.Pro_Members_DB_URL;
  if (!baseUrl) {
    return { error: "Pro_Members_DB_URL not defined" };
  }
  const dbUrl = baseUrl.replace(/\/+$/, '') + '/api/pro-members';
  const workerSecret = process.env.WORKER_CONNECTION_SECRET || '';

  let isAdmin = false;
  
  // 1. Check ADMIN environment variable (16-char ID + password)
  const adminSecret = process.env.ADMIN || '';
  if (adminSecret && adminUserId && adminPassword) {
    const expectedAdminUserId = adminSecret.substring(0, 16);
    const expectedAdminPassword = adminSecret.substring(16);
    if (adminUserId === expectedAdminUserId && adminPassword === expectedAdminPassword) {
      isAdmin = true;
    }
  }

  // 2. Legacy fallback to workerSecret substring for backward compatibility
  if (!isAdmin && adminPassword && workerSecret) {
    const adminSecretHalf = workerSecret.substring(0, 16);
    if (adminPassword === adminSecretHalf) {
      isAdmin = true;
    }
  }

  // If not admin and no userId is provided, unauthorized
  if (!isAdmin && !userId) {
     return { error: "Unauthorized" };
  }

  try {
    let res = await fetch(dbUrl, {
      method: 'GET',
      headers: {
        'x-worker-secret': workerSecret
      },
      next: { revalidate: 60 } // Cache this fetch so we don't spam the DB worker
    });

    if (res.status === 302 || res.status === 303 || res.status === 307 || res.status === 308) {
      const redirectUrl = res.headers.get('location');
      if (redirectUrl) {
        res = await fetch(redirectUrl, {
          method: 'GET',
          headers: {
            'x-worker-secret': workerSecret
          },
          next: { revalidate: 60 }
        });
      }
    }

    if (!res.ok) {
      return { error: `Failed to fetch pro members: ${res.status}` };
    }

    const data = await res.json();
    let members = data?.members || [];

    // Filter to build a dictionary of sensitive data
    const result: Record<string, { status: string | null, expiryDate: string | null }> = {};

    if (isAdmin) {
       // Return all
       for (const m of members) {
           result[m.userId] = {
               status: m.status || null,
               expiryDate: m.expiryDate || null
           };
       }
       return { isAdmin: true, data: result };
    } else if (userId) {
       // Return only one exact match
       const m = members.find((user: any) => user.userId.toLowerCase() === userId.toLowerCase());
       if (m) {
           result[m.userId] = {
               status: m.status || null,
               expiryDate: m.expiryDate || null
           };
       }
       return { isAdmin: false, data: result };
    }

    return { error: "Not found" };

  } catch (error: any) {
    return { error: error.message };
  }
}

export async function verifyMemberId(userId: string) {
  if (!userId || userId.trim().length !== 16) {
    return { error: "User ID must be exactly 16 characters." };
  }

  const baseUrl = process.env.Pro_Members_DB_URL;
  if (!baseUrl) {
    return { error: "Pro_Members_DB_URL not defined" };
  }
  const dbUrl = baseUrl.replace(/\/+$/, '') + '/api/pro-members';
  const workerSecret = process.env.WORKER_CONNECTION_SECRET || '';

  try {
    let res = await fetch(dbUrl, {
      method: 'GET',
      headers: {
        'x-worker-secret': workerSecret
      },
      next: { revalidate: 60 }
    });

    if (res.status === 302 || res.status === 303 || res.status === 307 || res.status === 308) {
      const redirectUrl = res.headers.get('location');
      if (redirectUrl) {
        res = await fetch(redirectUrl, {
          method: 'GET',
          headers: {
            'x-worker-secret': workerSecret
          },
          next: { revalidate: 60 }
        });
      }
    }

    if (!res.ok) {
      return { error: `Failed to query member database: ${res.status}` };
    }

    const data = await res.json();
    const members = data?.members || [];
    const found = members.find((m: any) => m.userId.toLowerCase() === userId.trim().toLowerCase());

    if (found) {
      return {
        success: true,
        member: {
          userId: found.userId,
          dateJoined: found.date || found.dateJoined || '',
          hasAccess: found.status === 'V',
        }
      };
    }
    return { success: false, error: "User ID not found in our records." };
  } catch (err: any) {
    return { error: err.message };
  }
}
