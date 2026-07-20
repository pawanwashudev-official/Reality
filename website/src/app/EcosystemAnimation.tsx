import React from 'react';
import { Smartphone, Shield, Moon, Brain, Cloud, Globe, FileText, Layout, Database, Zap, Sparkles } from 'lucide-react';

export default function EcosystemAnimation() {
  return (
    <section id="ecosystem" className="py-24 bg-neural-bg border-b border-gray-800 relative overflow-hidden">
      {/* Background gradients */}
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-neural-purple/10 via-neural-bg to-neural-bg opacity-50 z-0 pointer-events-none"></div>
      
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        <div className="text-center mb-16 space-y-4">
          <span className="text-xs uppercase font-mono tracking-widest text-neural-cyan font-bold">The Reality Ecosystem</span>
          <h2 className="text-4xl md:text-6xl font-extrabold text-white">How Everything Connects</h2>
          <p className="text-gray-400 max-w-3xl mx-auto text-sm">A seamless, local-first ecosystem where your Android device acts as the central hub, deeply integrated with Google Workspace and Tapasya Web.</p>
        </div>

        <div className="relative max-w-5xl mx-auto flex flex-col items-center justify-center p-4 sm:p-12">
          
          {/* Connecting Lines (SVG background) */}
          <svg className="absolute inset-0 w-full h-full pointer-events-none opacity-40 z-0" xmlns="http://www.w3.org/2000/svg">
             {/* Center to Top Left */}
             <path d="M 50% 50% L 25% 25%" stroke="#00E5FF" strokeWidth="2" strokeDasharray="5,5" className="animate-pulse" />
             {/* Center to Top Right */}
             <path d="M 50% 50% L 75% 25%" stroke="#7B61FF" strokeWidth="2" strokeDasharray="5,5" className="animate-pulse" />
             {/* Center to Bottom Left */}
             <path d="M 50% 50% L 25% 75%" stroke="#FF3366" strokeWidth="2" strokeDasharray="5,5" className="animate-pulse" />
             {/* Center to Bottom Right */}
             <path d="M 50% 50% L 75% 75%" stroke="#00E5FF" strokeWidth="2" strokeDasharray="5,5" className="animate-pulse" />
          </svg>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 md:gap-16 w-full relative z-10">
            
            {/* Top Left: App Blocker & Nightly Protocol */}
            <div className="flex flex-col items-center text-center space-y-4 transform md:translate-y-12 transition hover:scale-105">
               <div className="w-24 h-24 rounded-2xl bg-neural-card border-2 border-neural-cyan flex items-center justify-center shadow-[0_0_30px_rgba(0,229,255,0.2)] relative">
                 <Shield className="text-neural-cyan" size={40} />
                 <Moon className="absolute -bottom-2 -right-2 text-neural-purple bg-neural-card rounded-full p-1 border border-gray-700" size={28} />
               </div>
               <div>
                 <h3 className="text-lg font-bold text-white">App Blocker & Nightly</h3>
                 <p className="text-xs text-gray-400 mt-2 font-mono">Military-grade blocking, automated evening reflection, and wakeup math.</p>
               </div>
            </div>

            {/* Spacer for grid */}
            <div className="hidden md:block"></div>

            {/* Top Right: Reality Intelligence Assistant */}
            <div className="flex flex-col items-center text-center space-y-4 transform md:translate-y-12 transition hover:scale-105">
               <div className="w-24 h-24 rounded-2xl bg-neural-card border-2 border-neural-purple flex items-center justify-center shadow-[0_0_30px_rgba(123,97,255,0.2)] relative">
                 <Brain className="text-neural-purple" size={40} />
                 <Sparkles className="absolute -top-2 -right-2 text-yellow-400 animate-pulse" size={20} />
               </div>
               <div>
                 <h3 className="text-lg font-bold text-white">Intelligence Assistant</h3>
                 <p className="text-xs text-gray-400 mt-2 font-mono">On-device context engine using MCP to route actions and analyze logs.</p>
               </div>
            </div>

            {/* Center: Reality Android Hub (Span full width on mobile) */}
            <div className="md:col-start-2 md:col-span-1 flex flex-col items-center text-center space-y-4 z-20">
               <div className="relative group">
                 <div className="absolute -inset-4 bg-gradient-to-r from-neural-cyan via-neural-purple to-neural-cyan rounded-full blur-xl opacity-50 group-hover:opacity-80 transition duration-1000 animate-spin-slow"></div>
                 <div className="w-32 h-32 rounded-full bg-black border-4 border-gray-800 flex items-center justify-center relative shadow-2xl">
                   <Smartphone className="text-white" size={56} />
                   <Database className="absolute bottom-2 right-4 text-neural-cyan" size={20} />
                 </div>
               </div>
               <div>
                 <h3 className="text-2xl font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-neural-cyan to-neural-purple">Reality Hub</h3>
                 <p className="text-sm text-gray-300 mt-2 font-mono">The Android core powering the entire ecosystem securely offline.</p>
               </div>
            </div>

            {/* Bottom Left: Google Workspace */}
            <div className="flex flex-col items-center text-center space-y-4 transform md:-translate-y-12 transition hover:scale-105">
               <div className="w-24 h-24 rounded-2xl bg-neural-card border-2 border-red-500 flex items-center justify-center shadow-[0_0_30px_rgba(255,51,102,0.2)] relative">
                 <Cloud className="text-red-500" size={40} />
                 <FileText className="absolute -bottom-2 -right-2 text-blue-400 bg-neural-card rounded-full p-1 border border-gray-700" size={28} />
               </div>
               <div>
                 <h3 className="text-lg font-bold text-white">Google Workspace</h3>
                 <p className="text-xs text-gray-400 mt-2 font-mono">BYOC Drive Sync, Calendar, Tasks, and Gemini Docs integration.</p>
               </div>
            </div>

            {/* Spacer for grid */}
            <div className="hidden md:block"></div>

            {/* Bottom Right: Tapasya Web */}
            <div className="flex flex-col items-center text-center space-y-4 transform md:-translate-y-12 transition hover:scale-105">
               <div className="w-24 h-24 rounded-2xl bg-neural-card border-2 border-neural-cyan flex items-center justify-center shadow-[0_0_30px_rgba(0,229,255,0.2)] relative">
                 <Globe className="text-neural-cyan" size={40} />
                 <Zap className="absolute -top-2 -right-2 text-yellow-400 bg-neural-card rounded-full p-1 border border-gray-700" size={24} />
               </div>
               <div>
                 <h3 className="text-lg font-bold text-white">Tapasya Web</h3>
                 <p className="text-xs text-gray-400 mt-2 font-mono">Connected web portal for distraction-free deep work and analytics.</p>
               </div>
            </div>

          </div>
        </div>
      </div>
    </section>
  );
}
