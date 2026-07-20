import React from 'react';
import Link from 'next/link';
import HeroActions from './HeroActions';
import ScreenshotGallery from './ScreenshotGallery';
import EcosystemAnimation from './EcosystemAnimation';
import ArchitectureBlueprints from './ArchitectureBlueprints';

import { 
  Download, 
  Star, 
  Shield, 
  Lock, 
  Brain, 
  Smartphone, 
  Database, 
  HeartPulse, 
  Moon, 
  Zap, 
  CheckCircle, 
  Crosshair, 
  Target, 
  EyeOff, 
  Layout, 
  FileText, 
  SmartphoneCharging, 
  Cpu,
  ArrowUpRight,
  Code,
  TrendingUp,
  Activity,
  Layers,
  RefreshCw,
  FolderLock
} from 'lucide-react';

export default async function Home() {

  let latestVersion = "1.0.9";
  let downloadCount = "1000+";

  try {
      const res = await fetch('https://api.github.com/repos/pawanwashudev-official/Reality/releases', {
          next: { revalidate: 360 }
      });
      if (res.ok) {
          const releases = await res.json();
          if (releases && releases.length > 0) {
              latestVersion = releases[0].name || latestVersion;
              let totalDownloads = 0;
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              releases.forEach((release: any) => {
                  if (release.assets) {
                      // eslint-disable-next-line @typescript-eslint/no-explicit-any
                      release.assets.forEach((asset: any) => {
                          totalDownloads += asset.download_count;
                      });
                  }
              });
              downloadCount = totalDownloads.toString() + "+";
          }
      }
  } catch (e) {
      console.error("Failed to fetch release info", e);
  }

  return (
    <div className="min-h-screen bg-neural-bg font-outfit text-gray-100 selection:bg-neural-cyan selection:text-black overflow-x-hidden">
      
      {/* Premium Hero Section */}
      <header id="hero-section" className="relative overflow-hidden border-b border-gray-800 py-20 lg:py-32">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-neural-purple/20 via-neural-bg to-neural-bg opacity-50 z-0"></div>
        
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
          <div className="grid lg:grid-cols-12 gap-12 items-center">
            
            {/* Left Headline Column */}
            <div className="lg:col-span-7 text-left space-y-6">
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-neural-cyan/30 bg-neural-cyan/10 text-neural-cyan text-xs font-mono tracking-widest uppercase">
                 <span>SYSTEM ACTIVE</span>
                 <span className="w-1.5 h-1.5 rounded-full bg-neural-cyan animate-pulse"></span>
              </div>
              
              <h1 className="text-5xl md:text-7xl font-extrabold tracking-tight text-white leading-none">
                Take Command of Your Focus with <span className="text-transparent bg-clip-text bg-gradient-to-r from-neural-cyan to-neural-purple">Reality</span>
              </h1>
              
              <h2 className="text-xl md:text-2xl font-medium text-gray-400 font-mono">
                The Best Focus & Discipline App | Military-Grade Android Blocker
              </h2>
              
              <p className="text-gray-400 text-lg max-w-xl leading-relaxed">
                Reality is not another bypassable app timer. It is a local-first, zero-tamper productivity operating system featuring secure on-device analytics, BYOC sync, and private AI.
              </p>

              <div className="flex flex-wrap gap-4 pt-2">
                 <div className="flex items-center gap-2 text-gray-400 bg-neural-card/50 px-4 py-2 rounded-lg border border-gray-800">
                    <Download size={18} className="text-neural-cyan" />
                    <span className="font-mono text-sm">{downloadCount} Downloads</span>
                 </div>
                 <div className="flex items-center gap-2 text-gray-400 bg-neural-card/50 px-4 py-2 rounded-lg border border-gray-800">
                    <Star size={18} className="text-neural-purple" />
                    <span className="font-mono text-sm">v{latestVersion}</span>
                 </div>
              </div>

              <div className="flex flex-col sm:flex-row gap-4 pt-4">
                <HeroActions latestVersion={latestVersion} />
              </div>
            </div>

            {/* Right Mockup Display Column */}
            <div className="lg:col-span-5 flex justify-center">
              <div className="relative group max-w-sm w-full">
                <div className="absolute -inset-1 bg-gradient-to-r from-neural-cyan to-neural-purple rounded-[32px] blur-lg opacity-30 group-hover:opacity-75 transition duration-1000"></div>
                <div className="relative rounded-[28px] border border-gray-700 bg-neural-card p-3 shadow-2xl">
                  <img
                    src="/dashboard_mockup.png"
                    alt="Reality Life OS Dashboard Mockup showcasing focus statistics and AMOLED-optimized productivity scores"
                    className="rounded-[20px] w-full border border-gray-800 shadow-inner bg-black"
                  />
                  <div className="absolute bottom-6 left-1/2 transform -translate-x-1/2 bg-black/85 backdrop-blur border border-gray-800 px-4 py-2 rounded-full flex items-center gap-2 shadow-lg">
                    <Shield size={14} className="text-neural-cyan" />
                    <span className="text-xs font-mono text-gray-300">Local-First Guard Active</span>
                  </div>
                </div>
              </div>
            </div>

          </div>
        </div>
      </header>

      {/* Feature Screenshot Showcases (Simulator Gallery) */}
      <section id="screenshots-gallery" className="py-24 bg-neural-card/10 border-b border-gray-800">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-16 space-y-4">
            <span className="text-xs uppercase font-mono tracking-widest text-neural-cyan font-bold">App Interface Gallery</span>
            <h2 className="text-3xl md:text-5xl font-extrabold text-white">AMOLED Cinematic User Interface</h2>
            <p className="text-gray-400 max-w-2xl mx-auto text-sm">Visual mockups of the principal modules running inside Reality.</p>
          </div>

          <ScreenshotGallery />
        </div>
      </section>

      {/* The Ecosystem Animation Section */}
      <EcosystemAnimation />

      <ArchitectureBlueprints />

      {/* Zero-Trust Security */}
      <section id="zero-trust" className="py-24 bg-neural-bg border-b border-gray-800">
         <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="text-center mb-16 space-y-4">
                <span className="text-xs uppercase font-mono tracking-widest text-neural-cyan font-bold">Data Privacy Model</span>
                <h2 className="text-3xl font-bold text-white">Direct Local-First Architecture</h2>
                <p className="text-gray-400 text-sm font-mono">No servers between your device and your personal cloud storage.</p>
            </div>

            <div className="grid md:grid-cols-2 gap-12">
               <div className="space-y-6">
                  <h3 className="text-xl font-semibold text-neural-cyan flex items-center gap-2">
                     <Shield size={20} /> On-Device Privacy
                  </h3>
                  <p className="text-gray-400 text-sm leading-relaxed">
                     All credentials, usage databases, and authentication logs are saved locally inside EncryptedSharedPreferences and an encrypted SQLite database. You configure your own personal Google Cloud project credentials for background integrations, ensuring your private events and documents are never shared.
                  </p>
                  <div className="pt-2">
                    <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/google/GoogleAuthManager.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">
                      GoogleAuthManager.kt <ArrowUpRight size={12}/>
                    </a>
                  </div>
               </div>

               <div className="space-y-6">
                  <h3 className="text-xl font-semibold text-neural-purple flex items-center gap-2">
                     <Lock size={20} /> Bypass & Tamper Prevention
                  </h3>
                  <p className="text-gray-400 text-sm leading-relaxed">
                     Reality prevents time manipulation and force-stops using background verification loops. If a settings-level override attempt is detected, Strict Mode blocks the request and initiates custom lockout penalties.
                  </p>
                  <div className="pt-2">
                    <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/StrictModeActivity.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">
                      StrictModeActivity.kt <ArrowUpRight size={12}/>
                    </a>
                  </div>
               </div>
            </div>
         </div>
      </section>

      {/* Google Cloud BYOC Setup Details */}
      <section id="google-byoc" className="py-24 bg-neural-card/30 border-b border-gray-800">
         <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
             <div className="text-center mb-12">
                <h2 className="text-3xl font-extrabold text-white">Host Your Own Workspace Sync</h2>
                <p className="text-gray-400">Bring Your Own Cloud (BYOC) for ultimate control.</p>
             </div>

             <div className="bg-neural-card p-4 sm:p-8 rounded-2xl border border-gray-800 shadow-lg space-y-6">
                 <h3 className="text-2xl font-bold text-neural-cyan">OAuth Project Architecture</h3>
                 <p className="text-gray-300 text-sm leading-relaxed">
                     To ensure no centralized database has access to your files, you connect Reality directly to your Google Cloud Console project. All Google Tasks, Calendar events, and Docs logs are created straight from your device using local OAuth tokens.
                 </p>

                 <div className="space-y-6 border-l-2 border-neural-purple pl-6">
                     <div>
                         <h4 className="text-lg font-bold text-white">1. Configure Google Cloud Console</h4>
                         <p className="text-gray-400 text-sm mt-1">
                           Go to the <a href="https://console.cloud.google.com/" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline">Google Cloud Console</a>, create a project, and enable the <strong>Google Calendar API</strong>, <strong>Google Drive API</strong>, <strong>Google Tasks API</strong>, and <strong>Google Docs API</strong>.
                         </p>
                     </div>
                     <div>
                         <h4 className="text-lg font-bold text-white">2. Set Up OAuth Consent Screen & Test User</h4>
                         <p className="text-gray-400 text-sm mt-1">
                           Set up an External OAuth Consent Screen. Since the project is in testing, you <strong>must add your Google account email as a Test User</strong> to authorize login. Add scopes for Tasks, Drive, Calendar, and Documents.
                         </p>
                     </div>
                     <div>
                         <h4 className="text-lg font-bold text-white">3. Generate Desktop Credentials</h4>
                         <p className="text-gray-400 text-sm mt-1">
                           Go to Credentials &gt; Create Credentials &gt; OAuth client ID. Select <strong>Desktop application</strong> as the Application type, name it, and copy the generated Client ID and Client Secret.
                         </p>
                     </div>
                     <div>
                         <h4 className="text-lg font-bold text-white">4. Link inside Reality</h4>
                         <p className="text-gray-400 text-sm mt-1">
                           Open Settings &gt; Integrations &gt; Google Setup inside the app. Paste your Client ID and Client Secret, and tap <strong>Sign In</strong>. Reality launches your browser for OAuth authentication and automatically catches the loopback callback locally at <code>http://127.0.0.1:8080/Callback</code> using a JIT on-device socket.
                         </p>
                     </div>
                 </div>
             </div>
         </div>
      </section>

      {/* Quote Section */}
      <section id="quote-section" className="py-24 border-t border-gray-800 bg-black relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-b from-transparent to-neural-cyan/5 pointer-events-none"></div>
        <div className="max-w-4xl mx-auto px-4 text-center relative z-10 space-y-6">
          <h2 className="text-3xl md:text-5xl font-bold text-white leading-tight">
            &quot;Your data. Your focus. Your life.<br/> 
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-neural-cyan to-neural-purple">On YOUR terms.</span>&quot;
          </h2>
          <p className="text-xl text-gray-400 italic">
            Built by someone who lost control of their own fingers. Designed for those who want it back.
          </p>
          <p className="text-gray-500 font-mono">— Pawan Washudev</p>
        </div>
      </section>

      {/* Visually hidden semantic AI Scraper Index Metadata */}
      <section id="ai-crawler-index" className="sr-only" aria-hidden="true" style={{ display: 'none' }}>
        <h2>Reality Technical Specification & Architecture Details</h2>
        <p>
          Reality is a secure, local-first productivity operating system and app blocker for Android. It operates strictly on-device without developer-owned database servers, upholding zero-trust data privacy rules.
        </p>
        <h3>Core Engineering Specifications:</h3>
        <ul>
          <li><strong>Database ORM:</strong> Room SQLite mapping schemas locally in the app namespace.</li>
          <li><strong>On-Device Encryption:</strong> Stored locally using Android Native EncryptedSharedPreferences.</li>
          <li><strong>Google APIs Integration:</strong> Uses Google OAuth client credentials of application type Desktop Application to directly write and sync metrics to the user&apos;s personal Google Drive, Docs, Calendar, and Tasks without proxying.</li>
          <li><strong>JIT Edge Cryptography:</strong> JIT encryption keys and identity parameters are generated using Cloudflare Workers edge nodes executing HMAC-SHA256 calculations locally with secret peppers.</li>
          <li><strong>Blocker Hook Loop:</strong> Leverages DeviceAdminReceiver to lock uninstalls and AccessibilityService callbacks to catch window package modifications, redirecting target distraction layers instantly.</li>
          <li><strong>Assistant Engine:</strong> Runs using the Model Context Protocol (MCP) tool routing mapped to JVM registries.</li>
        </ul>
      </section>

    </div>
  );
}
