"use client";

import React, { useState, useRef, useEffect } from 'react';
import { X, Upload, Download, Share2, Crown, User, ShieldCheck, Loader2 } from 'lucide-react';
import { toPng, toBlob } from 'html-to-image';
import { QRCodeSVG as QRCode } from 'qrcode.react';

interface ProMember {
  userId: string;
  dateJoined: string;
  hasAccess: boolean;
}

interface ShareCertificateModalProps {
  isOpen: boolean;
  onClose: () => void;
  members: ProMember[];
}

export default function ShareCertificateModal({ isOpen, onClose, members }: ShareCertificateModalProps) {
  const [step, setStep] = useState(-1);
  const [isPro, setIsPro] = useState<boolean | null>(null);
  const [userId, setUserId] = useState('');
  const [verifyError, setVerifyError] = useState('');
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
      toPng(cardRef.current, { cacheBust: true, pixelRatio: 2 })
        .then(() => {
          if (cardRef.current) {
            return toPng(cardRef.current, { cacheBust: true, pixelRatio: 2 });
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

  const handleVerify = () => {
    if (!userId.trim()) {
      setVerifyError('Please enter your User ID');
      return;
    }
    const found = members.find(m => m.userId.toLowerCase() === userId.trim().toLowerCase());
    if (found) {
      setVerifyError('');
      setVerifiedMember(found);
      setStep(2);
    } else {
      setVerifyError('User ID not found. Please check and try again.');
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
      const dataUrl = cardImage || await toPng(cardRef.current!, { cacheBust: true, pixelRatio: 2 });
      const link = document.createElement('a');
      link.download = `reality-certificate-${userName || 'member'}.png`;
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
         blob = await toBlob(cardRef.current!, { cacheBust: true, pixelRatio: 2 });
      }
      if (blob && navigator.share) {
        const file = new File([blob], 'reality-certificate.png', { type: 'image/png' });
        await navigator.share({
          title: 'My Reality App Certificate',
          text: isPro ? 'I am a Reality Elite Member!' : 'I use the Reality app and it is really good!',
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

              <div>
                <input
                  type="text"
                  placeholder="Enter User ID..."
                  value={userId}
                  onChange={(e) => setUserId(e.target.value)}
                  className="w-full px-4 py-3 bg-black/50 border border-gray-700 rounded-xl text-white focus:outline-none focus:border-neural-cyan font-mono"
                />
                {verifyError && <p className="text-red-500 text-sm mt-2">{verifyError}</p>}
              </div>

              <div className="flex gap-4">
                <button
                  onClick={() => setStep(0)}
                  className="px-4 py-2 text-gray-400 hover:text-white transition-colors"
                >
                  Back
                </button>
                <button
                  onClick={handleVerify}
                  className="flex-1 px-4 py-3 bg-neural-cyan text-black font-bold rounded-xl hover:bg-cyan-400 transition-all"
                >
                  Verify Status
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
                <p className="text-sm text-yellow-200 font-medium">
                  🎁 <strong>Reward:</strong> Share this card on social media! If it gets good reach, send a screenshot to the developer on Telegram or WhatsApp <span className="text-white font-mono">@pawanwashudev</span>. You may win a 1-month to 1-year free Pro subscription and early access to unreleased versions of the app!
                </p>
              </div>

              {/* Image Output Container */}
              <div className="w-full max-w-[500px] aspect-[1.586/1] rounded-2xl relative overflow-hidden shadow-2xl flex items-center justify-center bg-black/50 border border-gray-800">
                {isGeneratingCard ? (
                  <div className="flex flex-col items-center gap-3 text-gray-400">
                    <Loader2 className="animate-spin text-neural-cyan" size={32} />
                    <span className="text-sm font-mono">Generating Card...</span>
                  </div>
                ) : cardImage ? (
                  /* eslint-disable-next-line @next/next/no-img-element */
                  <img src={cardImage} alt="Member Card" className="w-full h-full object-cover" />
                ) : (
                  <div className="text-red-400 text-sm">Failed to generate image.</div>
                )}
              </div>

              {/* Hidden HTML Template for Generation */}
              <div style={{ position: 'absolute', left: '-9999px', top: 0, overflow: 'hidden' }}>
              <div
                ref={cardRef}
                className={`w-[800px] h-[504px] rounded-2xl relative overflow-hidden flex flex-col shadow-2xl ${
                  isPro
                  ? 'bg-gradient-to-br from-[#1a1500] via-[#0a0a0a] to-[#05050A] border-[0.5px] border-yellow-900/50 shadow-[0_20px_50px_rgba(234,179,8,0.2)]'
                  : 'bg-gradient-to-br from-[#001a1f] via-[#0a0a0a] to-[#05050A] border-[0.5px] border-cyan-900/50'
                }`}
                style={{ backgroundColor: '#05050A' }}
              >

                {/* Member Card Background Pattern */}
                <div className={`absolute inset-0 opacity-[0.04] z-0 ${isPro ? 'bg-[url("https://www.transparenttextures.com/patterns/carbon-fibre.png")]' : ''}`} style={!isPro ? { backgroundImage: 'radial-gradient(#ffffff 1px, transparent 1px)', backgroundSize: '20px 20px' } : {}}></div>
                <div className={`absolute inset-0 opacity-30 z-0 bg-[radial-gradient(ellipse_at_top_left,_var(--tw-gradient-stops))] ${isPro ? 'from-yellow-500/20' : 'from-cyan-500/20'} to-transparent`}></div>

                {/* 3D Inner Shadow overlay for physical feel (mostly on Pro) */}
                {isPro && (
                  <div className="absolute inset-0 rounded-2xl border-[1px] border-yellow-500/20 shadow-[inset_0_0_20px_rgba(255,255,255,0.05)] pointer-events-none z-20"></div>
                )}

                {/* Member Card Content */}
                <div className="relative z-10 flex flex-col h-full p-4 sm:p-6 justify-between">

                  {/* Header Row */}
                  <div className="flex justify-between items-start mb-2">
                    <div className="flex flex-col">
                       {/* Enhanced Membership status tag */}
                       {isPro ? (
                         <div className="flex items-center gap-2 mb-1">
                           <span className="px-2 py-0.5 bg-yellow-500/20 rounded text-[9px] sm:text-[10px] font-bold text-yellow-400 border border-yellow-500/30 uppercase tracking-widest shadow-[0_0_10px_rgba(234,179,8,0.2)] flex items-center gap-1">
                             <Crown size={10} /> PRO MEMBERSHIP
                           </span>
                         </div>
                       ) : (
                         <div className="flex items-center gap-2 mb-1">
                           <span className="px-2 py-0.5 bg-white/10 rounded text-[9px] sm:text-[10px] font-mono text-gray-300 border border-white/20 uppercase tracking-widest flex items-center gap-1">
                             <ShieldCheck size={10} /> STANDARD MEMBERSHIP
                           </span>
                         </div>
                       )}
                    </div>

                    <div className="h-8 sm:h-10">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img src="/Realitylogo.png" alt="Reality Logo" className="h-full object-contain" />
                    </div>
                  </div>

                  {/* Middle Row: Photo, Details, and QR */}
                  <div className="flex items-center gap-3 sm:gap-4 flex-1 min-h-0">
                    {/* Photo */}
                    <div className={`w-16 h-16 sm:w-24 sm:h-24 rounded-xl flex items-center justify-center border-2 overflow-hidden shrink-0 relative ${
                        isPro
                        ? 'border-yellow-500/50 bg-black/80 shadow-[0_0_20px_rgba(0,0,0,0.8)]'
                        : 'border-neural-cyan/30 bg-black/50 shadow-lg'
                    }`}>
                      {userPhoto ? (
                        /* eslint-disable-next-line @next/next/no-img-element */
                        <img src={userPhoto} alt="User" className="w-full h-full object-cover" />
                      ) : (
                        <User size={32} className={`sm:w-10 sm:h-10 ${isPro ? "text-yellow-500/50" : "text-gray-600"}`} />
                      )}
                      {/* Subtle glare overlay on photo */}
                      {isPro && <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/5 to-transparent pointer-events-none"></div>}
                    </div>

                    {/* Details */}
                    <div className="flex flex-col min-w-0 flex-1 justify-center">
                      <p className="text-[8px] sm:text-[9px] text-gray-500 font-mono uppercase tracking-widest mb-0.5 truncate">Cardholder</p>
                      <h4 className={`text-lg sm:text-xl font-bold mb-1.5 sm:mb-2 font-outfit uppercase tracking-wide truncate ${isPro ? 'text-yellow-50' : 'text-white'}`}>
                        {userName || (isPro ? "VERIFIED PRO" : "STANDARD USER")}
                      </h4>

                      {isPro ? (
                        <div className="grid grid-cols-2 gap-x-2 gap-y-1">
                           <div className="min-w-0">
                             <p className="text-[7px] sm:text-[8px] text-yellow-500/60 font-mono uppercase tracking-widest truncate">User ID</p>
                             <p className="text-[8px] sm:text-[10px] text-yellow-100 font-mono truncate bg-black/40 px-1 rounded inline-block border border-yellow-500/20 max-w-full">{verifiedMember?.userId || userId || "PENDING"}</p>
                           </div>
                           <div className="min-w-0">
                             <p className="text-[7px] sm:text-[8px] text-yellow-500/60 font-mono uppercase tracking-widest truncate">Member Since</p>
                             <p className="text-[8px] sm:text-[10px] text-yellow-100 font-mono truncate max-w-full">{verifiedMember?.dateJoined ? new Date(verifiedMember.dateJoined).toLocaleDateString() : 'N/A'}</p>
                           </div>
                           <div className="col-span-2 mt-1 min-w-0">
                              <span className="inline-flex items-center gap-1 text-[8px] sm:text-[9px] font-bold text-yellow-400 uppercase tracking-widest bg-yellow-500/10 px-1.5 py-0.5 rounded border border-yellow-500/20 max-w-full truncate">
                                <ShieldCheck size={10} className="shrink-0" /> <span className="truncate">Verified Elite Neubofy Member</span>
                              </span>
                           </div>
                        </div>
                      ) : (
                        <div className="min-w-0">
                          <p className="text-[7px] sm:text-[8px] text-gray-500 font-mono uppercase tracking-widest mb-0.5 truncate">Status</p>
                          <span className="inline-flex items-center gap-1 text-[8px] sm:text-[10px] font-medium text-gray-400 uppercase tracking-widest bg-gray-800/50 px-2 py-0.5 rounded border border-gray-700 max-w-full truncate">
                             Unverified User
                          </span>
                        </div>
                      )}
                    </div>

                    {/* QR Code */}
                    <div className="w-14 h-14 sm:w-20 sm:h-20 shrink-0 bg-white p-1 rounded-lg sm:rounded-xl shadow-lg border border-gray-200">
                      <QRCode
                        value="https://reality.neubofy.in"
                        size={256}
                        style={{ height: "100%", width: "100%", objectFit: "contain" }}
                        level="H"
                        marginSize={0}
                      />
                    </div>
                  </div>

                  {/* Footer Row */}
                  <div className={`mt-2 pt-2 sm:pt-3 border-t flex justify-between items-end ${isPro ? 'border-yellow-500/20' : 'border-white/10'}`}>
                     <div className="flex items-end gap-2 sm:gap-3">
                        <div className="h-6 sm:h-8 mb-0.5">
                          {/* eslint-disable-next-line @next/next/no-img-element */}
                          <img src="/neubofylogo.png" alt="Neubofy Logo" className="h-full object-contain" />
                        </div>
                        <div className="flex flex-col pb-0.5 sm:pb-1">
                          {/* Simulated signature */}
                          <div className={`text-lg sm:text-xl -mb-1 opacity-90 ${isPro ? 'text-yellow-200' : 'text-gray-300'}`} style={{ fontFamily: 'Brush Script MT, cursive, serif' }}>Pawan Washudev</div>
                          <p className={`text-[6px] sm:text-[7px] font-mono uppercase tracking-widest ${isPro ? 'text-yellow-500/60' : 'text-gray-600'}`}>Founder, Neubofy</p>
                        </div>
                     </div>
                     <div className="text-right">
                        <p className={`text-[5px] sm:text-[6px] max-w-[100px] sm:max-w-[150px] font-mono uppercase tracking-widest ${isPro ? 'text-yellow-500/60' : 'text-gray-600'} text-right`}>
                          {isPro ? "Official Elite Pro Membership Identity Card" : "Official Standard User Identity Card"}
                        </p>
                     </div>
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
