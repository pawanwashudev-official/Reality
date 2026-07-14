"use client";

import React, { useState, useMemo, useEffect, useRef } from 'react';
import { Shield, User, Calendar, Sparkles, ChevronLeft, ChevronRight, Search, SlidersHorizontal, Share2, Lock, Clock, CreditCard } from 'lucide-react';
import { useRouter, useSearchParams } from 'next/navigation';
import ShareCertificateModal from './ShareCertificateModal';
import { fetchSensitiveMemberData } from './actions';

interface ProMember {
  userId: string;
  dateJoined: string;
  hasAccess: boolean;
  status?: string | null;
  expiryDate?: string | null;
}

interface ProMembersClientProps {
  initialMembers: ProMember[];
}

export default function ProMembersClient({ initialMembers }: ProMembersClientProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [searchQuery, setSearchQuery] = useState('');
  const [sortOrder, setSortOrder] = useState<'latest' | 'oldest'>('latest');
  const [isShareModalOpen, setIsShareModalOpen] = useState(false);

  const [adminPassword, setAdminPassword] = useState('');
  const [isAdmin, setIsAdmin] = useState(false);
  const [sensitiveData, setSensitiveData] = useState<Record<string, { status: string | null, expiryDate: string | null }>>({});

  useEffect(() => {
    const fetchData = async () => {
      // Check for exact match in searchQuery
      const exactMatch = initialMembers.find(m => m.userId.toLowerCase() === searchQuery.trim().toLowerCase());

      if (adminPassword.length > 0 || exactMatch) {
         const response = await fetchSensitiveMemberData(exactMatch?.userId, adminPassword);
         if (response && !response.error && response.data) {
             setSensitiveData(response.data);
             setIsAdmin(!!response.isAdmin);
         } else {
             if (adminPassword.length > 0) setIsAdmin(false);
         }
      } else {
         setSensitiveData({});
         setIsAdmin(false);
      }
    };

    // Debounce slightly to prevent spamming
    const timeout = setTimeout(() => {
        fetchData();
    }, 300);

    return () => clearTimeout(timeout);
  }, [searchQuery, adminPassword, initialMembers]);

  const pageParam = searchParams.get('page');
  const verifyParam = searchParams.get('verify');
  const [currentPage, setCurrentPage] = useState(pageParam ? parseInt(pageParam, 10) : 1);
  const pageSize = 50;

  // Sync state when URL changes externally (e.g. back button)
  useEffect(() => {
    const page = searchParams.get('page');
    if (page) {
      setCurrentPage(parseInt(page, 10));
    }

    const verify = searchParams.get('verify');
    if (verify) {
      setSearchQuery(verify);
      setIsShareModalOpen(true);
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
      {/* Admin Access Section */}
      <section className="py-4 border-b border-gray-800 bg-neural-bg relative z-20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2 text-gray-400">
            <Lock size={16} />
            <span className="text-sm font-mono">Neubofy Team Access</span>
          </div>
          <div className="relative w-full sm:w-64">
             <input
                type="password"
                placeholder="Enter connection secret..."
                value={adminPassword}
                onChange={(e) => setAdminPassword(e.target.value)}
                className={`block w-full px-3 py-1.5 bg-black/50 border ${isAdmin ? 'border-green-500/50 focus:ring-green-500 focus:border-green-500' : 'border-gray-700 focus:ring-neural-cyan focus:border-neural-cyan'} rounded-lg text-gray-300 placeholder-gray-600 focus:outline-none focus:ring-1 transition-colors font-mono text-sm`}
             />
             {isAdmin && <Shield className="absolute right-3 top-1.5 text-green-500" size={16} />}
          </div>
        </div>
      </section>

      {/* Call to Action: Share */}
      <section className="py-8 border-b border-gray-800 bg-neural-bg relative z-20 flex justify-center">
        <button
          onClick={() => setIsShareModalOpen(true)}
          className="group relative px-8 py-4 bg-gradient-to-r from-neural-cyan/20 to-blue-500/10 border border-neural-cyan/50 rounded-2xl overflow-hidden hover:scale-105 transition-all duration-300 shadow-[0_0_20px_rgba(0,229,255,0.15)] hover:shadow-[0_0_30px_rgba(0,229,255,0.3)] flex items-center gap-3"
        >
          <div className="absolute inset-0 bg-neural-cyan/10 translate-y-full group-hover:translate-y-0 transition-transform duration-300 ease-out z-0"></div>
          <Share2 className="text-neural-cyan relative z-10" size={24} />
          <span className="text-lg font-bold text-white relative z-10 font-outfit tracking-wide group-hover:text-neural-cyan transition-colors">
            Get Member Card & Share on Social Media
          </span>
          <Sparkles className="absolute top-2 right-2 text-neural-cyan/50 opacity-0 group-hover:opacity-100 transition-opacity z-10" size={16} />
        </button>
      </section>

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
                  <MemberCard
                     key={`${member.userId}-${index}`}
                     member={{...member, ...sensitiveData[member.userId]}}
                     searchQuery={searchQuery}
                     isAdmin={isAdmin}
                  />
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

      <ShareCertificateModal
        isOpen={isShareModalOpen}
        onClose={() => setIsShareModalOpen(false)}
        members={initialMembers}
      />
    </>
  );
}

function MemberCard({ member, searchQuery, isAdmin }: { member: ProMember, searchQuery: string, isAdmin: boolean }) {
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

  const isExactMatch = searchQuery.trim().toLowerCase() === member.userId.toLowerCase();
  const showDetails = isExactMatch || isAdmin;

  let displayId = member.userId;
  // If the user hasn't typed the exact ID and is not admin, hide the middle
  if (!showDetails && member.userId.length > 8) {
     const start = member.userId.substring(0, 4);
     const end = member.userId.substring(member.userId.length - 4);
     displayId = `${start}****${end}`;
  }

  let subStartDate = 'N/A';
  let subEndDate = 'N/A';
  let subMonths = 'N/A';

  if (member.expiryDate) {
    const parts = member.expiryDate.split('-');
    if (parts.length >= 2) {
      const expiryUnix = parseInt(parts[0], 10);
      const months = parseInt(parts[1], 10);
      if (!isNaN(expiryUnix) && !isNaN(months)) {
        subMonths = `${months} month${months > 1 ? 's' : ''}`;

        const endD = new Date(expiryUnix);
        subEndDate = endD.toLocaleDateString('en-US', {
          year: 'numeric',
          month: 'short',
          day: 'numeric'
        });

        // App duration ms: Math.floor(365/12) * months * 24*60*60*1000
        const durationMs = Math.floor(365 / 12) * months * 24 * 60 * 60 * 1000;
        const startD = new Date(expiryUnix - durationMs);
        subStartDate = startD.toLocaleDateString('en-US', {
           year: 'numeric',
           month: 'short',
           day: 'numeric'
        });
      }
    }
  }

  let statusText = 'UNKNOWN';
  let statusColor = 'text-gray-500';
  let statusIcon = <Shield className="text-gray-500 shrink-0" size={14} />;

  if (member.status === 'V') {
    statusText = 'VERIFIED';
    statusColor = 'text-green-500';
    statusIcon = <Shield className="text-green-500 shrink-0" size={14} />;
  } else if (member.status === 'P') {
    statusText = 'PENDING';
    statusColor = 'text-yellow-500';
    statusIcon = <Clock className="text-yellow-500 shrink-0" size={14} />;
  }

  return (
    <div className={`group relative bg-neural-card border ${showDetails ? 'border-neural-cyan/30' : 'border-gray-800'} p-6 rounded-2xl hover:border-neural-cyan/50 transition-all duration-300 shadow-lg hover:shadow-neural-cyan/10 overflow-hidden`}>
      <div className="absolute top-0 right-0 p-4 opacity-10 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none">
        <Sparkles className={member.status === 'P' ? 'text-yellow-500' : 'text-neural-cyan'} size={40} />
      </div>

      <div className="flex items-center gap-4 mb-4 relative z-10">
        <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-gray-800 to-black border border-gray-700 flex items-center justify-center shadow-inner shrink-0">
          <User className="text-gray-400 group-hover:text-white transition-colors" size={24} />
        </div>
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            {statusIcon}
            <span className={`text-xs font-bold ${statusColor} tracking-wider`}>{statusText}</span>
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

      {showDetails && member.expiryDate && (
        <div className="mt-4 pt-4 border-t border-gray-800/50 space-y-3 relative z-10">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-gray-500 text-xs font-mono">
              <CreditCard size={12} />
              <span>Purchased</span>
            </div>
            <div className="text-gray-300 text-xs font-medium">
              {subStartDate}
            </div>
          </div>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-gray-500 text-xs font-mono">
              <Clock size={12} />
              <span>Duration</span>
            </div>
            <div className="text-gray-300 text-xs font-medium">
              {subMonths}
            </div>
          </div>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-gray-500 text-xs font-mono">
              <Calendar size={12} />
              <span>Expires</span>
            </div>
            <div className="text-neural-cyan text-xs font-bold">
              {subEndDate}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
