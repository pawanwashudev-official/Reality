"use client";

import React, { useState, useMemo, useEffect, useRef } from 'react';
import { Shield, User, Calendar, Sparkles, ChevronLeft, ChevronRight, Search, SlidersHorizontal } from 'lucide-react';
import { useRouter, useSearchParams } from 'next/navigation';

interface ProMember {
  userId: string;
  dateJoined: string;
  hasAccess: boolean;
}

interface ProMembersClientProps {
  initialMembers: ProMember[];
}

export default function ProMembersClient({ initialMembers }: ProMembersClientProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [searchQuery, setSearchQuery] = useState('');
  const [sortOrder, setSortOrder] = useState<'latest' | 'oldest'>('latest');

  const pageParam = searchParams.get('page');
  const [currentPage, setCurrentPage] = useState(pageParam ? parseInt(pageParam, 10) : 1);
  const pageSize = 50;

  // Sync state when URL changes externally (e.g. back button)
  useEffect(() => {
    const page = searchParams.get('page');
    if (page) {
      setCurrentPage(parseInt(page, 10));
    }
  }, [searchParams]);

  // Filter and sort the full dataset on the client.
  const processedMembers = useMemo(() => {
    let result = [...initialMembers];

    // 1. Search Filter
    if (searchQuery.trim()) {
      const lowerQuery = searchQuery.toLowerCase();
      result = result.filter(m => m.userId.toLowerCase().includes(lowerQuery));
    }

    // 2. Sort
    result.sort((a, b) => {
      const dateA = new Date(a.dateJoined).getTime();
      const dateB = new Date(b.dateJoined).getTime();

      // Fallback for invalid dates
      if (isNaN(dateA) || isNaN(dateB)) return 0;

      if (sortOrder === 'latest') {
        return dateB - dateA; // Newest first
      } else {
        return dateA - dateB; // Oldest first
      }
    });

    return result;
  }, [initialMembers, searchQuery, sortOrder]);

  const isMounted = useRef(false);
  // Reset to page 1 when search or sort changes
  useEffect(() => {
    if (!isMounted.current) {
      isMounted.current = true;
      return;
    }
    setCurrentPage(1);
    const params = new URLSearchParams(window.location.search);
    params.set('page', '1');
    router.replace(`?${params.toString()}`);
  }, [searchQuery, sortOrder, router]);

  const totalPages = Math.ceil(processedMembers.length / pageSize);

  // Calculate sliced members for current page
  const paginatedMembers = useMemo(() => {
    const startIndex = (currentPage - 1) * pageSize;
    return processedMembers.slice(startIndex, startIndex + pageSize);
  }, [processedMembers, currentPage, pageSize]);

  const handlePageChange = (newPage: number) => {
    setCurrentPage(newPage);
    // Preserve existing search params if any
    const params = new URLSearchParams(window.location.search);
    params.set('page', newPage.toString());
    router.push(`?${params.toString()}`);
  };

  return (
    <>
      {/* Control Bar: Search and Sort */}
      <section className="py-6 border-b border-gray-800 bg-neural-card/30 relative z-20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex flex-col sm:flex-row justify-between items-center gap-4">

                {/* Search */}
                <div className="relative w-full sm:w-96">
                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                        <Search className="text-gray-500" size={18} />
                    </div>
                    <input
                        type="text"
                        placeholder="Search by User ID..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="block w-full pl-10 pr-3 py-2.5 bg-black/50 border border-gray-700 rounded-xl text-gray-300 placeholder-gray-500 focus:outline-none focus:ring-1 focus:ring-neural-cyan focus:border-neural-cyan transition-colors font-mono text-sm"
                    />
                </div>

                {/* Sort */}
                <div className="flex items-center gap-3 w-full sm:w-auto">
                    <SlidersHorizontal className="text-gray-500 hidden sm:block" size={18} />
                    <select
                        value={sortOrder}
                        onChange={(e) => setSortOrder(e.target.value as 'latest' | 'oldest')}
                        className="block w-full sm:w-48 pl-3 pr-8 py-2.5 bg-black/50 border border-gray-700 rounded-xl text-gray-300 focus:outline-none focus:ring-1 focus:ring-neural-cyan focus:border-neural-cyan transition-colors text-sm appearance-none cursor-pointer"
                    >
                        <option value="latest">Latest to Oldest</option>
                        <option value="oldest">Oldest to Latest</option>
                    </select>
                </div>
            </div>
        </div>
      </section>

      {/* Members Grid */}
      <section className="py-16 relative z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">

          {processedMembers.length === 0 ? (
            <div className="text-center py-20 bg-neural-card/30 border border-gray-800 rounded-2xl">
              <Shield className="mx-auto text-gray-600 mb-4" size={48} />
              <h3 className="text-xl font-bold text-gray-400">No members found</h3>
              <p className="text-gray-500 mt-2">
                {searchQuery ? `No matches found for "${searchQuery}".` : 'Could not retrieve the member list at this time.'}
              </p>
            </div>
          ) : (
            <>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                {paginatedMembers.map((member, index) => (
                  <MemberCard key={`${member.userId}-${index}`} member={member} searchQuery={searchQuery} />
                ))}
              </div>

              {/* Pagination */}
              {totalPages > 1 && (
                <div className="mt-12 flex justify-center items-center gap-4">
                  <button
                    onClick={() => handlePageChange(currentPage - 1)}
                    disabled={currentPage <= 1}
                    className={`p-2 rounded-lg border transition-colors ${
                        currentPage > 1
                        ? 'bg-neural-card border-gray-700 hover:border-neural-cyan hover:text-neural-cyan'
                        : 'bg-neural-card/30 border-gray-800 text-gray-600 cursor-not-allowed'
                    }`}
                  >
                    <ChevronLeft size={20} />
                  </button>

                  <span className="font-mono text-gray-400">
                    Page <span className="text-white">{currentPage}</span> of {totalPages}
                  </span>

                  <button
                    onClick={() => handlePageChange(currentPage + 1)}
                    disabled={currentPage >= totalPages}
                    className={`p-2 rounded-lg border transition-colors ${
                        currentPage < totalPages
                        ? 'bg-neural-card border-gray-700 hover:border-neural-cyan hover:text-neural-cyan'
                        : 'bg-neural-card/30 border-gray-800 text-gray-600 cursor-not-allowed'
                    }`}
                  >
                    <ChevronRight size={20} />
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </section>
    </>
  );
}

function MemberCard({ member, searchQuery }: { member: ProMember, searchQuery: string }) {
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


  let displayId = member.userId;
  // If the user hasn't typed the exact ID, hide the middle
  if (searchQuery.trim().toLowerCase() !== member.userId.toLowerCase() && member.userId.length > 8) {
     const start = member.userId.substring(0, 4);
     const end = member.userId.substring(member.userId.length - 4);
     displayId = `${start}****${end}`;
  }


  return (
    <div className="group relative bg-neural-card border border-gray-800 p-6 rounded-2xl hover:border-yellow-500/50 transition-all duration-300 shadow-lg hover:shadow-yellow-500/10 overflow-hidden">
      <div className="absolute top-0 right-0 p-4 opacity-10 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none">
        <Sparkles className="text-yellow-500" size={40} />
      </div>

      <div className="flex items-center gap-4 mb-4 relative z-10">
        <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-gray-800 to-black border border-gray-700 flex items-center justify-center shadow-inner shrink-0">
          <User className="text-gray-400 group-hover:text-white transition-colors" size={24} />
        </div>
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <Shield className="text-green-500 shrink-0" size={14} />
            <span className="text-xs font-bold text-green-500 tracking-wider">VERIFIED</span>
          </div>
          <div className="font-mono text-white text-lg mt-1 tracking-tight truncate" title={member.userId}>
            {displayId}
          </div>
        </div>
      </div>

      <div className="pt-4 border-t border-gray-800/50 flex items-center justify-between relative z-10">
        <div className="flex items-center gap-2 text-gray-500 text-sm font-mono shrink-0">
          <Calendar size={14} />
          <span>Joined</span>
        </div>
        <div className="text-gray-300 text-sm font-medium truncate ml-2">
          {displayDate}
        </div>
      </div>
    </div>
  );
}
