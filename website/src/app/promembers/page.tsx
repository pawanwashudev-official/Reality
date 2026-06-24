import React from 'react';
import { Shield, Crown, User, Calendar, Database, Sparkles, ChevronLeft, ChevronRight } from 'lucide-react';

// Force dynamic rendering to ensure fresh data and allow search params
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
      // Don't cache so we get live data
      cache: 'no-store'
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
        </div>
      </section>

      {/* Members Grid */}
      <section className="py-16 relative z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">

          {members.length === 0 ? (
            <div className="text-center py-20 bg-neural-card/30 border border-gray-800 rounded-2xl">
              <Shield className="mx-auto text-gray-600 mb-4" size={48} />
              <h3 className="text-xl font-bold text-gray-400">No members found</h3>
              <p className="text-gray-500 mt-2">Could not retrieve the member list at this time.</p>
            </div>
          ) : (
            <>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                {members.map((member, index) => (
                  <MemberCard key={`${member.userId}-${index}`} member={member} />
                ))}
              </div>

              {/* Pagination */}
              {totalPages > 1 && (
                <div className="mt-12 flex justify-center items-center gap-4">
                  {currentPage > 1 ? (
                    <a
                      href={`?page=${currentPage - 1}`}
                      className="p-2 bg-neural-card border border-gray-700 rounded-lg hover:border-neural-cyan hover:text-neural-cyan transition-colors"
                    >
                      <ChevronLeft size={20} />
                    </a>
                  ) : (
                    <div className="p-2 bg-neural-card/30 border border-gray-800 text-gray-600 rounded-lg cursor-not-allowed">
                      <ChevronLeft size={20} />
                    </div>
                  )}

                  <span className="font-mono text-gray-400">
                    Page <span className="text-white">{currentPage}</span> of {totalPages}
                  </span>

                  {currentPage < totalPages ? (
                    <a
                      href={`?page=${currentPage + 1}`}
                      className="p-2 bg-neural-card border border-gray-700 rounded-lg hover:border-neural-cyan hover:text-neural-cyan transition-colors"
                    >
                      <ChevronRight size={20} />
                    </a>
                  ) : (
                    <div className="p-2 bg-neural-card/30 border border-gray-800 text-gray-600 rounded-lg cursor-not-allowed">
                      <ChevronRight size={20} />
                    </div>
                  )}
                </div>
              )}
            </>
          )}

        </div>
      </section>
    </div>
  );
}

function MemberCard({ member }: { member: ProMember }) {
  // Format the date if it's a valid string
  let displayDate = member.dateJoined;
  try {
    const d = new Date(member.dateJoined);
    if (!isNaN(d.getTime())) {
      displayDate = d.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
      });
    }
  } catch {
    // Keep original string if parsing fails
  }

  // Obfuscate the user ID slightly for privacy if it's long enough
  let displayId = member.userId;
  if (displayId.length > 8) {
    displayId = `${displayId.substring(0, 4)}...${displayId.substring(displayId.length - 4)}`;
  }

  return (
    <div className="group relative bg-neural-card border border-gray-800 p-6 rounded-2xl hover:border-yellow-500/50 transition-all duration-300 shadow-lg hover:shadow-yellow-500/10">
      <div className="absolute top-0 right-0 p-4 opacity-10 group-hover:opacity-100 transition-opacity duration-500">
        <Sparkles className="text-yellow-500" size={40} />
      </div>

      <div className="flex items-center gap-4 mb-4 relative z-10">
        <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-gray-800 to-black border border-gray-700 flex items-center justify-center shadow-inner">
          <User className="text-gray-400 group-hover:text-white transition-colors" size={24} />
        </div>
        <div>
          <div className="flex items-center gap-2">
            <Shield className="text-green-500" size={14} />
            <span className="text-xs font-bold text-green-500 tracking-wider">VERIFIED</span>
          </div>
          <div className="font-mono text-white text-lg mt-1 tracking-tight" title={member.userId}>
            {displayId}
          </div>
        </div>
      </div>

      <div className="pt-4 border-t border-gray-800/50 flex items-center justify-between relative z-10">
        <div className="flex items-center gap-2 text-gray-500 text-sm font-mono">
          <Calendar size={14} />
          <span>Joined</span>
        </div>
        <div className="text-gray-300 text-sm font-medium">
          {displayDate}
        </div>
      </div>
    </div>
  );
}
