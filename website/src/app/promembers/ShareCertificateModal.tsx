"use client";

import React, { useState, useRef } from 'react';
import { X, Upload, Download, Share2, Crown, User, ShieldCheck } from 'lucide-react';
import { toPng, toBlob } from 'html-to-image';

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

  const [userName, setUserName] = useState('');
  const [userPhoto, setUserPhoto] = useState<string | null>(null);

  const cardRef = useRef<HTMLDivElement>(null);

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
    if (!cardRef.current) return;
    try {
      const dataUrl = await toPng(cardRef.current, { cacheBust: true, pixelRatio: 2 });
      const link = document.createElement('a');
      link.download = `reality-certificate-${userName || 'member'}.png`;
      link.href = dataUrl;
      link.click();
    } catch (err) {
      console.error('Failed to generate image', err);
    }
  };

  const shareImage = async () => {
    if (!cardRef.current) return;
    try {
      const blob = await toBlob(cardRef.current, { cacheBust: true, pixelRatio: 2 });
      if (blob && navigator.share) {
        const file = new File([blob], 'reality-certificate.png', { type: 'image/png' });
        await navigator.share({
          title: 'My Reality App Certificate',
          text: isPro ? 'I am a Reality Pro member!' : 'I use the Reality app and it is really good!',
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
                  With Reality Pro, you get access to advanced features like true agentic AI, unlimited local-first sleep tracking, seamless cloud backups, and much more—all while keeping the core app 99.9% open-source and deeply private.
                </p>
                <div className="mt-6 pt-6 border-t border-gray-800 flex items-center justify-between">
                   <div>
                     <p className="text-white font-bold font-outfit text-lg">Pawan Washudev</p>
                     <p className="text-xs text-gray-400">Founder & Developer, Neubofy</p>
                   </div>
                   <div className="text-right">
                     <p className="text-xs text-neural-cyan">Telegram: @pawanwashudev</p>
                     <p className="text-xs text-gray-400">Email: founder@neubofy.in</p>
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
              <h3 className="text-2xl font-bold text-white mb-6">Are you a Reality Pro member?</h3>
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
                  🎁 <strong>Reward:</strong> Share this card on social media! If it gets good reach, send a screenshot to the developer on Telegram <span className="text-white font-mono">@pawanwashudev</span>. You may win a 1-month to 1-year free Pro subscription and early access to unreleased versions of the app!
                </p>
              </div>

              {/* Member Card Preview Container */}
              <div
                ref={cardRef}
                className={`w-full max-w-[400px] aspect-[1.6/1] p-1 rounded-2xl relative overflow-hidden flex flex-col ${
                  isPro
                  ? 'bg-gradient-to-br from-yellow-600 via-gray-900 to-black'
                  : 'bg-gradient-to-br from-neural-cyan via-gray-900 to-black'
                }`}
                style={{ backgroundColor: '#05050A' }}
              >
                <div className="absolute inset-0 bg-[#05050A] m-[2px] rounded-2xl z-0"></div>

                {/* Member Card Background Pattern */}
                <div className="absolute inset-0 opacity-[0.03] z-0" style={{ backgroundImage: 'radial-gradient(#ffffff 1px, transparent 1px)', backgroundSize: '20px 20px' }}></div>
                <div className="absolute inset-0 opacity-20 z-0 bg-[radial-gradient(ellipse_at_top_left,_var(--tw-gradient-stops))] from-white to-transparent"></div>

                {/* Member Card Content */}
                <div className="relative z-10 flex flex-col h-full p-6">

                  {/* Header Row */}
                  <div className="flex justify-between items-start mb-4">
                    <div className="flex flex-col">
                      <div className="flex items-center gap-2">
                         <span className="font-outfit font-black text-2xl tracking-widest text-white drop-shadow-lg">NEUBOFY</span>
                         <span className="px-2 py-0.5 bg-white/10 rounded text-[10px] font-mono text-gray-300 border border-white/20">MEMBERSHIP CARD</span>
                      </div>
                      <span className="text-xs text-gray-400 font-mono mt-1">reality.neubofy.in</span>
                    </div>
                    {isPro ? (
                      <div className="w-12 h-12 rounded-lg bg-yellow-500/20 border border-yellow-500/40 flex items-center justify-center">
                        <Crown className="text-yellow-500" size={24} />
                      </div>
                    ) : (
                      <div className="w-12 h-12 rounded-lg bg-neural-cyan/20 border border-neural-cyan/40 flex items-center justify-center">
                        <ShieldCheck className="text-neural-cyan" size={24} />
                      </div>
                    )}
                  </div>

                  {/* Middle Row: Photo and Details */}
                  <div className="flex items-center gap-5 flex-1">
                    {/* Photo */}
                    <div className={`w-24 h-24 rounded-xl flex items-center justify-center border-2 overflow-hidden shadow-2xl shrink-0 ${isPro ? 'border-yellow-500/50 bg-black/50' : 'border-neural-cyan/50 bg-black/50'}`}>
                      {userPhoto ? (
                        /* eslint-disable-next-line @next/next/no-img-element */
                        <img src={userPhoto} alt="User" className="w-full h-full object-cover" />
                      ) : (
                        <User size={40} className="text-gray-500" />
                      )}
                    </div>

                    {/* Details */}
                    <div className="flex flex-col">
                      <p className="text-[10px] text-gray-500 font-mono uppercase tracking-widest mb-1">Cardholder Name</p>
                      <h4 className="text-xl font-bold text-white mb-3 font-outfit uppercase tracking-wide truncate max-w-[200px]">
                        {userName || "OFFICIAL MEMBER"}
                      </h4>

                      <p className="text-[10px] text-gray-500 font-mono uppercase tracking-widest mb-1">Status / App</p>
                      {isPro ? (
                        <p className="text-sm text-yellow-400 font-bold tracking-wide">Elite Pro • Reality</p>
                      ) : (
                        <p className="text-sm text-neural-cyan font-bold tracking-wide">User • Reality</p>
                      )}
                    </div>
                  </div>

                  {/* Footer Row */}
                  <div className="mt-auto pt-4 border-t border-white/10 flex justify-between items-end">
                     <div>
                        <p className={`text-[11px] max-w-[220px] leading-tight ${isPro ? 'text-yellow-100/70' : 'text-gray-400'}`}>
                          {isPro
                            ? "Officially verified Elite Member. Thank you for supporting Neubofy."
                            : "I use Reality and it is really good! Proud to be part of Neubofy."}
                        </p>
                     </div>
                     <div className="text-right flex flex-col items-end">
                        <div className="font-outfit text-white text-sm italic opacity-80 mb-0.5">P. Washudev</div>
                        <p className="text-[8px] text-gray-500 font-mono uppercase">Authorized Signatory</p>
                     </div>
                  </div>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex gap-4 w-full mt-8">
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
