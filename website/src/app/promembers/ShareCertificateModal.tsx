"use client";

import React, { useState, useRef, useEffect } from 'react';
import { X, Upload, Download, Share2, Crown, User, ShieldCheck, Loader2, Calendar, Shield, Clock } from 'lucide-react';
import { toPng, toBlob } from 'html-to-image';
import { QRCodeSVG as QRCode } from 'qrcode.react';
import { verifyMemberId } from './actions';

interface ProMember {
  userId: string;
  dateJoined: string;
  hasAccess: boolean;
}

interface ShareCertificateModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function ShareCertificateModal({ isOpen, onClose }: ShareCertificateModalProps) {
  const [step, setStep] = useState(-1);
  const [isPro, setIsPro] = useState<boolean | null>(null);
  const [userId, setUserId] = useState('');
  const [verifyError, setVerifyError] = useState('');
  const [isVerifying, setIsVerifying] = useState(false);
  const [verifiedMember, setVerifiedMember] = useState<ProMember | null>(null);

  const [userName, setUserName] = useState('');
  const [userPhoto, setUserPhoto] = useState<string | null>(null);
  const [cardImage, setCardImage] = useState<string | null>(null);
  const [isGeneratingCard, setIsGeneratingCard] = useState(false);

  const cardRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (step === 3 && cardRef.current) {
      setIsGeneratingCard(true);
      // Generate image twice to ensure fonts/images are fully loaded in canvas
      toPng(cardRef.current, { cacheBust: false, pixelRatio: 2 })
        .then(() => {
          if (cardRef.current) {
            return toPng(cardRef.current, { cacheBust: false, pixelRatio: 2 });
          }
          return null;
        })
        .then((dataUrl) => {
          if (dataUrl) {
            setCardImage(dataUrl);
          }
          setIsGeneratingCard(false);
        })
        .catch((err) => {
          console.error('Failed to pre-generate image', err);
          setIsGeneratingCard(false);
        });
    } else if (step !== 3) {
      setCardImage(null);
    }
  }, [step, isPro, verifiedMember, userName, userPhoto, userId]);

  if (!isOpen) return null;

  const handleProChoice = (choice: boolean) => {
    setIsPro(choice);
    if (choice) {
      setStep(1);
    } else {
      setStep(2);
    }
  };

  const handleVerify = async () => {
    if (!userId.trim()) {
      setVerifyError('Please enter your User ID');
      return;
    }
    setIsVerifying(true);
    setVerifyError('');

    try {
      const res = await verifyMemberId(userId.trim());
      if (res.success && res.member) {
        setVerifyError('');
        setVerifiedMember(res.member);
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
      reader.onloadend = () => {
        setUserPhoto(reader.result as string);
      };
      reader.readAsDataURL(file);
    }
  };

  const downloadImage = async () => {
    if (!cardImage && !cardRef.current) return;
    try {
      const dataUrl = cardImage || await toPng(cardRef.current!, { cacheBust: false, pixelRatio: 2 });
      const link = document.createElement('a');
      link.download = `reality-member-pass-${userName || 'member'}.png`;
      link.href = dataUrl;
      link.click();
    } catch (err) {
      console.error('Failed to generate image', err);
    }
  };

  const shareImage = async () => {
    if (!cardImage && !cardRef.current) return;
    try {
      let blob: Blob | null = null;
      if (cardImage) {
         const res = await fetch(cardImage);
         blob = await res.blob();
      } else {
         blob = await toBlob(cardRef.current!, { cacheBust: false, pixelRatio: 2 });
      }
      if (blob && navigator.share) {
        const file = new File([blob], 'reality-member-pass.png', { type: 'image/png' });
        await navigator.share({
          title: 'My Reality App Membership Pass',
          text: isPro ? 'I am a Reality Elite Pro Member!' : 'I use the Reality app and it is amazing!',
          files: [file]
        });
      } else {
        alert('Web Share API is not supported in your browser or device. You can download the image instead.');
      }
    } catch (err) {
      console.error('Failed to share image', err);
      alert('Could not share. You may need to download it instead.');
    }
  };

  const resetAndClose = () => {
    setStep(-1);
    setIsPro(null);
    setUserId('');
    setVerifyError('');
    setVerifiedMember(null);
    setUserName('');
    setUserPhoto(null);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
      <div className="bg-neural-bg border border-gray-800 rounded-2xl w-full max-w-lg overflow-hidden shadow-2xl relative flex flex-col max-h-[90vh]">

        {/* Header */}
        <div className="flex justify-between items-center p-4 border-b border-gray-800 bg-neural-card/30">
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Share2 className="text-neural-cyan" size={20} />
            Share Member Card
          </h2>
          <button onClick={resetAndClose} className="p-2 text-gray-400 hover:text-white transition-colors">
            <X size={20} />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto">
          {step === -1 && (
            <div className="py-4 space-y-6">
              <div className="bg-gradient-to-r from-neural-cyan/10 to-blue-500/5 border border-neural-cyan/30 rounded-xl p-6 text-left">
                <h3 className="text-xl font-bold text-white mb-4">A Heartfelt Thank You from Neubofy ❤️</h3>
                <p className="text-gray-300 text-sm leading-relaxed mb-4">
                  Thank you for supporting us and motivating us to build such an incredible product. Because normal crowdfunding can&apos;t sustain us and support enough to maintain everything for free, we added a small Pro barrier.
                </p>
                <p className="text-gray-300 text-sm leading-relaxed mb-4">
                  With Reality Elite Member, you get access to advanced features like true agentic AI, unlimited local-first sleep tracking, seamless cloud backups, and much more—all while keeping the core app 99.9% source-available and deeply private.
                </p>
                <div className="mt-6 pt-6 border-t border-gray-800 flex items-center justify-between">
                   <div>
                     <p className="text-white font-bold font-outfit text-lg">Pawan Washudev</p>
                     <p className="text-xs text-gray-400">Founder & Developer, Neubofy</p>
                   </div>
                   <div className="text-right">
                     <p className="text-xs text-neural-cyan">Telegram: @pawanwashudev</p>
                     <p className="text-xs text-neural-cyan">WhatsApp: @pawanwashudev</p>
                     <p className="text-xs text-neural-cyan">Instagram: @pawanwashudev</p>
                     <p className="text-xs text-neural-cyan">LinkedIn: @pawanwashudev</p>
                     <p className="text-xs text-gray-400">Email: support@neubofy.in</p>
                   </div>
                 </div>
              </div>
              <button
                onClick={() => setStep(0)}
                className="w-full px-6 py-4 bg-neural-cyan text-black font-bold rounded-xl hover:bg-cyan-400 transition-all flex items-center justify-center gap-2"
              >
                Generate My Member Card
              </button>
            </div>
          )}

          {step === 0 && (
            <div className="text-center py-8">
              <h3 className="text-2xl font-bold text-white mb-6">Are you a Reality Elite Member?</h3>
              <div className="flex flex-col sm:flex-row gap-4 justify-center">
                <button
                  onClick={() => handleProChoice(true)}
                  className="px-6 py-3 bg-gradient-to-r from-yellow-600/20 to-yellow-500/10 border border-yellow-500/50 rounded-xl text-yellow-500 font-bold hover:bg-yellow-500/20 transition-all flex items-center justify-center gap-2"
                >
                  <Crown size={20} />
                  Yes, I am a Pro Member
                </button>
                <button
                  onClick={() => handleProChoice(false)}
                  className="px-6 py-3 bg-neural-card border border-gray-700 rounded-xl text-white font-bold hover:border-neural-cyan hover:text-neural-cyan transition-all"
                >
                  No, I am a regular user
                </button>
              </div>
            </div>
          )}

          {step === 1 && (
            <div className="py-4 space-y-6">
              <h3 className="text-xl font-bold text-white text-center">Verify Pro Membership</h3>
              <p className="text-sm text-gray-400 text-center">
                Please enter your 16-character User ID to verify your Elite status.
                <br />
                <span className="text-xs text-gray-500 mt-1 block">
                  (You can find it on your profile page in the settings page on top)
                </span>
              </p>

              <div className="flex gap-2">
                <input
                  type="text"
                  placeholder="Enter User ID..."
                  value={userId}
                  onChange={(e) => setUserId(e.target.value)}
                  className="flex-1 px-4 py-3 bg-black/50 border border-gray-700 rounded-xl text-white focus:outline-none focus:border-neural-cyan font-mono"
                />
                <button
                  onClick={handleVerify}
                  disabled={isVerifying}
                  className="px-5 py-3 bg-neural-cyan text-black font-extrabold rounded-xl hover:bg-cyan-400 disabled:bg-gray-700 disabled:text-gray-400 transition-all font-mono"
                >
                  {isVerifying ? 'Verifying...' : 'Verify'}
                </button>
              </div>
              {verifyError && <p className="text-red-500 text-sm mt-2 text-center">{verifyError}</p>}

              <div className="flex gap-4 pt-4 border-t border-gray-800">
                <button
                  onClick={() => setStep(0)}
                  className="w-full px-4 py-2 bg-neural-card border border-gray-700 rounded-xl text-gray-400 hover:text-white transition-colors"
                >
                  Back
                </button>
              </div>
            </div>
          )}

          {step === 2 && (
            <div className="py-4 space-y-6">
              <h3 className="text-xl font-bold text-white text-center">Customize Your Member Card</h3>

              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-400 mb-1">Your Name (Optional)</label>
                  <input
                    type="text"
                    placeholder="Enter your name"
                    value={userName}
                    onChange={(e) => setUserName(e.target.value)}
                    maxLength={22}
                    className="w-full px-4 py-3 bg-black/50 border border-gray-700 rounded-xl text-white focus:outline-none focus:border-neural-cyan"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-400 mb-1">Your Photo (Optional)</label>
                  <label className="flex items-center justify-center w-full p-4 border-2 border-dashed border-gray-700 rounded-xl hover:border-neural-cyan hover:bg-neural-cyan/5 transition-all cursor-pointer">
                    <div className="flex flex-col items-center gap-2 text-gray-500">
                      <Upload size={24} />
                      <span className="text-sm">Click to upload image</span>
                    </div>
                    <input type="file" accept="image/*" className="hidden" onChange={handlePhotoUpload} />
                  </label>
                  {userPhoto && (
                    <div className="mt-4 flex justify-center">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img src={userPhoto} alt="Preview" className="w-20 h-20 rounded-full object-cover border-2 border-neural-cyan" />
                    </div>
                  )}
                </div>
              </div>

              <div className="flex gap-4 pt-4">
                <button
                  onClick={() => setStep(isPro ? 1 : 0)}
                  className="px-4 py-2 text-gray-400 hover:text-white transition-colors"
                >
                  Back
                </button>
                <button
                  onClick={() => setStep(3)}
                  className="flex-1 px-4 py-3 bg-neural-cyan text-black font-bold rounded-xl hover:bg-cyan-400 transition-all"
                >
                  Generate Member Card
                </button>
              </div>
            </div>
          )}

          {step === 3 && (
            <div className="py-4 flex flex-col items-center">

              <div className="mb-6 p-4 bg-yellow-500/10 border border-yellow-500/30 rounded-xl text-center">
                <p className="text-sm text-yellow-200 font-medium leading-relaxed">
                  🎁 <strong>Reward:</strong> Share this card on social media! If it gets good reach, send a screenshot to the developer on Telegram, WhatsApp, Instagram, or LinkedIn <span className="text-white font-mono font-bold">@pawanwashudev</span>. You may win a free Pro subscription extension and early access!
                </p>
              </div>

              {/* Portrait Image Output Container */}
              <div className="w-full max-w-[340px] aspect-[1/1.56] rounded-2xl relative overflow-hidden shadow-[0_0_40px_rgba(0,0,0,0.8)] flex items-center justify-center bg-black/50 border border-gray-800">
                {isGeneratingCard ? (
                  <div className="flex flex-col items-center gap-3 text-gray-400">
                    <Loader2 className="animate-spin text-neural-cyan" size={32} />
                    <span className="text-sm font-mono">Generating VIP Pass...</span>
                  </div>
                ) : cardImage ? (
                  /* eslint-disable-next-line @next/next/no-img-element */
                  <img src={cardImage} alt="Member Card" className="w-full h-full object-cover rounded-2xl" />
                ) : (
                  <div className="text-red-400 text-sm">Failed to generate image.</div>
                )}
              </div>

              {/* Hidden HTML Template for Generation (Redesigned as Portrait Elite VIP Pass - 500x780) */}
              <div style={{ position: 'absolute', left: '-9999px', top: 0, overflow: 'hidden' }}>
              <div
                ref={cardRef}
                className={`w-[500px] h-[780px] rounded-2xl relative overflow-hidden flex flex-col justify-between shadow-2xl p-6 border ${
                  isPro
                  ? 'border-yellow-500/30 shadow-[0_0_50px_rgba(234,179,8,0.25)]'
                  : 'border-cyan-500/30 shadow-[0_0_50px_rgba(0,229,255,0.25)]'
                }`}
                style={{
                  backgroundColor: '#05050A',
                  width: '500px',
                  height: '780px',
                  backgroundImage: isPro
                    ? 'radial-gradient(circle at 50% 20%, rgba(234, 179, 8, 0.12) 0%, transparent 60%), radial-gradient(circle at 0% 100%, rgba(234, 179, 8, 0.05) 0%, transparent 40%)'
                    : 'radial-gradient(circle at 50% 20%, rgba(0, 229, 255, 0.12) 0%, transparent 60%), radial-gradient(circle at 0% 100%, rgba(0, 229, 255, 0.05) 0%, transparent 40%)'
                }}
              >
                {/* Cyber Matrix/Grid Overlay Pattern */}
                <div className="absolute inset-0 opacity-[0.03] z-0 pointer-events-none" style={{ backgroundImage: 'radial-gradient(#ffffff 1.5px, transparent 1.5px)', backgroundSize: '24px 24px' }}></div>
                
                {/* Diagonal Holographic Sheen overlay */}
                <div className="absolute inset-0 z-0 bg-gradient-to-tr from-transparent via-white/[0.02] to-transparent pointer-events-none"></div>

                {/* Inner Border Ring */}
                <div className={`absolute inset-3 rounded-[18px] border-[0.5px] pointer-events-none z-0 ${isPro ? 'border-yellow-500/10' : 'border-cyan-500/10'}`}></div>

                {/* HEADER SECTION */}
                <div className="relative z-10 flex justify-between items-center pb-4 border-b border-gray-900/60">
                  <div className="flex flex-col">
                    <span className="text-[10px] font-mono tracking-[0.25em] text-gray-500 uppercase">REALITY OS</span>
                    <span className={`text-[12px] font-bold tracking-[0.1em] font-mono ${isPro ? 'text-yellow-400' : 'text-cyan-400'}`}>PRODUCTIVITY PASS</span>
                  </div>
                  <div className="h-9">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src="/Realitylogo.png" alt="Reality Logo" className="h-full object-contain" />
                  </div>
                </div>

                {/* BADGE SECTION */}
                <div className="relative z-10 flex flex-col items-center mt-3">
                  {isPro ? (
                    <div className="inline-flex flex-col items-center">
                      <div className="w-12 h-12 rounded-full bg-yellow-500/10 border border-yellow-500/40 flex items-center justify-center shadow-[0_0_15px_rgba(234,179,8,0.2)] mb-1">
                        <Crown className="text-yellow-400" size={24} />
                      </div>
                      <span className="text-[10px] font-mono font-black text-yellow-400 tracking-[0.3em] uppercase">ELITE PRO TIER</span>
                    </div>
                  ) : (
                    <div className="inline-flex flex-col items-center">
                      <div className="w-12 h-12 rounded-full bg-cyan-500/10 border border-cyan-500/40 flex items-center justify-center shadow-[0_0_15px_rgba(0,229,255,0.2)] mb-1">
                        <ShieldCheck className="text-cyan-400" size={24} />
                      </div>
                      <span className="text-[10px] font-mono font-black text-cyan-400 tracking-[0.3em] uppercase">COMMUNITY TIER</span>
                    </div>
                  )}
                </div>

                {/* IDENTITY AVATAR */}
                <div className="relative z-10 flex justify-center my-4">
                  <div className={`w-36 h-36 rounded-2xl flex items-center justify-center border-2 overflow-hidden relative shadow-[0_15px_30px_rgba(0,0,0,0.6)] ${
                      isPro
                      ? 'border-yellow-500/40 bg-black/60 shadow-yellow-500/5'
                      : 'border-cyan-500/40 bg-black/60 shadow-cyan-500/5'
                  }`}>
                    {userPhoto ? (
                      /* eslint-disable-next-line @next/next/no-img-element */
                      <img src={userPhoto} alt="User" className="w-full h-full object-cover" />
                    ) : (
                      <div className="flex flex-col items-center justify-center text-gray-600 gap-1">
                        <User size={56} className={`${isPro ? "text-yellow-500/20" : "text-cyan-500/20"}`} />
                        <span className="text-[9px] font-mono uppercase tracking-wider text-gray-500">Awaiting Pic</span>
                      </div>
                    )}
                    {/* Gloss sheen overlay */}
                    <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/5 to-transparent pointer-events-none"></div>
                  </div>
                </div>

                {/* PROFILE INFORMATION */}
                <div className="relative z-10 text-center px-4">
                  <span className={`text-[9px] font-mono uppercase tracking-[0.2em] ${isPro ? 'text-yellow-500/60' : 'text-cyan-500/60'}`}>Identity Cardholder</span>
                  <h4 className={`text-2xl font-black font-outfit uppercase tracking-wide truncate mt-1 ${isPro ? 'text-yellow-50' : 'text-white'}`}>
                    {userName || (isPro ? "VERIFIED PRO MEMBER" : "STANDARD MEMBER")}
                  </h4>
                  <div className="mt-2.5 flex justify-center">
                    {isPro ? (
                      <span className="inline-flex items-center gap-1 text-[9px] font-bold text-yellow-400 uppercase tracking-widest bg-yellow-500/10 px-2.5 py-1 rounded border border-yellow-500/25">
                        <ShieldCheck size={11} className="shrink-0 animate-pulse" /> Verified Elite Neubofy Member
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 text-[9px] font-mono text-gray-400 uppercase tracking-widest bg-gray-900/60 px-2.5 py-1 rounded border border-gray-800">
                        Unverified Member
                      </span>
                    )}
                  </div>
                </div>

                {/* MEMBERSHIP TECH METADATA */}
                <div className="relative z-10 mt-4 px-2">
                  <div className="bg-black/60 border border-gray-900 rounded-xl p-3.5 space-y-2.5 shadow-inner">
                    <div className="flex items-center justify-between pb-1.5 border-b border-gray-900/60">
                      <span className="text-[8px] font-mono text-gray-500 uppercase tracking-wider flex items-center gap-1">
                        <User size={10} /> User ID Code
                      </span>
                      <span className={`text-[10px] font-mono font-bold ${isPro ? 'text-yellow-100' : 'text-cyan-100'} bg-black px-1.5 py-0.5 rounded border border-gray-800`}>
                        {verifiedMember?.userId || userId || "UNVERIFIED"}
                      </span>
                    </div>

                    <div className="flex items-center justify-between pb-1.5 border-b border-gray-900/60">
                      <span className="text-[8px] font-mono text-gray-500 uppercase tracking-wider flex items-center gap-1">
                        <Calendar size={10} /> Member Since
                      </span>
                      <span className="text-[10px] font-mono font-bold text-gray-300">
                        {verifiedMember?.dateJoined ? new Date(verifiedMember.dateJoined).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' }) : 'N/A'}
                      </span>
                    </div>

                    {isPro && (
                      <div className="flex items-center justify-between">
                        <span className="text-[8px] font-mono text-gray-500 uppercase tracking-wider flex items-center gap-1">
                          <Clock size={10} /> Access Duration
                        </span>
                        <span className="text-[10px] font-mono font-bold text-yellow-400">
                          ELITE LIFETIME ACCESS
                        </span>
                      </div>
                    )}
                  </div>
                </div>

                {/* FOOTER & SIGNATURE & QR ROW */}
                <div className="relative z-10 flex justify-between items-end pt-3 mt-3 border-t border-gray-900/60">
                  <div className="flex items-end gap-3">
                    <div className="h-8 mb-0.5 shrink-0">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img src="/neubofylogo.png" alt="Neubofy Logo" className="h-full object-contain" />
                    </div>
                    <div className="flex flex-col pb-0.5">
                      {/* Simulated signature with cursive brush style */}
                      <div className={`text-[20px] -mb-1 opacity-90 leading-none select-none font-bold ${isPro ? 'text-yellow-300' : 'text-gray-300'}`} style={{ fontFamily: 'Brush Script MT, cursive, serif, sans-serif' }}>
                        Pawan Washudev
                      </div>
                      <p className={`text-[6px] font-mono uppercase tracking-widest leading-none mt-1 ${isPro ? 'text-yellow-500/60' : 'text-gray-600'}`}>Founder, Neubofy</p>
                    </div>
                  </div>

                  {/* QR Code Container */}
                  <div className="w-16 h-16 shrink-0 bg-white p-1 rounded-lg shadow-lg border border-gray-200">
                    <QRCode
                      value="https://reality.neubofy.in"
                      size={128}
                      style={{ height: "100%", width: "100%", objectFit: "contain" }}
                      level="H"
                      marginSize={0}
                    />
                  </div>
                </div>
              </div>
              </div>

              {/* Action Buttons */}
              <div className="flex gap-4 w-full mt-8 relative z-20">
                <button
                  onClick={() => setStep(2)}
                  className="px-4 py-2 text-gray-400 hover:text-white transition-colors"
                >
                  Edit
                </button>
                <button
                  onClick={downloadImage}
                  className="flex-1 px-4 py-3 bg-neural-card border border-gray-700 text-white font-bold rounded-xl hover:border-neural-cyan hover:text-neural-cyan transition-all flex items-center justify-center gap-2 text-sm"
                >
                  <Download size={18} />
                  Save Image
                </button>
                <button
                  onClick={shareImage}
                  className="flex-1 px-4 py-3 bg-neural-cyan text-black font-bold rounded-xl hover:bg-cyan-400 transition-all flex items-center justify-center gap-2 text-sm"
                >
                  <Share2 size={18} />
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
