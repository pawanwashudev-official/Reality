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

async function getProMembers(): Promise<MembersResponse | null> {
  const baseUrl = process.env.Pro_Members_DB_URL;
  if (!baseUrl) {
    console.error("Pro_Members_DB_URL environment variable is not defined.");
    return null;
  }

  // Remove trailing slashes and append the endpoint path
  const dbUrl = baseUrl.replace(/\/+$/, '') + '/api/pro-members';
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
      // Fallback to default follow behavior if manual redirect failed to catch it
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
  const data = await getProMembers();
  const allMembers = data?.members || [];
  const totalActive = data?.total || 0;

  // Server-side filtering, sorting, and pagination
  const rawSearch = searchParams?.search || '';
  const searchQuery = rawSearch.trim().toLowerCase();
  const sortOrder = (searchParams?.sort || 'latest') as 'latest' | 'oldest';
  const currentPage = parseInt(searchParams?.page || '1', 10);
  const pageSize = 50;

  // 1. Filter
  let filtered = allMembers;
  if (searchQuery) {
    filtered = allMembers.filter(m => m.userId.toLowerCase().includes(searchQuery));
  }

  // Helper to score plan priority for sorting
  const getPlanScore = (member: typeof allMembers[0]) => {
    if (member.status === 'V') return 4;
    if (member.status === 'P') return 3;
    if (member.status === 'N' || (member.expiryDate && member.status === 'N')) return 2;
    if (member.trial_plan) return 1;
    return 0;
  };

  // 2. Sort: prioritize paid plans (active/expired), then trial, then standard
  filtered.sort((a, b) => {
    const scoreA = getPlanScore(a);
    const scoreB = getPlanScore(b);
    if (scoreA !== scoreB) {
      return scoreB - scoreA; // Higher plan priority score first
    }
    const dateA = new Date(a.dateJoined).getTime();
    const dateB = new Date(b.dateJoined).getTime();
    if (isNaN(dateA) || isNaN(dateB)) return 0;
    return sortOrder === 'latest' ? dateB - dateA : dateA - dateB;
  });

  // 3. Paginate
  const totalFiltered = filtered.length;
  const startIndex = (currentPage - 1) * pageSize;
  const paginated = filtered.slice(startIndex, startIndex + pageSize);

  // Strip sensitive info before sending to client component
  const safeMembers = paginated.map(m => ({
    userId: m.userId,
    dateJoined: m.dateJoined,
    hasAccess: m.hasAccess,
    trial_plan: m.trial_plan,
    status: m.status,
    expiryDate: m.expiryDate
  }));

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
          totalFiltered={totalFiltered}
          initialSearch={rawSearch}
          initialSort={sortOrder}
          initialPage={currentPage}
          pageSize={pageSize}
        />
      </Suspense>
    </div>
  );
}

