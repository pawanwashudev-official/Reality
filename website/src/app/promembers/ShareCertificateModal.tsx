"use client";

import React, { useState, useRef, useEffect } from 'react';
import { X, Upload, Download, Share2, Crown, User, ShieldCheck, Loader2, Calendar, Clock, Eye, EyeOff } from 'lucide-react';
import { toPng, toBlob } from 'html-to-image';
import { verifyMemberId } from './actions';

interface VerifiedMember {
  userId: string;
  dateJoined: string;
  hasAccess: boolean;
  status?: string | null;
  expiryDate?: string | null;
  trial_plan?: string | null;
}

interface ShareCertificateModalProps {
  isOpen: boolean;
  onClose: () => void;
  preVerifiedMember?: VerifiedMember | null;
}

// Parse expiryDate string (format: "unixMs-months") to useful dates
function parseExpiryDate(expiryDate: string | null | undefined): {
  startDate: string;
  endDate: string;
  months: number;
} {
  if (!expiryDate) return { startDate: 'N/A', endDate: 'N/A', months: 0 };
  const parts = expiryDate.split('-');
  if (parts.length < 2) return { startDate: 'N/A', endDate: 'N/A', months: 0 };

  const expiryUnix = parseInt(parts[0], 10);
  const months = parseInt(parts[1], 10);
  if (isNaN(expiryUnix) || isNaN(months)) return { startDate: 'N/A', endDate: 'N/A', months: 0 };

  const durationMs = Math.floor(365 / 12) * months * 24 * 60 * 60 * 1000;
  const fmt = (d: Date) => d.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });

  return {
    startDate: fmt(new Date(expiryUnix - durationMs)),
    endDate: fmt(new Date(expiryUnix)),
    months,
  };
}

