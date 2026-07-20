"use client";

import React, { useState, useEffect, useMemo } from 'react';
import { Shield, User, Calendar, Sparkles, ChevronLeft, ChevronRight, Search, SlidersHorizontal, Share2, Lock, Clock, CreditCard, ShieldCheck, Crown } from 'lucide-react';
import { useRouter, useSearchParams, usePathname } from 'next/navigation';
import ShareCertificateModal from './ShareCertificateModal';
import { fetchSensitiveMemberData } from './actions';

interface ProMember {
  userId: string;
  dateJoined: string;
  hasAccess?: boolean;
  status?: string | null;
  expiryDate?: string | null;
  trial_plan?: string | null;
}

interface ProMembersClientProps {
  initialMembers: ProMember[];
  totalFiltered: number;
  initialSearch: string;
  initialSort: 'latest' | 'oldest';
  initialPage: number;
  pageSize: number;
}

export default function ProMembersClient({
  initialMembers,
  totalFiltered,
  initialSearch,
  initialSort,
  initialPage,
  pageSize,
}: ProMembersClientProps) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  // Search input state (local to input field)
  const [searchInput, setSearchInput] = useState(initialSearch);

  // Admin login credentials states
  const [adminUserIdInput, setAdminUserIdInput] = useState('');
  const [adminPasswordInput, setAdminPasswordInput] = useState('');
  const [isAdmin, setIsAdmin] = useState(false);
  const [adminError, setAdminError] = useState('');
  const [isVerifyingAdmin, setIsVerifyingAdmin] = useState(false);

  const [members, setMembers] = useState<ProMember[]>(initialMembers);
  const [isShareModalOpen, setIsShareModalOpen] = useState(false);
  const [selectedUserIdForCard, setSelectedUserIdForCard] = useState<string | null>(null);
  const [sensitiveData, setSensitiveData] = useState<Record<string, { status: string | null, expiryDate: string | null }>>({});

  // Sync input value if URL changes externally (e.g. back button)
  useEffect(() => {
    setSearchInput(initialSearch);
  }, [initialSearch]);

  // Sync members for non-admins when initialMembers (page) changes
  useEffect(() => {
    if (!isAdmin) {
      setMembers(initialMembers);
    }
  }, [initialMembers, isAdmin]);

  // Fetch sensitive details for searched member ONLY when search query is exactly 16 characters (full ID)
  useEffect(() => {
    const fetchSearchedDetail = async () => {
      const trimmedQuery = initialSearch.trim();
      if (trimmedQuery.length === 16) {
        const exactMatch = initialMembers.find(m => m.userId.toLowerCase() === trimmedQuery.toLowerCase());
        if (exactMatch) {
          try {
            const response = await fetchSensitiveMemberData(exactMatch.userId, undefined, undefined);
            if (response && !response.error && response.data) {
              setSensitiveData(prev => ({ ...prev, ...response.data }));
            }
          } catch (err) {
            console.error("Error fetching exact match details:", err);
          }
        }
      }
    };
    fetchSearchedDetail();
  }, [initialSearch, initialMembers]);

  // Fetch admin data on page change if currently logged in as admin
  useEffect(() => {
    if (isAdmin && adminUserIdInput && adminPasswordInput) {
      const fetchAdminData = async () => {
        try {
          const response = await fetchSensitiveMemberData(
            undefined,
            adminUserIdInput.trim(),
            adminPasswordInput.trim(),
            pageSize,
            (initialPage - 1) * pageSize
          );
          if (response && !response.error && response.data && response.members) {
            setSensitiveData(response.data);
            const adminMembers = response.members.map((m: any) => ({
              userId: m.userId,
              dateJoined: m.date,
              hasAccess: m.status === 'V' || (m.trial_plan && !m.status),
              status: m.status || null,
              expiryDate: m.expiryDate || null,
              trial_plan: m.trial_plan || null
            }));
            setMembers(adminMembers);
          }
        } catch (err) {
          console.error("Error fetching admin data on page change:", err);
        }
      };
      fetchAdminData();
    }
  }, [initialPage, initialSearch, isAdmin]);

  // Search submit handler (updates URL)
  const handleSearchSubmit = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const params = new URLSearchParams(searchParams.toString());
    if (searchInput.trim()) {
      params.set('search', searchInput.trim());
    } else {
      params.delete('search');
    }
    params.set('page', '1'); // Reset to page 1
    router.push(`${pathname}?${params.toString()}`);
  };

  // Sort change handler
  const handleSortChange = (newSort: 'latest' | 'oldest') => {
    const params = new URLSearchParams(searchParams.toString());
    params.set('sort', newSort);
    params.set('page', '1');
    router.push(`${pathname}?${params.toString()}`);
  };

  // Pagination handler
  const handlePageChange = (newPage: number) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set('page', newPage.toString());
    router.push(`${pathname}?${params.toString()}`);
  };

  // Admin submit credentials handler (prevents keystroke requests, uses form submit)
  const handleAdminSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setAdminError('');
    setIsVerifyingAdmin(true);

    if (!adminUserIdInput.trim() || !adminPasswordInput.trim()) {
      setAdminError('Please enter both Admin ID and Password.');
      setIsVerifyingAdmin(false);
      return;
    }

    try {
      const response = await fetchSensitiveMemberData(
        undefined,
        adminUserIdInput.trim(),
        adminPasswordInput.trim(),
        pageSize,
        (initialPage - 1) * pageSize
      );
      if (response && !response.error && response.data && response.members) {
        setSensitiveData(response.data);
        const adminMembers = response.members.map((m: any) => ({
          userId: m.userId,
          dateJoined: m.date,
          hasAccess: m.status === 'V' || (m.trial_plan && !m.status),
          status: m.status || null,
          expiryDate: m.expiryDate || null,
          trial_plan: m.trial_plan || null
        }));
        setMembers(adminMembers);
        setIsAdmin(true);
        setAdminError('');
      } else {
        setAdminError(response?.error || 'Invalid admin credentials.');
        setIsAdmin(false);
      }
    } catch (err: any) {
      setAdminError(err.message || 'An error occurred during verification.');
      setIsAdmin(false);
    } finally {
      setIsVerifyingAdmin(false);
    }
  };

  const totalPages = Math.ceil(totalFiltered / pageSize);

  return (
    <>
      {/* Admin Access Section */}
      <section className="py-6 border-b border-gray-800 bg-neural-bg relative z-20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
          <div className="flex items-center gap-2.5 text-gray-400">
            <Lock size={16} className="text-gray-500" />
            <div className="flex flex-col">
              <span className="text-sm font-bold font-mono text-gray-300">Neubofy Team Access</span>
              <span className="text-xs text-gray-500 font-mono">Unlock full directory subscription info</span>
            </div>
          </div>
          
          <div className="w-full md:w-auto flex flex-col items-start gap-2">
            <form onSubmit={handleAdminSubmit} className="flex flex-col sm:flex-row items-end gap-3 w-full sm:w-auto">
              <div className="relative w-full sm:w-48">
                <label htmlFor="admin-username" className="block text-[10px] text-gray-500 font-mono uppercase mb-1 font-bold tracking-wider">Admin ID</label>
                <input
                   id="admin-username"
                   name="username"
                   type="text"
                   autoComplete="username"
                   placeholder="Admin User ID..."
                   value={adminUserIdInput}
                   onChange={(e) => setAdminUserIdInput(e.target.value)}
                   className="block w-full px-3 py-2 bg-black/50 border border-gray-700 focus:ring-neural-cyan focus:border-neural-cyan rounded-xl text-gray-300 placeholder-gray-600 focus:outline-none focus:ring-1 transition-colors font-mono text-sm"
                />
              </div>
              <div className="relative w-full sm:w-48">
                <label htmlFor="admin-password" className="block text-[10px] text-gray-500 font-mono uppercase mb-1 font-bold tracking-wider">Password</label>
                <input
                   id="admin-password"
                   name="password"
                   type="password"
                   autoComplete="current-password"
                   placeholder="Connection secret..."
                   value={adminPasswordInput}
                   onChange={(e) => setAdminPasswordInput(e.target.value)}
                   className="block w-full px-3 py-2 bg-black/50 border border-gray-700 focus:ring-neural-cyan focus:border-neural-cyan rounded-xl text-gray-300 placeholder-gray-600 focus:outline-none focus:ring-1 transition-colors font-mono text-sm"
                />
              </div>
              <button
                type="submit"
                disabled={isVerifyingAdmin}
                className={`w-full sm:w-auto px-5 py-2 bg-neural-cyan/10 border border-neural-cyan/30 text-neural-cyan hover:bg-neural-cyan/20 hover:border-neural-cyan/50 font-bold rounded-xl transition-all text-sm h-[38px] shrink-0 font-mono flex items-center justify-center gap-1.5`}
              >
                {isVerifyingAdmin ? 'Unlocking...' : 'Unlock'}
                {isAdmin && <Shield className="text-green-400" size={15} />}
              </button>
            </form>
            {adminError && <p className="text-xs text-red-400 font-mono">{adminError}</p>}
            {isAdmin && <p className="text-xs text-green-400 font-mono">Access granted. Full subscription details unlocked.</p>}
          </div>
        </div>
      </section>

      {/* Control Bar: Search and Sort */}
      <section className="py-6 border-b border-gray-800 bg-neural-card/30 relative z-20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex flex-col sm:flex-row justify-between items-center gap-4">

                {/* Search form with Verify submit button */}
                <form onSubmit={handleSearchSubmit} className="flex w-full sm:w-auto gap-2">
                    <div className="relative w-full sm:w-80">
                        <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                            <Search className="text-gray-500" size={18} />
                        </div>
                        <input
                            type="text"
                            placeholder="Search/Verify by User ID..."
                            value={searchInput}
                            onChange={(e) => setSearchInput(e.target.value)}
                            className="block w-full pl-10 pr-3 py-2.5 bg-black/50 border border-gray-700 rounded-xl text-gray-300 placeholder-gray-500 focus:outline-none focus:ring-1 focus:ring-neural-cyan focus:border-neural-cyan transition-colors font-mono text-sm"
                        />
                    </div>
                    <button
                      type="submit"
                      className="px-5 py-2.5 bg-neural-cyan text-black font-extrabold rounded-xl hover:bg-cyan-400 hover:scale-[1.02] active:scale-[0.98] transition-all font-mono text-sm flex items-center gap-1.5 shrink-0 shadow-lg shadow-neural-cyan/20"
                    >
                      Verify
                    </button>
                </form>

                {/* Sort dropdown */}
                <div className="flex items-center gap-3 w-full sm:w-auto justify-end">
                    <SlidersHorizontal className="text-gray-500 hidden sm:block" size={18} />
                    <select
                        value={initialSort}
                        onChange={(e) => handleSortChange(e.target.value as 'latest' | 'oldest')}
                        className="block w-full sm:w-48 pl-3 pr-8 py-2.5 bg-black/50 border border-gray-700 rounded-xl text-gray-300 focus:outline-none focus:ring-1 focus:ring-neural-cyan focus:border-neural-cyan transition-colors text-sm appearance-none cursor-pointer font-mono"
                    >
                        <option value="latest">Latest First</option>
                        <option value="oldest">Oldest First</option>
                    </select>
                </div>
            </div>
        </div>
      </section>

      {/* Members Grid */}
      <section className="py-16 relative z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">

          {initialMembers.length === 0 ? (
            <div className="text-center py-20 bg-neural-card/30 border border-gray-800 rounded-2xl">
              <Shield className="mx-auto text-gray-600 mb-4 animate-bounce" size={48} />
              <h3 className="text-xl font-bold text-gray-400 font-mono">No active members found</h3>
              <p className="text-gray-500 mt-2 font-mono text-sm max-w-md mx-auto">
                {initialSearch ? `No exact matches found for User ID "${initialSearch}". Make sure it is correct.` : 'No active member directories loaded.'}
              </p>
            </div>
          ) : (
            <>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                {members.map((member, index) => (
                  <MemberCard
                     key={`${member.userId}-${index}`}
                     member={{...member, ...sensitiveData[member.userId]}}
                     searchQuery={initialSearch}
                     isAdmin={isAdmin}
                     onGenerateCard={() => {
                       setSelectedUserIdForCard(member.userId);
                       setIsShareModalOpen(true);
                     }}
                  />
                ))}
              </div>

              {/* Pagination */}
              {totalPages > 1 && (
                <div className="mt-12 flex justify-center items-center gap-4">
                  <button
                    onClick={() => handlePageChange(initialPage - 1)}
                    disabled={initialPage <= 1}
                    className={`p-2 rounded-xl border transition-all ${
                        initialPage > 1
                        ? 'bg-neural-card border-gray-700 hover:border-neural-cyan hover:text-neural-cyan hover:scale-105'
                        : 'bg-neural-card/30 border-gray-800 text-gray-600 cursor-not-allowed'
                    }`}
                  >
                    <ChevronLeft size={20} />
                  </button>

                  <span className="font-mono text-gray-400 text-sm">
                    Page <span className="text-white font-bold">{initialPage}</span> of {totalPages}
                  </span>

                  <button
                    onClick={() => handlePageChange(initialPage + 1)}
                    disabled={initialPage >= totalPages}
                    className={`p-2 rounded-xl border transition-all ${
                        initialPage < totalPages
                        ? 'bg-neural-card border-gray-700 hover:border-neural-cyan hover:text-neural-cyan hover:scale-105'
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
        onClose={() => {
            setIsShareModalOpen(false);
            setSelectedUserIdForCard(null);
        }}
        preVerifiedMember={
          selectedUserIdForCard 
            ? {
                ...(initialMembers.find(m => m.userId === selectedUserIdForCard) as any),
                ...(sensitiveData[selectedUserIdForCard] || {})
              }
            : null
        }
      />
    </>
  );
}

function MemberCard({ member, searchQuery, isAdmin, onGenerateCard }: { member: ProMember, searchQuery: string, isAdmin: boolean, onGenerateCard: () => void }) {
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
     displayId = `${start}••••${end}`;
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

  // 1. Determine active subscription state (prioritize paid, ignore trial on paid users)
  let displayStatus = 'STANDARD';
  
  if (member.status === 'ELITE' || member.status === 'V') {
    if (member.expiryDate) {
      const parts = member.expiryDate.split('-');
      if (parts.length === 2) {
        const expiryUnix = parseInt(parts[0], 10);
        if (!isNaN(expiryUnix) && expiryUnix > Date.now()) {
          displayStatus = 'ELITE';
        }
      }
    } else {
      // Public view loads with server-computed classification
      displayStatus = 'ELITE';
    }
  }

  // If not active paid, check trial plan
  if (displayStatus === 'STANDARD' && (member.status === 'TRIAL' || member.trial_plan)) {
    const hasPaidPlan = !!member.expiryDate;
    if (!hasPaidPlan) {
      if (member.trial_plan) {
        const parts = member.trial_plan.split('-');
        if (parts.length === 2) {
          const trialExpiryUnix = parseInt(parts[0], 10);
          if (!isNaN(trialExpiryUnix) && trialExpiryUnix > Date.now()) {
            displayStatus = 'TRIAL';
          }
        }
      } else {
        // Public view loads with server-computed classification
        displayStatus = 'TRIAL';
      }
    }
  }

  // If pending activation (Vercel D1 check fallback for admin)
  if (member.status === 'P') {
     displayStatus = 'PENDING';
  }

  let statusText = 'STANDARD';
  let statusColor = 'text-cyan-400 bg-cyan-950/40 border-cyan-800/40';
  let dotColor = 'bg-cyan-400 shadow-[0_0_8px_#00e5ff]';
  let cardBorder = showDetails ? 'border-cyan-500/50 shadow-[0_0_20px_rgba(0,229,255,0.15)]' : 'border-gray-800 hover:border-cyan-500/30';
  let cardBg = 'from-[#0A0E1A] to-[#05060B]';
  let statusIcon = <Shield className="text-cyan-400 shrink-0" size={14} />;
  let showSubDetails = false;

  if (displayStatus === 'ELITE') {
    statusText = 'ELITE PRO';
    statusColor = 'text-yellow-400 bg-yellow-950/40 border-yellow-800/40';
    dotColor = 'bg-yellow-400 shadow-[0_0_8px_#fbbf24]';
    cardBorder = showDetails ? 'border-yellow-500/50 shadow-[0_0_25px_rgba(234,179,8,0.2)]' : 'border-gray-800 hover:border-yellow-500/30';
    cardBg = 'from-[#1A1305] to-[#0A0702]';
    statusIcon = <Crown className="text-yellow-400 shrink-0" size={14} />;
    showSubDetails = !!member.expiryDate;
  } else if (displayStatus === 'PENDING') {
    statusText = 'PENDING';
    statusColor = 'text-amber-500 bg-amber-950/40 border-amber-800/40';
    dotColor = 'bg-amber-500 shadow-[0_0_8px_#f59e0b]';
    cardBorder = showDetails ? 'border-amber-500/50 shadow-[0_0_20px_rgba(245,158,11,0.15)]' : 'border-gray-800 hover:border-amber-500/30';
    cardBg = 'from-[#1A1005] to-[#0A0602]';
    statusIcon = <Clock className="text-amber-500 shrink-0" size={14} />;
  } else if (displayStatus === 'TRIAL') {
    statusText = 'TRIAL';
    statusColor = 'text-purple-400 bg-purple-950/40 border-purple-800/40';
    dotColor = 'bg-purple-400 shadow-[0_0_8px_#c084fc]';
    cardBorder = showDetails ? 'border-purple-500/50 shadow-[0_0_20px_rgba(192,132,252,0.15)]' : 'border-gray-800 hover:border-purple-500/30';
    cardBg = 'from-[#130A1A] to-[#07020A]';
    statusIcon = <Sparkles className="text-purple-400 shrink-0" size={14} />;
    showSubDetails = !!member.trial_plan;
    
    if (member.trial_plan) {
      const parts = member.trial_plan.split('-');
      if (parts.length >= 2) {
        const expiryUnix = parseInt(parts[0], 10);
        const days = parseInt(parts[1], 10);
        if (!isNaN(expiryUnix) && !isNaN(days)) {
           subMonths = `${days} day${days > 1 ? 's' : ''}`;
           const endD = new Date(expiryUnix);
           subEndDate = endD.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
           const startD = new Date(expiryUnix - (days * 24 * 60 * 60 * 1000));
           subStartDate = startD.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
        }
      }
    }
  }

  return (
    <div className={`group relative bg-gradient-to-b ${cardBg} border ${cardBorder} p-5 rounded-2xl transition-all duration-500 shadow-xl overflow-hidden backdrop-blur-md hover:scale-[1.03] hover:-translate-y-1.5 flex flex-col justify-between`}>

      {/* Glossy overlay sheen animation */}
      <div className="absolute inset-0 w-[200%] translate-x-[-100%] group-hover:translate-x-[100%] bg-gradient-to-r from-transparent via-white/5 to-transparent transition-all duration-1000 ease-in-out pointer-events-none z-10" />

      {/* Floating Sparkles for Elite / Trial / Pending members */}
      {(displayStatus === 'ELITE' || displayStatus === 'PENDING' || displayStatus === 'TRIAL') && (
        <div className="absolute top-0 right-0 p-4 opacity-10 group-hover:opacity-100 transition-opacity duration-700 pointer-events-none">
          <Sparkles className={displayStatus === 'ELITE' ? 'text-yellow-500' : displayStatus === 'PENDING' ? 'text-amber-500' : 'text-purple-500'} size={32} />
        </div>
      )}

      {/* Header section: ID, Status */}
      <div className="flex items-start justify-between gap-2 mb-4 relative z-10">
        <div className="min-w-0 flex-1">
          <div className="font-mono text-white text-lg tracking-tight truncate font-bold" title={member.userId}>
            {displayId}
          </div>
          <div className="text-gray-500 text-xs font-mono uppercase tracking-widest mt-0.5">
            MEMBER ID
          </div>
        </div>
        <div className={`inline-flex items-center gap-1.5 px-2 py-1 rounded-md border text-[10px] font-bold tracking-wider uppercase font-mono ${statusColor} shrink-0`}>
           <span className={`w-1.5 h-1.5 rounded-full ${dotColor} animate-pulse`} />
           {statusText}
        </div>
      </div>

      {/* Main Details Body */}
      <div className="space-y-3 relative z-10 pt-4 border-t border-gray-900/60">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-gray-500 text-xs font-mono font-medium uppercase tracking-wider">
            <Calendar size={13} className="text-gray-600" />
            <span>Registration</span>
          </div>
          <div className="text-gray-300 text-sm font-semibold font-mono">
            {displayDate}
          </div>
        </div>

        {showSubDetails && showDetails && (
          <div className="pt-3 mt-3 border-t border-gray-900/60 space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-gray-500 text-xs font-mono font-medium uppercase tracking-wider">
                <CreditCard size={13} className="text-gray-600" />
                <span>Started</span>
              </div>
              <div className="text-gray-300 text-sm font-semibold font-mono">
                {subStartDate}
              </div>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-gray-500 text-xs font-mono font-medium uppercase tracking-wider">
                <Clock size={13} className="text-gray-600" />
                <span>Duration</span>
              </div>
              <div className="text-gray-300 text-sm font-semibold font-mono">
                {subMonths}
              </div>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-gray-500 text-xs font-mono font-medium uppercase tracking-wider">
                <ShieldCheck size={13} className="text-gray-600" />
                <span>Valid Until</span>
              </div>
              <div className={`${member.trial_plan && !member.status ? 'text-purple-400' : 'text-yellow-400'} text-sm font-bold font-mono`}>
                {subEndDate}
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Generate Card Button (Only for verified exact matches or admin) */}
      {showDetails && (
        <div className="mt-5 relative z-10 pt-4 border-t border-gray-900/60">
          <button
            onClick={onGenerateCard}
            className="w-full flex items-center justify-center gap-2 py-2.5 bg-neural-cyan/10 border border-neural-cyan/30 text-neural-cyan hover:bg-neural-cyan hover:text-black font-bold font-mono text-sm rounded-xl transition-all"
          >
            <Share2 size={16} />
            Generate Member Card
          </button>
        </div>
      )}
    </div>
  );
}
