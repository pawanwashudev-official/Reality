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
  const [step, setStep] = useState(0);
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
    setStep(0);
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
            Share Certificate
          </h2>
          <button onClick={resetAndClose} className="p-2 text-gray-400 hover:text-white transition-colors">
            <X size={20} />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto">
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
              <h3 className="text-xl font-bold text-white text-center">Customize Your Certificate</h3>

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
                  Generate Certificate
                </button>
              </div>
            </div>
          )}

          {step === 3 && (
            <div className="py-4 flex flex-col items-center">

              {/* Certificate Preview Container */}
              <div
                ref={cardRef}
                className={`w-full max-w-[350px] aspect-[4/5] p-1 rounded-2xl relative overflow-hidden flex flex-col ${
                  isPro
                  ? 'bg-gradient-to-br from-yellow-500 via-gray-900 to-black'
                  : 'bg-gradient-to-br from-neural-cyan via-gray-900 to-black'
                }`}
                style={{ backgroundColor: '#05050A' }}
              >
                <div className="absolute inset-0 bg-[#05050A] m-[2px] rounded-2xl z-0"></div>

                {/* Certificate Background Pattern */}
                <div className="absolute inset-0 opacity-10 z-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-white to-transparent"></div>

                {/* Certificate Content */}
                <div className="relative z-10 flex flex-col h-full p-6 text-center">

                  {/* Header */}
                  <div className="flex justify-center items-center gap-2 mb-6">
                    <span className="font-outfit font-black text-2xl tracking-widest text-white">REALITY</span>
                    <span className="text-xs text-gray-500 font-mono">by Neubofy</span>
                  </div>

                  {/* Photo / Avatar */}
                  <div className="flex-1 flex flex-col items-center justify-center">
                    <div className={`w-28 h-28 rounded-full mb-4 flex items-center justify-center border-4 overflow-hidden ${isPro ? 'border-yellow-500/50 bg-yellow-500/10' : 'border-neural-cyan/50 bg-neural-cyan/10'}`}>
                      {userPhoto ? (
                        /* eslint-disable-next-line @next/next/no-img-element */
                        <img src={userPhoto} alt="User" className="w-full h-full object-cover" />
                      ) : (
                        <User size={48} className={isPro ? 'text-yellow-500' : 'text-neural-cyan'} />
                      )}
                    </div>

                    {/* Name */}
                    {userName && (
                      <h4 className="text-2xl font-bold text-white mb-2 font-outfit">{userName}</h4>
                    )}

                    {/* Status Badge */}
                    {isPro ? (
                      <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-yellow-500/20 border border-yellow-500/30 rounded-full text-yellow-500 text-xs font-bold uppercase tracking-wider mb-4">
                        <Crown size={14} /> Elite Member
                      </div>
                    ) : (
                      <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-neural-cyan/20 border border-neural-cyan/30 rounded-full text-neural-cyan text-xs font-bold uppercase tracking-wider mb-4">
                        <ShieldCheck size={14} /> Official Member
                      </div>
                    )}

                    {/* Description */}
                    <p className={`text-sm ${isPro ? 'text-yellow-200/80' : 'text-gray-300'} font-medium px-4 leading-relaxed`}>
                      {isPro
                        ? "Reality Pro Subscriber. Officially verified as an Elite Member of Neubofy."
                        : "I use the Reality app and it is really good! Proud to be a part of Neubofy."}
                    </p>
                  </div>

                  {/* Footer */}
                  <div className="pt-6 border-t border-gray-800/50 mt-auto">
                    <p className="text-xs text-gray-500 font-mono">reality.neubofy.in</p>
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
