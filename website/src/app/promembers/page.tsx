import React from 'react';
import { Crown, Database, Heart } from 'lucide-react';
import ProMembersClient from './ProMembersClient';

// Force dynamic rendering to allow search params but utilize caching
export const dynamic = 'force-dynamic';

interface ProMember {
  userId: string;
  dateJoined: string;
  hasAccess: boolean;
}

interface MembersResponse {
  members: ProMember[];
  total: number;
  page: number;
  pageSize: number;
}

async function getProMembers(page: number, pageSize: number): Promise<MembersResponse | null> {
  const dbUrl = process.env.Pro_Members_DB_URL;
  if (!dbUrl) {
    console.error("Pro_Members_DB_URL environment variable is not defined.");
    return null;
  }

  try {
    const res = await fetch(`${dbUrl}?page=${page}&pageSize=${pageSize}`, {
      // Use Next.js caching to revalidate the data every 60 seconds.
      // This fulfills the latency and caching requirements, keeping the page incredibly fast.
      next: { revalidate: 60 }
    });

    if (!res.ok) {
      console.error(`Failed to fetch pro members: ${res.status} ${res.statusText}`);
      return null;
    }

    return await res.json();
  } catch (error) {
    console.error("Error fetching pro members:", error);
    return null;
  }
}

export default async function ProMembersPage({
  searchParams,
}: {
  searchParams: { [key: string]: string | string[] | undefined };
}) {
  const pageParam = searchParams.page;
  const currentPage = typeof pageParam === 'string' ? parseInt(pageParam, 10) : 1;
  const pageSize = 50;

  const data = await getProMembers(currentPage, pageSize);
  const members = data?.members || [];
  const total = data?.total || 0;
  const totalPages = Math.ceil(total / pageSize);

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
            Live directory of Reality Pro active users.
          </p>

          <div className="flex justify-center gap-6">
            <div className="flex flex-col items-center p-4 bg-neural-card/50 border border-gray-800 rounded-xl min-w-[120px]">
              <Database className="text-neural-cyan mb-2" size={24} />
              <span className="text-2xl font-bold text-white">{total}</span>
              <span className="text-xs text-gray-500 font-mono uppercase mt-1">Total Active</span>
            </div>
          </div>

          <div className="mt-12 max-w-3xl mx-auto bg-neural-card/30 border border-gray-800 p-6 rounded-2xl flex flex-col sm:flex-row items-center gap-6 text-left shadow-lg backdrop-blur-sm">
            <div className="w-12 h-12 rounded-full bg-pink-500/10 border border-pink-500/20 flex items-center justify-center shrink-0">
              <Heart className="text-pink-500 fill-pink-500/20" size={24} />
            </div>
            <div>
              <h3 className="text-white font-bold mb-1 tracking-tight">Our Heartfelt Thanks</h3>
              <p className="text-sm text-gray-400 leading-relaxed">
                These are the people who contributed to Reality. Thanks to them, we are able to maintain this open-source project and provide proper updates and patches on a regular basis.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Interactive Client Component for Search, Sort, and Grid Display */}
      <ProMembersClient
        initialMembers={members}
        currentPage={currentPage}
        totalPages={totalPages}
      />
    </div>
  );
}
