import React from 'react';
import Link from 'next/link';
import { Download, Star, Shield, Lock, Brain, Smartphone, Database, HeartPulse, Moon, Zap, Activity } from 'lucide-react';

export default async function Home() {

  let latestVersion = "1.0.6";
  let downloadCount = "1.2k+";

  try {
      const res = await fetch('https://api.github.com/repos/pawanwashudev-official/Reality/releases/latest', {
          next: { revalidate: 3600 } // Cache for 1 hour
      });
      if (res.ok) {
          const data = await res.json();
          latestVersion = data.name || latestVersion;
          if (data.assets && data.assets.length > 0) {
              downloadCount = data.assets[0].download_count.toString();
          }
      }
  } catch (e) {
      console.error("Failed to fetch release info", e);
  }

  return (
    <div className="min-h-screen bg-neural-bg font-outfit text-gray-100 selection:bg-neural-cyan selection:text-black">
      {/* Hero Section */}
      <section className="relative overflow-hidden border-b border-gray-800">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-neural-purple/20 via-neural-bg to-neural-bg opacity-50 z-0"></div>
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-24 text-center relative z-10">
          <div className="mb-8 flex justify-center">
            <div className="relative group">
              <div className="absolute -inset-1 bg-gradient-to-r from-neural-cyan to-neural-purple rounded-3xl blur opacity-25 group-hover:opacity-100 transition duration-1000 group-hover:duration-200"></div>
              <img
                src="https://res.cloudinary.com/dnh4fonis/image/upload/v1781091079/ck39669alz53z3vkeiaq.png"
                alt="Reality Logo"
                className="relative w-32 h-32 rounded-3xl shadow-xl border border-gray-700 bg-neural-card"
              />
            </div>
          </div>

          <div className="inline-flex items-center gap-2 mb-4 px-3 py-1 rounded-full border border-neural-cyan/30 bg-neural-cyan/10 text-neural-cyan text-xs font-mono tracking-widest uppercase">
             <span>SYSTEM ONLINE</span>
             <span className="w-1.5 h-1.5 rounded-full bg-neural-cyan animate-pulse"></span>
          </div>

          <h1 className="text-5xl md:text-7xl font-extrabold tracking-tight text-white mb-4 drop-shadow-md">
            Reality
          </h1>
          <h2 className="text-2xl md:text-3xl font-medium text-gray-400 mb-6 font-mono">
            The Intelligent Life OS
          </h2>
          <p className="max-w-2xl mx-auto text-xl text-gray-500 mb-8 italic">
            &quot;Stop managing your life. Start commanding it.&quot;
          </p>

          <div className="flex flex-wrap justify-center gap-4 mb-10">
             <div className="flex items-center gap-2 text-gray-400 bg-neural-card/50 px-4 py-2 rounded-lg border border-gray-800">
                <Download size={18} className="text-neural-cyan" />
                <span className="font-mono text-sm">{downloadCount} Downloads</span>
             </div>
             <div className="flex items-center gap-2 text-gray-400 bg-neural-card/50 px-4 py-2 rounded-lg border border-gray-800">
                <Star size={18} className="text-neural-purple" />
                <span className="font-mono text-sm">{latestVersion}</span>
             </div>
             <div className="flex items-center gap-2 text-gray-400 bg-neural-card/50 px-4 py-2 rounded-lg border border-gray-800">
                <Shield size={18} className="text-green-500" />
                <span className="font-mono text-sm">100% Open Source</span>
             </div>
          </div>

          <div className="flex flex-col sm:flex-row justify-center items-center gap-4">
            <Link
              href="https://github.com/pawanwashudev-official/Reality/releases/latest"
              className="w-full sm:w-auto px-8 py-4 bg-white text-black text-lg font-bold rounded-xl hover:bg-gray-200 transition-colors shadow-lg flex items-center justify-center gap-2"
            >
              <Download size={20} />
              Download APK
            </Link>
            <Link
              href="https://github.com/pawanwashudev-official/Reality"
              className="w-full sm:w-auto px-8 py-4 bg-neural-card border border-gray-700 text-white text-lg font-bold rounded-xl hover:border-gray-500 transition-colors shadow-lg"
            >
              View Source Code
            </Link>
          </div>
        </div>
      </section>

      {/* Philosophy Section */}
      <section className="py-24 bg-neural-bg relative z-10 border-b border-gray-800">
        <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
            <h2 className="text-3xl font-bold text-white mb-6">Built for Focus, Not Friction.</h2>
            <p className="text-lg text-gray-400 leading-relaxed">
              In a world optimized to steal your attention, standard app blockers are just speed bumps.
              Reality is an impenetrable fortress. It combines military-grade app blocking with an onboard autonomous AI agent to force intentional living. No cloud dependencies, no subscription fees, no bypasses.
            </p>
        </div>
      </section>

      {/* Features Grid */}
      <section className="py-24 bg-neural-card/30 relative z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-16">
            <h2 className="text-3xl font-bold text-white mb-4">Core Architecture</h2>
            <div className="h-1 w-20 bg-gradient-to-r from-neural-cyan to-neural-purple mx-auto rounded-full"></div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            <FeatureCard
              icon={<Zap className="text-neural-cyan" size={28} />}
              title="Tapasya (Focus Mode)"
              desc="Amoled-optimized, military-grade distraction blocking. Neural sync allows automated Do Not Disturb sessions."
              colorClass="group-hover:border-neural-cyan bg-neural-cyan/10"
            />
            <FeatureCard
              icon={<Moon className="text-neural-purple" size={28} />}
              title="Nightly Protocol"
              desc="End-of-day sequence that forces reflection, auto-generates professional PDF logs, and syncs tasks to G-Drive."
              colorClass="group-hover:border-neural-purple bg-neural-purple/10"
            />
             <FeatureCard
              icon={<Lock className="text-blue-500" size={28} />}
              title="Strict Mode OS"
              desc="Impossible-to-bypass app blocking, screen-overlay prevention, and anti-uninstall security layer."
              colorClass="group-hover:border-blue-500 bg-blue-500/10"
            />
            <FeatureCard
              icon={<Brain className="text-pink-500" size={28} />}
              title="Neural Assistant"
              desc="Bring Your Own Key (BYOK) AI chat integrated into your daily workflow without the subscription fees."
              colorClass="group-hover:border-pink-500 bg-pink-500/10"
            />
             <FeatureCard
              icon={<HeartPulse className="text-green-500" size={28} />}
              title="Health Connect"
              desc="Deep sync with Android Health Connect to overlay your productivity stats with sleep and biometric data."
              colorClass="group-hover:border-green-500 bg-green-500/10"
            />
             <FeatureCard
              icon={<Database className="text-orange-500" size={28} />}
              title="G-Cloud BYOK"
              desc="Use your own Desktop OAuth credentials to securely sync with Tasks, Calendar, Drive, and Docs locally."
              colorClass="group-hover:border-orange-500 bg-orange-500/10"
            />
          </div>
        </div>
      </section>

      {/* Comparison Section */}
      <section className="py-24 bg-neural-bg border-t border-gray-800">
         <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="text-center mb-16">
                <h2 className="text-3xl font-bold text-white mb-4">Zero-Trust Security Model</h2>
                <p className="text-gray-400">Why Reality's architecture is different.</p>
            </div>

            <div className="grid md:grid-cols-2 gap-12">
               <div className="space-y-6">
                  <h3 className="text-xl font-semibold text-neural-cyan flex items-center gap-2">
                     <Shield size={20} /> Data & Privacy
                  </h3>
                  <ul className="space-y-4">
                     <ListItem text="No Backend Servers - Reality has zero infrastructure" />
                     <ListItem text="Local-First Processing - All blocking logic runs on-device" />
                     <ListItem text="Your Drive Only - Backups stored in your personal Google Drive" />
                     <ListItem text="Open Source Audit - Every line of code publicly visible" />
                     <ListItem text="Encrypted Credentials - OAuth tokens stored securely" />
                  </ul>
               </div>

               <div className="space-y-6">
                  <h3 className="text-xl font-semibold text-neural-purple flex items-center gap-2">
                     <Lock size={20} /> Bypass Prevention
                  </h3>
                  <ul className="space-y-4">
                     <ListItem text="AccessibilityService window monitoring (real-time)" />
                     <ListItem text="SettingsBox O(1) lookup (instant page detection)" />
                     <ListItem text="Keyword scanning fallback (for ambiguous settings pages)" />
                     <ListItem text="Device Admin enforcement (prevents uninstall)" />
                     <ListItem text="Time validation (detects clock tampering)" />
                  </ul>
               </div>
            </div>
         </div>
      </section>

      {/* Quote Section */}
      <section className="py-24 border-t border-gray-800 bg-black relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-b from-transparent to-neural-cyan/5 pointer-events-none"></div>
        <div className="max-w-4xl mx-auto px-4 text-center relative z-10">
          <h2 className="text-3xl md:text-5xl font-bold text-white mb-8 leading-tight">
            "Your data. Your focus. Your life.<br/> <span className="text-transparent bg-clip-text bg-gradient-to-r from-neural-cyan to-neural-purple">On YOUR terms.</span>"
          </h2>
          <p className="text-xl text-gray-400 mb-4 italic">
            Built by someone who lost control of their own fingers. Designed for those who want it back.
          </p>
          <p className="text-gray-500 font-mono mt-8">— Pawan Washudev</p>
        </div>
      </section>

      {/* Footer */}
      <footer className="bg-black py-12 border-t border-gray-900">
        <div className="max-w-7xl mx-auto px-4 text-center text-gray-600 text-sm">
          <p className="mb-4">© {new Date().getFullYear()} Neubofy. All rights reserved.</p>
          <div className="flex justify-center gap-6 font-mono">
             <Link href="https://github.com/pawanwashudev-official/Reality" className="hover:text-white transition-colors">GitHub</Link>
             <Link href="mailto:support@neubofy.in" className="hover:text-white transition-colors">Contact</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}

function FeatureCard({ icon, title, desc, colorClass }: { icon: React.ReactNode, title: string, desc: string, colorClass: string }) {
   return (
      <div className={`bg-neural-card border border-gray-800 p-8 rounded-2xl transition-colors duration-300 group hover:border-gray-600 ${colorClass.split(' ')[0]}`}>
         <div className={`w-14 h-14 rounded-xl flex items-center justify-center mb-6 group-hover:scale-110 transition-transform duration-300 ${colorClass.split(' ')[1]}`}>
         {icon}
         </div>
         <h3 className="text-xl font-bold text-white mb-3">{title}</h3>
         <p className="text-gray-400 leading-relaxed">
         {desc}
         </p>
      </div>
   )
}

function ListItem({ text }: { text: string }) {
   return (
      <li className="flex items-start gap-3">
         <span className="text-neural-cyan mt-1">•</span>
         <span className="text-gray-300">{text}</span>
      </li>
   )
}
