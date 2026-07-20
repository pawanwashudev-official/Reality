import React from 'react';
import { Crown, Database, Heart } from 'lucide-react';
import { Suspense } from 'react';
import ProMembersClient from './ProMembersClient';

export const revalidate = 60;

interface ProMember {
  userId: string;
  dateJoined: string;
  hasAccess: boolean;
  status: string | null;
  expiryDate: string | null;
  trial_plan: string | null;
}

interface MembersResponse {
  members: ProMember[];
  total: number;
}

async function getProMembers(limit: number, offset: number, userId?: string): Promise<MembersResponse | null> {
  const baseUrl = process.env.Pro_Members_DB_URL;
  if (!baseUrl) {
    console.error("Pro_Members_DB_URL environment variable is not defined.");
    return null;
  }

  // Build the URL with query parameters
  let dbUrl = baseUrl.replace(/\/+$/, '') + '/api/pro-members';
  if (userId) {
    dbUrl += `?userId=${encodeURIComponent(userId)}`;
  } else {
    dbUrl += `?limit=${limit}&offset=${offset}`;
  }

  const workerSecret = process.env.WORKER_CONNECTION_SECRET || '';

  try {
    let res = await fetch(dbUrl, {
      method: 'GET',
      headers: {
        'x-worker-secret': workerSecret
      },
      redirect: 'manual',
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
    } else if (!res.ok) {
      // Fallback
      res = await fetch(dbUrl, {
        method: 'GET',
        headers: {
          'x-worker-secret': workerSecret
        },
        next: { revalidate: 60 }
      });
    }

    if (!res.ok) {
      console.error(`Failed to fetch pro members: ${res.status} ${res.statusText}`);
      return null;
    }

    const data = await res.json();

    let members: ProMember[] = [];
    let total = 0;

    if (data && typeof data === 'object') {
      if (Array.isArray(data.members)) {
        members = data.members.map((m: any) => ({
          userId: m.userId,
          dateJoined: m.date,
          hasAccess: m.status === 'V' || (m.trial_plan && !m.status),
          status: m.status || null,
          expiryDate: m.expiryDate || null,
          trial_plan: m.trial_plan || null
        }));
      }
      if (typeof data.totalMembers === 'number') {
        total = data.totalMembers;
      } else {
        total = members.length;
      }
    }

    return {
      members,
      total
    };
  } catch (error) {
    console.error("Error fetching pro members:", error);
    return null;
  }
}

interface PageProps {
  searchParams: {
    page?: string;
    search?: string;
    sort?: string;
  };
}

export default async function ProMembersPage({ searchParams }: PageProps) {
  const rawSearch = searchParams?.search || '';
  const searchQuery = rawSearch.trim().toLowerCase();
  const sortOrder = (searchParams?.sort || 'latest') as 'latest' | 'oldest';
  const currentPage = parseInt(searchParams?.page || '1', 10);
  const pageSize = 10;
  const offset = (currentPage - 1) * pageSize;

  const data = await getProMembers(pageSize, offset, searchQuery);
  const allMembers = data?.members || [];
  const totalActive = data?.total || 0;

  // Map and compute status securely on the server
  const classifiedMembers = allMembers.map(m => {
    let computedStatus = 'STANDARD';
    if (m.expiryDate) {
      const parts = m.expiryDate.split('-');
      if (parts.length === 2) {
        const expiryUnix = parseInt(parts[0], 10);
        if (!isNaN(expiryUnix) && expiryUnix > Date.now()) {
          computedStatus = 'ELITE';
        }
      }
    }
    if (computedStatus === 'STANDARD' && m.trial_plan) {
      const parts = m.trial_plan.split('-');
      if (parts.length === 2) {
        const trialExpiryUnix = parseInt(parts[0], 10);
        if (!isNaN(trialExpiryUnix) && trialExpiryUnix > Date.now()) {
          computedStatus = 'TRIAL';
        }
      }
    }
    let displayUserId = m.userId;
    // Only reveal full ID if it was an exact search match
    const isExactSearch = searchQuery.length === 16 && searchQuery === m.userId.toLowerCase();
    
    if (!isExactSearch && displayUserId.length >= 8) {
      displayUserId = displayUserId.substring(0, 4) + '********' + displayUserId.substring(displayUserId.length - 4);
    }

    return {
      userId: displayUserId,
      dateJoined: m.dateJoined,
      status: computedStatus
    };
  });

  // Sort is already handled by the worker if no search query. If search query, it's just 1 user.
  // We can still sort locally just in case.
  const getPlanScore = (status: string) => {
    if (status === 'ELITE') return 2;
    if (status === 'TRIAL') return 1;
    return 0;
  };

  classifiedMembers.sort((a, b) => {
    const scoreA = getPlanScore(a.status);
    const scoreB = getPlanScore(b.status);
    if (scoreA !== scoreB) {
      return scoreB - scoreA;
    }
    const dateA = new Date(a.dateJoined).getTime();
    const dateB = new Date(b.dateJoined).getTime();
    if (isNaN(dateA) || isNaN(dateB)) return 0;
    return sortOrder === 'latest' ? dateB - dateA : dateA - dateB;
  });

  const safeMembers = classifiedMembers;

  return (
    <div className="min-h-screen bg-neural-bg font-outfit text-gray-100 selection:bg-neural-cyan selection:text-black">
      {/* Header Section */}
      <section className="relative overflow-hidden border-b border-gray-800 pb-12 pt-24">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-yellow-500/10 via-neural-bg to-neural-bg opacity-50 z-0"></div>
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center relative z-10">
          <div className="inline-flex items-center gap-2 mb-4 px-3 py-1 rounded-full border border-yellow-500/30 bg-yellow-500/10 text-yellow-500 text-xs font-mono tracking-widest uppercase">
             <Crown size={14} />
             <span>ELITE TIER</span>
          </div>
          <h1 className="text-4xl md:text-6xl font-extrabold tracking-tight text-white mb-4 drop-shadow-md flex justify-center items-center gap-4">
            Pro Members
          </h1>
          <p className="max-w-2xl mx-auto text-lg text-gray-400 mb-8 font-mono">
            Live directory of Reality Elite Member active users.
          </p>

          <div className="flex justify-center gap-6">
            <div className="flex flex-col items-center p-4 bg-neural-card/50 border border-gray-800 rounded-xl min-w-[120px]">
              <Database className="text-neural-cyan mb-2" size={24} />
              <span className="text-2xl font-bold text-white">{totalActive}</span>
              <span className="text-xs text-gray-500 font-mono uppercase mt-1">Total Active</span>
            </div>
          </div>

          <div className="mt-12 max-w-3xl mx-auto bg-neural-card/30 border border-gray-800 p-6 rounded-2xl flex flex-col sm:flex-row items-center gap-6 text-left shadow-lg backdrop-blur-sm">
            <div className="w-12 h-12 rounded-full bg-pink-500/10 border border-pink-500/20 flex items-center justify-center shrink-0">
              <Heart className="text-pink-500 fill-pink-500/20" size={24} />
            </div>
            <div>
              <h3 className="text-white font-bold mb-1 tracking-tight">Our Heartfelt Thanks</h3>
              <p className="text-sm text-gray-400 leading-relaxed mb-2">
                These are the people who contributed to Reality. Thanks to them, we are able to maintain this source-available project and provide proper updates and patches on a regular basis.
              </p>
              <p className="text-xs text-gray-500 font-mono leading-relaxed bg-black/30 p-3 rounded-lg border border-gray-800/50">
                <strong className="text-gray-400">Privacy Notice:</strong> We do not collect or store your name, email, or any personal details. The identifiers shown below are generated hashed User IDs used strictly for anonymous Pro verification.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Interactive Client Component for Search, Sort, and Grid Display */}
      <Suspense fallback={<div className="text-center py-20 font-mono text-gray-500">Loading members directory...</div>}>
        <ProMembersClient
          initialMembers={safeMembers}
          totalFiltered={searchQuery ? safeMembers.length : totalActive}
          initialSearch={rawSearch}
          initialSort={sortOrder}
          initialPage={currentPage}
          pageSize={pageSize}
        />
      </Suspense>
    </div>
  );
}