export default function ShareCertificateModal({ isOpen, onClose, preVerifiedMember }: ShareCertificateModalProps) {
  const [step, setStep] = useState(-1);
  const [isPro, setIsPro] = useState<boolean | null>(null);
  const [userId, setUserId] = useState('');
  const [verifyError, setVerifyError] = useState('');
  const [isVerifying, setIsVerifying] = useState(false);
  const [verifiedMember, setVerifiedMember] = useState<VerifiedMember | null>(null);

  const [userName, setUserName] = useState('');
  const [userPhoto, setUserPhoto] = useState<string | null>(null);
  const [showFullUserId, setShowFullUserId] = useState(false);
  const [cardImage, setCardImage] = useState<string | null>(null);
  const [isGeneratingCard, setIsGeneratingCard] = useState(false);

  const cardRef = useRef<HTMLDivElement>(null);
  // Fixed card dimensions — NEVER change these; they determine the output image size
  const CARD_W = 560;

  const checkIsPro = (member: any) => {
    if (!member) return false;
    if (member.expiryDate) {
      const parts = member.expiryDate.split('-');
      if (parts.length === 2) {
        const expiryUnix = parseInt(parts[0], 10);
        if (!isNaN(expiryUnix) && expiryUnix > Date.now()) {
          return true;
        }
      }
      return false;
    }
    if (member.trial_plan) {
      const parts = member.trial_plan.split('-');
      if (parts.length === 2) {
        const trialExpiryUnix = parseInt(parts[0], 10);
        if (!isNaN(trialExpiryUnix) && trialExpiryUnix > Date.now()) {
          return true;
        }
      }
    }
    return false;
  };

  // Jump to customize step if pre-verified member is passed
  useEffect(() => {
    if (isOpen) {
      if (preVerifiedMember) {
        setVerifiedMember(preVerifiedMember);
        setIsPro(checkIsPro(preVerifiedMember));
        setUserId(preVerifiedMember.userId);
        setStep(2);
      } else {
        setStep(-1);
      }
    }
  }, [isOpen, preVerifiedMember]);

  useEffect(() => {
    if (step === 3 && cardRef.current) {
      setIsGeneratingCard(true);
      const el = cardRef.current;
      const opts = {
        cacheBust: false,
        pixelRatio: 2,
        // Lock capture dimensions so the output is always identical regardless of viewport
        width: CARD_W,
        style: { transform: 'none', zoom: '1' },
      };
      // Two-pass: first warms up font/image caches, second does the real capture
      toPng(el, opts)
        .then(() => toPng(el, opts))
        .then((dataUrl) => {
          setCardImage(dataUrl);
          setIsGeneratingCard(false);
        })
        .catch((err) => {
          console.error('Failed to generate card image', err);
          setIsGeneratingCard(false);
        });
    } else if (step !== 3) {
      setCardImage(null);
    }
  }, [step, isPro, verifiedMember, userName, userPhoto, userId, showFullUserId]);

  if (!isOpen) return null;

  const handleProChoice = (choice: boolean) => {
    setIsPro(choice);
    setStep(choice ? 1 : 2);
  };

  const handleVerify = async () => {
    if (!userId.trim()) { setVerifyError('Please enter your User ID'); return; }
    setIsVerifying(true);
    setVerifyError('');
    try {
      const res = await verifyMemberId(userId.trim());
      if (res.success && res.member) {
        setVerifiedMember(res.member as VerifiedMember);
        setIsPro(checkIsPro(res.member));
        setStep(2);
      } else {
        setVerifyError(res.error || 'User ID not found. Please check and try again.');
      }
    } catch (err: any) {
      setVerifyError(err.message || 'An error occurred during verification.');
    } finally {
      setIsVerifying(false);
    }
  };

  const handlePhotoUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => setUserPhoto(reader.result as string);
      reader.readAsDataURL(file);
    }
  };

  const downloadImage = async () => {
    if (!cardImage && !cardRef.current) return;
    try {
      const dataUrl = cardImage || await toPng(cardRef.current!, { cacheBust: false, pixelRatio: 2 });
      const a = document.createElement('a');
      a.download = `reality-membership-${userName || 'member'}.png`;
      a.href = dataUrl;
      a.click();
    } catch (err) { console.error('Failed to download', err); }
  };

  const shareImage = async () => {
    if (!cardImage && !cardRef.current) return;
    try {
      let blob: Blob | null = null;
      if (cardImage) {
        blob = await (await fetch(cardImage)).blob();
      } else {
        blob = await toBlob(cardRef.current!, { cacheBust: false, pixelRatio: 2 });
      }
      if (blob && navigator.share) {
        await navigator.share({
          title: 'My Reality App Membership',
          text: isPro ? 'I am a Reality Elite Pro Member! 🏆' : 'I use the Reality app — best productivity OS!',
          files: [new File([blob], 'reality-membership.png', { type: 'image/png' })],
        });
      } else {
        alert('Sharing not supported on this browser. Please download and share manually.');
      }
    } catch (err) { console.error('Failed to share', err); }
  };

  const resetAndClose = () => {
    setStep(-1); setIsPro(null); setUserId(''); setVerifyError('');
    setVerifiedMember(null); setUserName(''); setUserPhoto(null);
    setShowFullUserId(false);
    onClose();
  };

  // Compute dates for the card
  const memberSince = verifiedMember?.dateJoined
    ? new Date(verifiedMember.dateJoined).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })
    : 'N/A';

  const { startDate: subStart, endDate: subEnd, months: subMonths } = parseExpiryDate(verifiedMember?.expiryDate);

  // Determine display ID
  const rawId = verifiedMember?.userId || userId || '';
  const maskedId = rawId.length > 8 ? `${rawId.substring(0, 4)}••••${rawId.substring(rawId.length - 4)}` : rawId;
  const displayId = showFullUserId ? rawId : maskedId;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
      <div className="bg-neural-bg border border-gray-800 rounded-2xl w-full max-w-xl overflow-hidden shadow-2xl relative flex flex-col max-h-[95vh]">

        {/* Header */}
        <div className="flex justify-between items-center p-4 border-b border-gray-800 bg-neural-card/30 shrink-0">
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Share2 className="text-neural-cyan" size={20} />
            Share Membership Card
          </h2>
          <button onClick={resetAndClose} className="p-2 text-gray-400 hover:text-white transition-colors rounded-lg hover:bg-gray-800">
            <X size={20} />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto">

          {/* STEP -1: Welcome */}
          {step === -1 && (
            <div className="py-4 space-y-6">
              <div className="bg-gradient-to-br from-neural-cyan/10 to-blue-500/5 border border-neural-cyan/20 rounded-2xl p-6 text-left">
                <h3 className="text-xl font-bold text-white mb-3">A Heartfelt Thank You ❤️</h3>
                <p className="text-gray-300 text-sm leading-relaxed mb-3">
                  Your support keeps Reality alive and growing. Because of members like you, we can continue building a deeply private, source-available productivity OS without depending on traditional crowdfunding.
                </p>
                <p className="text-gray-300 text-sm leading-relaxed">
                  With <span className="text-yellow-400 font-semibold">Reality Elite</span>, you unlock true agentic AI, unlimited sleep tracking, cloud backup, and much more — while the core app stays 99.9% open and private.
                </p>
                <div className="mt-5 pt-5 border-t border-gray-800 flex items-center justify-between">
                  <div>
                    <p className="text-white font-bold text-base">Pawan Washudev</p>
                    <p className="text-xs text-gray-400">Founder & Developer, Neubofy</p>
                  </div>
                  <div className="text-right space-y-0.5">
                    <p className="text-xs text-neural-cyan">@pawanwashudev</p>
                    <p className="text-xs text-gray-500">support@neubofy.in</p>
                  </div>
                </div>
              </div>
              <button
                onClick={() => setStep(0)}
                className="w-full px-6 py-4 bg-neural-cyan text-black font-extrabold rounded-xl hover:bg-cyan-400 transition-all"
              >
                Generate My Membership Card
              </button>
            </div>
          )}

          {/* STEP 0: Pro or regular */}
          {step === 0 && (
            <div className="text-center py-8 space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-white mb-2">Are you a Reality Elite Member?</h3>
                <p className="text-sm text-gray-500">This determines which card style we generate for you.</p>
              </div>
              <div className="flex flex-col sm:flex-row gap-4 justify-center">
                <button
                  onClick={() => handleProChoice(true)}
                  className="px-6 py-4 bg-gradient-to-br from-yellow-600/20 to-yellow-500/10 border border-yellow-500/50 rounded-xl text-yellow-400 font-bold hover:bg-yellow-500/20 hover:border-yellow-400 transition-all flex items-center justify-center gap-2"
                >
                  <Crown size={20} />
                  Yes — I am Elite Pro
                </button>
                <button
                  onClick={() => handleProChoice(false)}
                  className="px-6 py-4 bg-neural-card border border-gray-700 rounded-xl text-gray-300 font-bold hover:border-neural-cyan hover:text-neural-cyan transition-all"
                >
                  No — Regular User
                </button>
              </div>
            </div>
          )}

          {/* STEP 1: Verify Pro ID */}
          {step === 1 && (
            <div className="py-4 space-y-5">
              <div className="text-center">
                <h3 className="text-xl font-bold text-white mb-1">Verify Your Membership</h3>
                <p className="text-sm text-gray-400">Enter your 16-character User ID to verify Elite status and pull your membership dates.</p>
                <p className="text-xs text-gray-600 mt-1">Find it in Settings → Profile (top of page)</p>
              </div>
              <div className="flex gap-2">
                <input
                  type="text"
                  placeholder="Enter your 16-char User ID..."
                  value={userId}
                  onChange={(e) => setUserId(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && handleVerify()}
                  className="flex-1 px-4 py-3 bg-black/50 border border-gray-700 rounded-xl text-white focus:outline-none focus:border-neural-cyan font-mono text-sm"
                />
                <button
                  onClick={handleVerify}
                  disabled={isVerifying}
                  className="px-5 py-3 bg-neural-cyan text-black font-extrabold rounded-xl hover:bg-cyan-400 disabled:bg-gray-700 disabled:text-gray-400 transition-all font-mono shrink-0"
                >
                  {isVerifying ? <Loader2 className="animate-spin" size={18} /> : 'Verify'}
                </button>
              </div>
              {verifyError && <p className="text-red-400 text-sm text-center bg-red-950/30 border border-red-800/30 rounded-lg p-2">{verifyError}</p>}
              <button onClick={() => setStep(0)} className="text-gray-500 hover:text-gray-300 text-sm transition-colors">
                ← Back
              </button>
            </div>
          )}

          {/* STEP 2: Customize */}
          {step === 2 && (
            <div className="py-4 space-y-5">
              <div className="text-center">
                <h3 className="text-xl font-bold text-white mb-1">Customize Your Card</h3>
                {verifiedMember && (
                  <p className="text-xs text-green-400 font-mono bg-green-950/30 border border-green-800/30 rounded-lg px-3 py-1.5 inline-block mt-1">
                    ✓ Verified: {verifiedMember.userId}
                  </p>
                )}
              </div>

              <div className="space-y-4">
                {/* Name input */}
                <div>
                  <label className="block text-sm font-semibold text-gray-300 mb-1.5">Your Name <span className="text-gray-600 font-normal">(optional)</span></label>
                  <input
                    type="text"
                    placeholder="e.g. Alex Johnson"
                    value={userName}
                    onChange={(e) => setUserName(e.target.value)}
                    maxLength={24}
                    className="w-full px-4 py-3 bg-black/50 border border-gray-700 rounded-xl text-white focus:outline-none focus:border-neural-cyan transition-colors"
                  />
                </div>

                {/* Photo upload */}
                <div>
                  <label className="block text-sm font-semibold text-gray-300 mb-1.5">Profile Photo <span className="text-gray-600 font-normal">(optional)</span></label>
                  
                  <div className="mb-4 p-3 bg-blue-950/20 border border-blue-900/30 rounded-xl text-left">
                     <p className="text-xs text-blue-300/80 leading-relaxed">
                        You can use your original name and own photo. This is completely client-side. We don't even know who generated the card. We only verify and send details to the website if the User ID is found in our DB, which we collect during subscription. We don't have your name, email, or personal details. The User ID is irreversible, meaning no one can get your email or generate the same User ID from your email.
                     </p>
                  </div>

                  <label className="flex items-center justify-center w-full p-4 border-2 border-dashed border-gray-700 rounded-xl hover:border-neural-cyan hover:bg-neural-cyan/5 transition-all cursor-pointer">
                    <div className="flex items-center gap-3 text-gray-500">
                      <Upload size={20} />
                      <span className="text-sm">{userPhoto ? 'Change photo' : 'Click to upload photo'}</span>
                    </div>
                    <input type="file" accept="image/*" className="hidden" onChange={handlePhotoUpload} />
                  </label>
                  {userPhoto && (
                    <div className="mt-3 flex justify-center">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img src={userPhoto} alt="Preview" className="w-16 h-16 rounded-full object-cover border-2 border-neural-cyan" />
                    </div>
                  )}
                </div>

                {/* Show full User ID toggle */}
                {(verifiedMember || userId) && (
                  <div className="flex items-center justify-between p-4 bg-black/30 border border-gray-800 rounded-xl">
                    <div>
                      <p className="text-sm font-semibold text-gray-300">Show Full User ID on Card</p>
                      <p className="text-xs text-gray-500 mt-0.5">
                        {showFullUserId
                          ? `Showing: ${rawId}`
                          : `Showing: ${maskedId} (masked)`}
                      </p>
                    </div>
                    <button
                      onClick={() => setShowFullUserId(v => !v)}
                      className={`flex items-center gap-2 px-3 py-2 rounded-lg border text-xs font-bold transition-all ${
                        showFullUserId
                          ? 'bg-neural-cyan/10 border-neural-cyan/40 text-neural-cyan'
                          : 'bg-gray-900 border-gray-700 text-gray-400 hover:border-gray-600'
                      }`}
                    >
                      {showFullUserId ? <Eye size={14} /> : <EyeOff size={14} />}
                      {showFullUserId ? 'Visible' : 'Hidden'}
                    </button>
                  </div>
                )}
              </div>

              <div className="flex gap-3 pt-2">
                {!preVerifiedMember && (
                  <button
                    onClick={() => setStep(isPro ? 1 : 0)}
                    className="px-4 py-2.5 text-gray-400 hover:text-white transition-colors text-sm"
                  >
                    ← Back
                  </button>
                )}
                <button
                  onClick={() => setStep(3)}
                  className="flex-1 px-4 py-3 bg-neural-cyan text-black font-extrabold rounded-xl hover:bg-cyan-400 transition-all"
                >
                  Generate Card
                </button>
              </div>
            </div>
          )}

          {/* STEP 3: View & Share */}
          {step === 3 && (
            <div className="py-4 flex flex-col items-center">

              <div className="mb-5 p-4 bg-yellow-500/10 border border-yellow-500/20 rounded-xl text-center w-full">
                <p className="text-sm text-yellow-200 leading-relaxed">
                  🎁 <strong>Reward:</strong> Share on social media and send a screenshot to <span className="font-mono font-bold text-white">@pawanwashudev</span> on Telegram, Instagram, or LinkedIn — win a free Pro extension!
                </p>
              </div>

              {/* Portrait Image Preview — scales proportionally to modal width.    */}
              {/* The PNG is always captured at exactly CARD_W×auto px, so showing  */}
              {/* it at width:100% always preserves the correct aspect ratio.        */}
              <div className="w-full rounded-2xl overflow-hidden shadow-[0_0_60px_rgba(0,0,0,0.9)] bg-black border border-gray-800">
                {isGeneratingCard ? (
                  <div className="flex flex-col items-center gap-3 text-gray-400 py-24">
                    <Loader2 className="animate-spin text-neural-cyan" size={32} />
                    <span className="text-sm font-mono">Rendering card...</span>
                  </div>
                ) : cardImage ? (
                  /* eslint-disable-next-line @next/next/no-img-element */
                  <img
                    src={cardImage}
                    alt="Membership Card"
                    style={{ width: '100%', height: 'auto', display: 'block' }}
                  />
                ) : (
                  <div className="text-red-400 text-sm text-center px-4 py-16">Failed to generate card image. Please try again.</div>
                )}
              </div>

              {/* ═══════════════════════════════════════════════════════════════════ */}
              {/* HIDDEN CARD TEMPLATE — isolated from all viewport/responsive CSS  */}
              {/* position:fixed at -9999px removes it from layout while keeping     */}
              {/* it renderable. All sizing is locked with min/max width.            */}
              {/* ═══════════════════════════════════════════════════════════════════ */}
              <div style={{
                position: 'fixed',
                left: '-9999px',
                top: '-9999px',
                pointerEvents: 'none',
                zIndex: -1,
                /* Prevent any ancestor transform/scale from leaking in */
                transform: 'none',
                zoom: 1,
              }}>
                <div
                  ref={cardRef}
                  style={{
                    /* Lock dimensions absolutely — html-to-image will always capture exactly this */
                    width: `${CARD_W}px`,
                    minWidth: `${CARD_W}px`,
                    maxWidth: `${CARD_W}px`,
                    /* Reset everything that could vary by screen/font settings */
                    fontSize: '16px',
                    lineHeight: 1.5,
                    boxSizing: 'border-box',
                    transform: 'none',
                    zoom: 1,
                    backgroundColor: '#05050A',
                    backgroundImage: isPro
                      ? 'radial-gradient(ellipse at 50% 0%, rgba(234,179,8,0.18) 0%, transparent 55%), radial-gradient(ellipse at 100% 100%, rgba(180,130,0,0.08) 0%, transparent 45%)'
                      : 'radial-gradient(ellipse at 50% 0%, rgba(0,229,255,0.14) 0%, transparent 55%), radial-gradient(ellipse at 100% 100%, rgba(0,150,200,0.06) 0%, transparent 45%)',
                    borderRadius: '20px',
                    border: isPro ? '1px solid rgba(234,179,8,0.25)' : '1px solid rgba(0,229,255,0.2)',
                    display: 'flex',
                    flexDirection: 'column',
                    padding: '28px 28px 24px',
                    gap: '0px',
                    position: 'relative',
                    overflow: 'hidden',
                    fontFamily: 'ui-sans-serif, system-ui, -apple-system, sans-serif',
                  }}
                >
                  {/* Dot matrix background */}
                  <div style={{
                    position: 'absolute', inset: 0, opacity: 0.025, pointerEvents: 'none', zIndex: 0,
                    backgroundImage: 'radial-gradient(circle, #ffffff 1px, transparent 1px)',
                    backgroundSize: '20px 20px',
                  }} />
                  {/* Inner glow border */}
                  <div style={{
                    position: 'absolute', inset: '10px', borderRadius: '14px', pointerEvents: 'none', zIndex: 0,
                    border: isPro ? '0.5px solid rgba(234,179,8,0.08)' : '0.5px solid rgba(0,229,255,0.08)',
                  }} />
                  {/* Top highlight line */}
                  <div style={{
                    position: 'absolute', top: 0, left: '15%', right: '15%', height: '1px', zIndex: 1,
                    background: isPro
                      ? 'linear-gradient(to right, transparent, rgba(234,179,8,0.6), transparent)'
                      : 'linear-gradient(to right, transparent, rgba(0,229,255,0.5), transparent)',
                  }} />

                  {/* ── HEADER ── */}
                  <div style={{ position: 'relative', zIndex: 10, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
                    <div>
                      <div style={{ fontSize: '9px', letterSpacing: '0.2em', color: '#6b7280', textTransform: 'uppercase', fontWeight: 600, marginBottom: '2px' }}>
                        Neubofy · Reality OS
                      </div>
                      <div style={{ fontSize: '13px', fontWeight: 800, letterSpacing: '0.08em', textTransform: 'uppercase', color: isPro ? '#fbbf24' : '#00E5FF' }}>
                        {isPro ? 'Elite Pro Membership' : 'Community Membership'}
                      </div>
                    </div>
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src="/neubofylogo.png" alt="Neubofy" style={{ height: '36px', objectFit: 'contain' }} />
                  </div>

                  {/* ── AVATAR + NAME ── */}
                  <div style={{ position: 'relative', zIndex: 10, display: 'flex', flexDirection: 'column', alignItems: 'center', marginBottom: '14px' }}>
                    {/* Avatar */}
                    <div style={{
                      width: '96px', height: '96px', borderRadius: '50%', overflow: 'hidden',
                      border: isPro ? '3px solid rgba(234,179,8,0.5)' : '3px solid rgba(0,229,255,0.4)',
                      backgroundColor: '#0a0a12',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      boxShadow: isPro ? '0 0 24px rgba(234,179,8,0.15), 0 6px 18px rgba(0,0,0,0.6)' : '0 0 24px rgba(0,229,255,0.12), 0 6px 18px rgba(0,0,0,0.6)',
                      marginBottom: '10px', position: 'relative',
                    }}>
                      {userPhoto ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img src={userPhoto} alt="Member" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                      ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px' }}>
                          <svg width="40" height="40" viewBox="0 0 24 24" fill="none">
                            <circle cx="12" cy="8" r="4" fill={isPro ? 'rgba(234,179,8,0.25)' : 'rgba(0,229,255,0.2)'} />
                            <path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" stroke={isPro ? 'rgba(234,179,8,0.3)' : 'rgba(0,229,255,0.25)'} strokeWidth="1.5" strokeLinecap="round" />
                          </svg>
                          <span style={{ fontSize: '8px', color: '#6b7280', letterSpacing: '0.1em', textTransform: 'uppercase' }}>No photo</span>
                        </div>
                      )}
                    </div>

                    {/* Name */}
                    <div style={{ fontSize: '20px', fontWeight: 900, letterSpacing: '0.04em', textTransform: 'uppercase', color: isPro ? '#fefce8' : '#ffffff', textAlign: 'center', lineHeight: 1.1, marginBottom: '6px', maxWidth: '480px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {userName || (isPro ? 'Elite Pro Member' : 'Community Member')}
                    </div>

                    {/* Status badge */}
                    <div style={{
                      display: 'inline-flex', alignItems: 'center', gap: '5px',
                      padding: '4px 12px', borderRadius: '100px',
                      border: isPro ? '1px solid rgba(234,179,8,0.3)' : '1px solid rgba(0,229,255,0.25)',
                      backgroundColor: isPro ? 'rgba(234,179,8,0.08)' : 'rgba(0,229,255,0.06)',
                    }}>
                      <div style={{ width: '5px', height: '5px', borderRadius: '50%', backgroundColor: isPro ? '#fbbf24' : '#00E5FF' }} />
                      <span style={{ fontSize: '9px', fontWeight: 700, letterSpacing: '0.2em', textTransform: 'uppercase', color: isPro ? '#fbbf24' : '#00E5FF' }}>
                        {isPro 
                          ? (verifiedMember?.expiryDate ? 'Verified Elite Pro' : 'Verified Trial Pro') 
                          : 'Verified Member'}
                      </span>
                    </div>
                  </div>

                  {/* ── SEPARATOR ── */}
                  <div style={{
                    height: '1px', marginBottom: '12px',
                    background: isPro
                      ? 'linear-gradient(to right, transparent, rgba(234,179,8,0.2), transparent)'
                      : 'linear-gradient(to right, transparent, rgba(0,229,255,0.15), transparent)',
                  }} />

                  {/* ── MEMBERSHIP DETAILS ── */}
                  <div style={{ position: 'relative', zIndex: 10, backgroundColor: 'rgba(0,0,0,0.45)', border: '1px solid rgba(255,255,255,0.05)', borderRadius: '14px', padding: '14px 18px', marginBottom: '14px' }}>

                    {/* User ID — single row, show full or masked based on toggle */}
                    {rawId && (
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px', paddingBottom: '10px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                        <span style={{ fontSize: '9px', color: '#9ca3af', letterSpacing: '0.15em', textTransform: 'uppercase', fontWeight: 600 }}>Member ID</span>
                        <span style={{ fontSize: '11px', fontWeight: 700, fontFamily: 'monospace', color: isPro ? '#fef3c7' : '#e0f9ff', backgroundColor: 'rgba(0,0,0,0.4)', padding: '2px 8px', borderRadius: '6px', border: '1px solid rgba(255,255,255,0.08)', letterSpacing: '0.05em' }}>
                          {showFullUserId ? rawId : maskedId}
                        </span>
                      </div>
                    )}

                    {/* Member since */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: isPro && subEnd !== 'N/A' ? '10px' : '0', paddingBottom: isPro && subEnd !== 'N/A' ? '10px' : '0', borderBottom: isPro && subEnd !== 'N/A' ? '1px solid rgba(255,255,255,0.05)' : 'none' }}>
                      <span style={{ fontSize: '9px', color: '#9ca3af', letterSpacing: '0.15em', textTransform: 'uppercase', fontWeight: 600 }}>Member Since</span>
                      <span style={{ fontSize: '11px', fontWeight: 700, color: '#d1d5db' }}>{memberSince}</span>
                    </div>

                    {/* Subscription dates — only for verified Pro with expiryDate */}
                    {isPro && subEnd !== 'N/A' && (
                      <>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px', paddingBottom: '10px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                          <span style={{ fontSize: '9px', color: '#9ca3af', letterSpacing: '0.15em', textTransform: 'uppercase', fontWeight: 600 }}>Valid From</span>
                          <span style={{ fontSize: '11px', fontWeight: 700, color: '#d1d5db' }}>{subStart}</span>
                        </div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px', paddingBottom: '10px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                          <span style={{ fontSize: '9px', color: '#9ca3af', letterSpacing: '0.15em', textTransform: 'uppercase', fontWeight: 600 }}>Valid Until</span>
                          <span style={{ fontSize: '11px', fontWeight: 700, color: '#fbbf24' }}>{subEnd}</span>
                        </div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <span style={{ fontSize: '9px', color: '#9ca3af', letterSpacing: '0.15em', textTransform: 'uppercase', fontWeight: 600 }}>Plan</span>
                          <span style={{ fontSize: '11px', fontWeight: 700, color: '#fbbf24' }}>{subMonths}-Month Elite Pro</span>
                        </div>
                      </>
                    )}

                    {/* For non-pro or pro without dates, show access level */}
                    {(!isPro || subEnd === 'N/A') && (
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontSize: '9px', color: '#9ca3af', letterSpacing: '0.15em', textTransform: 'uppercase', fontWeight: 600 }}>Access Level</span>
                        <span style={{ fontSize: '11px', fontWeight: 700, color: isPro ? '#fbbf24' : '#00E5FF' }}>{isPro ? 'Elite Pro Tier' : 'Community Tier'}</span>
                      </div>
                    )}
                  </div>

                  {/* ── FOOTER ── */}
                  <div style={{ position: 'relative', zIndex: 10, display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: '12px', borderTop: isPro ? '1px solid rgba(234,179,8,0.1)' : '1px solid rgba(0,229,255,0.08)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img src="/Realitylogo.png" alt="Reality" style={{ height: '24px', objectFit: 'contain' }} />
                      <div>
                        <div style={{ fontSize: '7px', color: isPro ? 'rgba(251,191,36,0.5)' : 'rgba(0,229,255,0.4)', letterSpacing: '0.12em', textTransform: 'uppercase', fontWeight: 600 }}>Issued by Neubofy</div>
                        <div style={{ fontSize: '9px', color: isPro ? 'rgba(251,191,36,0.7)' : 'rgba(180,180,180,0.6)', fontWeight: 600 }}>reality.neubofy.in</div>
                      </div>
                    </div>
                    {/* Signature */}
                    <div style={{ textAlign: 'right' }}>
                      <div style={{ fontSize: '17px', fontFamily: 'Brush Script MT, cursive, serif', color: isPro ? 'rgba(251,191,36,0.8)' : 'rgba(200,200,200,0.65)', lineHeight: 1 }}>
                        Pawan Washudev
                      </div>
                      <div style={{ fontSize: '7px', color: '#6b7280', letterSpacing: '0.12em', textTransform: 'uppercase', marginTop: '2px' }}>Founder, Neubofy</div>
                    </div>
                  </div>

                  {/* Bottom accent line */}
                  <div style={{
                    position: 'absolute', bottom: 0, left: '15%', right: '15%', height: '1px', zIndex: 1,
                    background: isPro
                      ? 'linear-gradient(to right, transparent, rgba(234,179,8,0.4), transparent)'
                      : 'linear-gradient(to right, transparent, rgba(0,229,255,0.3), transparent)',
                  }} />
                </div>
              </div>
              {/* ═══════════════════════════════════════════════════ */}

              {/* Action Buttons */}
              <div className="flex gap-3 w-full mt-6">
                <button
                  onClick={() => setStep(2)}
                  className="px-4 py-2.5 text-gray-500 hover:text-gray-300 transition-colors text-sm font-medium"
                >
                  ← Edit
                </button>
                <button
                  onClick={downloadImage}
                  disabled={isGeneratingCard || !cardImage}
                  className="flex-1 px-4 py-3 bg-neural-card border border-gray-700 text-white font-bold rounded-xl hover:border-neural-cyan hover:text-neural-cyan transition-all flex items-center justify-center gap-2 text-sm disabled:opacity-40"
                >
                  <Download size={17} />
                  Save Image
                </button>
                <button
                  onClick={shareImage}
                  disabled={isGeneratingCard || !cardImage}
                  className="flex-1 px-4 py-3 bg-neural-cyan text-black font-extrabold rounded-xl hover:bg-cyan-400 transition-all flex items-center justify-center gap-2 text-sm disabled:opacity-40"
                >
                  <Share2 size={17} />
                  Share
                </button>
              </div>
            </div>
          )}

        </div>
      </div>
    </div>
  );
}
