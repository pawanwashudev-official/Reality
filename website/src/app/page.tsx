import React from 'react';
import Link from 'next/link';
import HeroActions from './HeroActions';
import ScreenshotGallery from './ScreenshotGallery';

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

      {/* Visual System Blueprints (11 Inline SVGs Section) */}
      <section id="visual-blueprints" className="py-24 bg-neural-bg border-b border-gray-800">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-20 space-y-4">
            <span className="text-xs uppercase font-mono tracking-widest text-neural-purple font-bold">Architecture Blueprints</span>
            <h2 className="text-4xl md:text-6xl font-extrabold text-white">Technical Connections & Visual Flows</h2>
            <p className="text-gray-400 max-w-3xl mx-auto text-sm">Complete visual mappings explaining Reality&apos;s security policies, database ORM relations, on-device managers, and Cloudflare Worker endpoints.</p>
            <div className="h-1 w-24 bg-gradient-to-r from-neural-cyan to-neural-purple mx-auto rounded-full mt-4"></div>
          </div>

          <div className="space-y-20">

            {/* Diagram Layer 1: App Blocker Security */}
            <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl">
              <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2">
                <Lock className="text-neural-cyan" size={24}/> Block Verification & Tamper Loop
              </h3>
              <div className="grid lg:grid-cols-2 gap-8 items-center">
                <div className="space-y-4 text-sm text-gray-400">
                  <p>Reality secures your focus using two distinct system hooks:</p>
                  <ul className="space-y-2">
                    <li><strong className="text-white">Accessibility Watchdog:</strong> Instantly hooks layout update triggers to scan package titles against forbidden paths.</li>
                    <li><strong className="text-white">Device Admin Safeguard:</strong> Rejects deactivation attempts and shields settings menus from de-authorization hooks.</li>
                  </ul>
                  <div className="flex flex-wrap gap-x-4 gap-y-2 pt-2">
                    <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/AppBlockerService.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">
                      AppBlockerService.kt <ArrowUpRight size={12}/>
                    </a>
                    <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/StrictModeActivity.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">
                      StrictModeActivity.kt <ArrowUpRight size={12}/>
                    </a>
                  </div>
                </div>

                <div className="bg-black/40 p-4 rounded-xl border border-gray-800 overflow-x-auto scrollbar-thin">
                  <svg viewBox="0 0 400 240" className="w-full h-auto min-w-[380px]">
                    {/* Node 1 */}
                    <rect x="10" y="80" width="100" height="60" rx="8" fill="#0D0D14" stroke="#7B61FF" strokeWidth="2"/>
                    <text x="60" y="115" fill="#FFF" fontSize="10" fontFamily="monospace" textAnchor="middle">Window Callback</text>
                    
                    {/* Path 1 */}
                    <path d="M 110,110 L 140,110" stroke="#7B61FF" strokeWidth="2" markerEnd="url(#arrow-purple)"/>

                    {/* Node 2 (Accessibility check) */}
                    <rect x="140" y="30" width="120" height="160" rx="8" fill="#0D0D14" stroke="#00E5FF" strokeWidth="2"/>
                    <text x="200" y="60" fill="#00E5FF" fontSize="11" fontWeight="bold" fontFamily="monospace" textAnchor="middle">AppBlockerService</text>
                    <text x="200" y="90" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">O(1) SettingsBox Map</text>
                    <text x="200" y="120" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">Clock Tamper Audit</text>
                    <text x="200" y="150" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">Device Admin check</text>

                    {/* Path 2 */}
                    <path d="M 260,110 L 290,110" stroke="#00E5FF" strokeWidth="2" markerEnd="url(#arrow-cyan)"/>

                    {/* Node 3 */}
                    <rect x="290" y="80" width="100" height="60" rx="8" fill="#1C0A10" stroke="#FF3366" strokeWidth="2"/>
                    <text x="340" y="115" fill="#FF3366" fontSize="10" fontFamily="monospace" textAnchor="middle">Render Lockscreen</text>
                  </svg>
                </div>
              </div>
            </div>

            {/* Diagram Layer 2: Room ORM Database Architecture */}
            <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl">
              <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2">
                <Database className="text-neural-cyan" size={24}/> Database schema (Room ORM)
              </h3>
              <div className="grid lg:grid-cols-2 gap-8 items-center">
                <div className="space-y-4 text-sm text-gray-400">
                  <p>Reality maintains strict offline-first integrity. All records are persisted using Room ORM mapping directly to an isolated database file on Android:</p>
                  <ul className="space-y-2">
                    <li><strong className="text-white">Alarms & Tasks:</strong> Store scheduled times, snooze counts, and bidirectional task identifiers.</li>
                    <li><strong className="text-white">AI Memories & Reflections:</strong> Log sliding-window facts, user introduction variables, and performance deltas.</li>
                  </ul>
                  <div className="pt-2">
                    <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/data/" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">
                      Reality Room Database Package <ArrowUpRight size={12}/>
                    </a>
                  </div>
                </div>

                <div className="bg-black/40 p-4 rounded-xl border border-gray-800 overflow-x-auto scrollbar-thin">
                  <svg viewBox="0 0 400 240" className="w-full h-auto min-w-[380px]">
                    {/* Node 1: App Database */}
                    <rect x="140" y="20" width="120" height="40" rx="6" fill="#0D0D14" stroke="#00E5FF" strokeWidth="2"/>
                    <text x="200" y="45" fill="#00E5FF" fontSize="11" fontWeight="bold" fontFamily="monospace" textAnchor="middle">RealityDatabase</text>

                    {/* Connectors */}
                    <path d="M 140,40 L 60,100" stroke="#7B61FF" strokeWidth="2"/>
                    <path d="M 200,60 L 200,100" stroke="#7B61FF" strokeWidth="2"/>
                    <path d="M 260,40 L 340,100" stroke="#7B61FF" strokeWidth="2"/>

                    {/* Nodes */}
                    <rect x="10" y="100" width="100" height="60" rx="6" fill="#0D0D14" stroke="#7B61FF" strokeWidth="1"/>
                    <text x="60" y="125" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">TaskEntity</text>
                    <text x="60" y="145" fill="#888" fontSize="8" fontFamily="monospace" textAnchor="middle">Google Sync ID</text>

                    <rect x="150" y="100" width="100" height="60" rx="6" fill="#0D0D14" stroke="#7B61FF" strokeWidth="1"/>
                    <text x="200" y="125" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">AlarmEntity</text>
                    <text x="200" y="145" fill="#888" fontSize="8" fontFamily="monospace" textAnchor="middle">Math Difficulty</text>

                    <rect x="290" y="100" width="100" height="60" rx="6" fill="#0D0D14" stroke="#7B61FF" strokeWidth="1"/>
                    <text x="340" y="125" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">MemoryEntity</text>
                    <text x="340" y="145" fill="#888" fontSize="8" fontFamily="monospace" textAnchor="middle">Fact Vectors</text>
                  </svg>
                </div>
              </div>
            </div>

            {/* Diagram Layer 3: Cloud Backup Architecture */}
            <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl">
              <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2">
                <FolderLock className="text-neural-cyan" size={24}/> Google Drive Encrypted Backups
              </h3>
              <div className="grid lg:grid-cols-2 gap-8 items-center">
                <div className="space-y-4 text-sm text-gray-400">
                  <p>Your backups stay in your control. The application encrypts local data packages before initiating uploads directly to the user&apos;s Drive folder:</p>
                  <ul className="space-y-2">
                    <li><strong className="text-white">Local Encryption:</strong> Database schemas and shared preferences are compiled and encrypted on-device.</li>
                    <li><strong className="text-white">Drive Transfer:</strong> Interacts directly with the personal Google Drive API endpoint.</li>
                  </ul>
                  <div className="pt-2">
                    <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/google/GoogleDriveManager.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">
                      GoogleDriveManager.kt <ArrowUpRight size={12}/>
                    </a>
                  </div>
                </div>

                <div className="bg-black/40 p-4 rounded-xl border border-gray-800 overflow-x-auto scrollbar-thin">
                  <svg viewBox="0 0 450 200" className="w-full h-auto min-w-[380px]">
                    {/* Local DB */}
                    <rect x="10" y="60" width="100" height="80" rx="8" fill="#0D0D14" stroke="#7B61FF" strokeWidth="2"/>
                    <text x="60" y="95" fill="#FFF" fontSize="10" fontFamily="monospace" textAnchor="middle">Local SQLite +</text>
                    <text x="60" y="115" fill="#FFF" fontSize="10" fontFamily="monospace" textAnchor="middle">Preferences</text>

                    {/* Arrow 1 */}
                    <path d="M 110,100 L 160,100" stroke="#7B61FF" strokeWidth="2" markerEnd="url(#arrow-purple)"/>

                    {/* AES Encryptor */}
                    <rect x="160" y="60" width="120" height="80" rx="8" fill="#0D0D14" stroke="#00E5FF" strokeWidth="2"/>
                    <text x="220" y="95" fill="#00E5FF" fontSize="11" fontWeight="bold" fontFamily="monospace" textAnchor="middle">JIT Encryption</text>
                    <text x="220" y="115" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">(AES key seed)</text>

                    {/* Arrow 2 */}
                    <path d="M 280,100 L 330,100" stroke="#00E5FF" strokeWidth="2" markerEnd="url(#arrow-cyan)"/>

                    {/* Google Drive */}
                    <rect x="330" y="60" width="110" height="80" rx="8" fill="#0D0D14" stroke="#7B61FF" strokeWidth="2"/>
                    <text x="385" y="95" fill="#FFF" fontSize="10" fontFamily="monospace" textAnchor="middle">Google Drive</text>
                    <text x="385" y="115" fill="#888" fontSize="9" fontFamily="monospace" textAnchor="middle">(/appDataFolder)</text>
                  </svg>
                </div>
              </div>
            </div>

            {/* Diagram Layer 4: Google Workspace Handshake */}
            <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl">
              <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2">
                <RefreshCw className="text-neural-cyan" size={24}/> Google Calendar & Tasks Handshake
              </h3>
              <div className="grid lg:grid-cols-2 gap-8 items-center">
                <div className="space-y-4 text-sm text-gray-400">
                  <p>Integrates calendar routines and tasks securely on your terms. Direct client-side OAuth handles authentication without middle servers:</p>
                  <ul className="space-y-2">
                    <li><strong className="text-white">Credentials Validation:</strong> Interacts with Google Auth server using your client ID configs.</li>
                    <li><strong className="text-white">Synchronized writing:</strong> Automates tasks/events updates in Google Tasks and Calendar.</li>
                  </ul>
                  <div className="flex flex-wrap gap-x-4 gap-y-2 pt-2">
                    <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/google/GoogleCalendarManager.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">
                      GoogleCalendarManager.kt <ArrowUpRight size={12}/>
                    </a>
                    <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/google/GoogleTasksManager.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">
                      GoogleTasksManager.kt <ArrowUpRight size={12}/>
                    </a>
                  </div>
                </div>

                <div className="bg-black/40 p-4 rounded-xl border border-gray-800 overflow-x-auto scrollbar-thin">
                  <svg viewBox="0 0 400 240" className="w-full h-auto min-w-[380px]">
                    {/* Reality App */}
                    <rect x="20" y="80" width="100" height="80" rx="8" fill="#0D0D14" stroke="#7B61FF" strokeWidth="2"/>
                    <text x="70" y="125" fill="#FFF" fontSize="11" fontWeight="bold" fontFamily="monospace" textAnchor="middle">Reality App</text>

                    {/* Arrow 1 (Auth) */}
                    <path d="M 120,100 L 160,100" stroke="#7B61FF" strokeWidth="2" markerEnd="url(#arrow-purple)"/>

                    {/* Google OAuth */}
                    <rect x="160" y="50" width="110" height="140" rx="8" fill="#0D0D14" stroke="#00E5FF" strokeWidth="2"/>
                    <text x="215" y="85" fill="#00E5FF" fontSize="11" fontWeight="bold" fontFamily="monospace" textAnchor="middle">Google Server</text>
                    <text x="215" y="115" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">OAuth 2.0 Auth</text>
                    <text x="215" y="145" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">Access/Refresh</text>

                    {/* Arrow 2 */}
                    <path d="M 270,100 L 300,100" stroke="#00E5FF" strokeWidth="2" markerEnd="url(#arrow-cyan)"/>

                    {/* Workspace API */}
                    <rect x="300" y="80" width="90" height="80" rx="8" fill="#0D0D14" stroke="#7B61FF" strokeWidth="2"/>
                    <text x="345" y="115" fill="#FFF" fontSize="10" fontFamily="monospace" textAnchor="middle">Workspace</text>
                    <text x="345" y="135" fill="#888" fontSize="9" fontFamily="monospace" textAnchor="middle">Calendar / Tasks</text>
                  </svg>
                </div>
              </div>
            </div>

            {/* Diagram Layer 5: Cloudflare Worker Edge Identity */}
            <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl">
              <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2">
                <Cpu className="text-neural-cyan" size={24}/> Cloudflare Worker Edge JIT Identity
              </h3>
              <div className="grid lg:grid-cols-2 gap-8 items-center">
                <div className="space-y-4 text-sm text-gray-400">
                  <p>Protects key seeds against client-side exploitation. Both user-specific identifier maps and database encryption keys are processed JIT on Cloudflare Workers edge nodes:</p>
                  <ul className="space-y-2">
                    <li><strong className="text-white">Edge Node calculations:</strong> Runs server-side HMAC-SHA256 operations using private secret string peppers.</li>
                    <li><strong className="text-white">Encrypted Injection:</strong> Directly writes verified variables into Android&apos;s EncryptedSharedPreferences container.</li>
                  </ul>
                  <div className="pt-2">
                    <a href="https://github.com/pawanwashudev-official/Reality/blob/main/workers/identity/worker.js" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">
                      workers/identity/worker.js <ArrowUpRight size={12}/>
                    </a>
                  </div>
                </div>

                <div className="bg-black/40 p-4 rounded-xl border border-gray-800 overflow-x-auto scrollbar-thin">
                  <svg viewBox="0 0 450 200" className="w-full h-auto min-w-[380px]">
                    {/* Google Login */}
                    <rect x="10" y="60" width="110" height="80" rx="8" fill="#0D0D14" stroke="#7B61FF" strokeWidth="2"/>
                    <text x="65" y="95" fill="#FFF" fontSize="10" fontFamily="monospace" textAnchor="middle">Google Login</text>
                    <text x="65" y="115" fill="#888" fontSize="9" fontFamily="monospace" textAnchor="middle">(OAuth Callback)</text>

                    {/* Arrow 1 */}
                    <path d="M 120,100 L 170,100" stroke="#7B61FF" strokeWidth="2" markerEnd="url(#arrow-purple)"/>

                    {/* CF Workers */}
                    <rect x="170" y="40" width="140" height="120" rx="8" fill="#0D0D14" stroke="#00E5FF" strokeWidth="2"/>
                    <text x="240" y="75" fill="#00E5FF" fontSize="11" fontWeight="bold" fontFamily="monospace" textAnchor="middle">Cloudflare Edge</text>
                    <text x="240" y="105" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">HMAC-SHA256 JIT</text>
                    <text x="240" y="125" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">Deterministic Keys</text>

                    {/* Arrow 2 */}
                    <path d="M 310,100 L 360,100" stroke="#00E5FF" strokeWidth="2" markerEnd="url(#arrow-cyan)"/>

                    {/* EncryptedPrefs */}
                    <rect x="360" y="60" width="80" height="80" rx="8" fill="#0D0D14" stroke="#7B61FF" strokeWidth="2"/>
                    <text x="400" y="95" fill="#FFF" fontSize="10" fontFamily="monospace" textAnchor="middle">Encrypted</text>
                    <text x="400" y="115" fill="#888" fontSize="9" fontFamily="monospace" textAnchor="middle">SharedPrefs</text>
                  </svg>
                </div>
              </div>
            </div>

            {/* Diagram Layer 6: Tapasya focus mechanics */}
            <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl">
              <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2">
                <Zap className="text-neural-cyan" size={24}/> Tapasya focus logic
              </h3>
              <div className="grid lg:grid-cols-2 gap-8 items-center">
                <div className="space-y-4 text-sm text-gray-400">
                  <p>Tapasya monitors work blocks dynamically. If focus is interrupted, penalties are applied and the active work chunk is reset:</p>
                  <ul className="space-y-2">
                    <li><strong className="text-white">Effective Focus Calculation:</strong> Aggregates focus in strict 15-minute segments.</li>
                    <li><strong className="text-white">Tamper penalty:</strong> Distraction activities trigger penalty lockouts.</li>
                  </ul>
                  <div className="pt-2">
                    <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/TapasyaManager.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">
                      TapasyaManager.kt <ArrowUpRight size={12}/>
                    </a>
                  </div>
                </div>

                <div className="bg-black/40 p-4 rounded-xl border border-gray-800 overflow-x-auto scrollbar-thin">
                  <svg viewBox="0 0 400 200" className="w-full h-auto min-w-[380px]">
                    {/* Tapasya Start */}
                    <rect x="10" y="60" width="100" height="80" rx="8" fill="#0D0D14" stroke="#7B61FF" strokeWidth="2"/>
                    <text x="60" y="105" fill="#FFF" fontSize="11" fontWeight="bold" fontFamily="monospace" textAnchor="middle">Focus Start</text>

                    {/* Arrow 1 */}
                    <path d="M 110,100 L 160,100" stroke="#7B61FF" strokeWidth="2" markerEnd="url(#arrow-purple)"/>

                    {/* Timer Logic */}
                    <rect x="160" y="40" width="120" height="120" rx="8" fill="#0D0D14" stroke="#00E5FF" strokeWidth="2"/>
                    <text x="220" y="75" fill="#00E5FF" fontSize="11" fontWeight="bold" fontFamily="monospace" textAnchor="middle">TapasyaManager</text>
                    <text x="220" y="105" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">15-min segment math</text>
                    <text x="220" y="125" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">App-switch checks</text>

                    {/* Arrow 2 */}
                    <path d="M 280,100 L 330,100" stroke="#00E5FF" strokeWidth="2" markerEnd="url(#arrow-cyan)"/>

                    {/* Outcome */}
                    <rect x="330" y="60" width="60" height="80" rx="8" fill="#0D0D14" stroke="#7B61FF" strokeWidth="2"/>
                    <text x="360" y="95" fill="#FFF" fontSize="10" fontFamily="monospace" textAnchor="middle">Grade /</text>
                    <text x="360" y="115" fill="#FFF" fontSize="10" fontFamily="monospace" textAnchor="middle">Penalty</text>
                  </svg>
                </div>
              </div>
            </div>

            {/* Diagram Layer 7: Wakeup Alarm scaling */}
            <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl">
              <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2">
                <SmartphoneCharging className="text-neural-cyan" size={24}/> Math Alarm Difficulty Scaling
              </h3>
              <div className="grid lg:grid-cols-2 gap-8 items-center">
                <div className="space-y-4 text-sm text-gray-400">
                  <p>Alarms dynamically scale question complexity depending on the hour to prevent snooze-skips on early mornings:</p>
                  <ul className="space-y-2">
                    <li><strong className="text-white">Time Check Trigger:</strong> AlarmManager checks current hour.</li>
                    <li><strong className="text-white">Scale multiplier:</strong> Earlier hours generate complex arithmetic challenges.</li>
                  </ul>
                  <div className="pt-2">
                    <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">
                      WakeupAlarmService.kt <ArrowUpRight size={12}/>
                    </a>
                  </div>
                </div>

                <div className="bg-black/40 p-4 rounded-xl border border-gray-800 overflow-x-auto scrollbar-thin">
                  <svg viewBox="0 0 400 200" className="w-full h-auto min-w-[380px]">
                    {/* Ring Trigger */}
                    <rect x="10" y="60" width="100" height="80" rx="8" fill="#0D0D14" stroke="#7B61FF" strokeWidth="2"/>
                    <text x="60" y="95" fill="#FFF" fontSize="10" fontFamily="monospace" textAnchor="middle">Alarm Ring</text>
                    <text x="60" y="115" fill="#888" fontSize="9" fontFamily="monospace" textAnchor="middle">(Triggered)</text>

                    {/* Arrow 1 */}
                    <path d="M 110,100 L 160,100" stroke="#7B61FF" strokeWidth="2" markerEnd="url(#arrow-purple)"/>

                    {/* Scale Math */}
                    <rect x="160" y="40" width="120" height="120" rx="8" fill="#0D0D14" stroke="#00E5FF" strokeWidth="2"/>
                    <text x="220" y="75" fill="#00E5FF" fontSize="11" fontWeight="bold" fontFamily="monospace" textAnchor="middle">Hour Checker</text>
                    <text x="220" y="105" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">4 AM - 6 AM (Hard)</text>
                    <text x="220" y="125" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">7 AM - 9 AM (Normal)</text>

                    {/* Arrow 2 */}
                    <path d="M 280,100 L 330,100" stroke="#00E5FF" strokeWidth="2" markerEnd="url(#arrow-cyan)"/>

                    {/* Math Screen */}
                    <rect x="330" y="60" width="60" height="80" rx="8" fill="#0D0D14" stroke="#7B61FF" strokeWidth="2"/>
                    <text x="360" y="95" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">Math</text>
                    <text x="360" y="115" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">Challenge</text>
                  </svg>
                </div>
              </div>
            </div>

            {/* Diagram Layer 8: MCP Assistant Routing */}
            <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl">
              <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2">
                <Brain className="text-neural-cyan" size={24}/> Model Context Protocol (MCP) Tool Routing
              </h3>
              <div className="grid lg:grid-cols-2 gap-8 items-center">
                <div className="space-y-4 text-sm text-gray-400">
                  <p>Reality Intelligence Assistant operates on the Model Context Protocol (MCP) to run tools securely based on user instructions:</p>
                  <ul className="space-y-2">
                    <li><strong className="text-white">Command Parsing:</strong> Chat prompts are parsed to identify tool requirements.</li>
                    <li><strong className="text-white">Registry execution:</strong> Binds actions directly to `ToolRegistry.kt` for automation.</li>
                  </ul>
                  <div className="flex flex-wrap gap-x-4 gap-y-2 pt-2">
                    <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/utils/ToolRegistry.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">
                      ToolRegistry.kt <ArrowUpRight size={12}/>
                    </a>
                  </div>
                </div>

                <div className="bg-black/40 p-4 rounded-xl border border-gray-800 overflow-x-auto scrollbar-thin">
                  <svg viewBox="0 0 400 200" className="w-full h-auto min-w-[380px]">
                    {/* User Prompt */}
                    <rect x="10" y="60" width="90" height="80" rx="8" fill="#0D0D14" stroke="#7B61FF" strokeWidth="2"/>
                    <text x="55" y="105" fill="#FFF" fontSize="10" fontFamily="monospace" textAnchor="middle">User Voice/Text</text>

                    {/* Arrow 1 */}
                    <path d="M 100,100 L 150,100" stroke="#7B61FF" strokeWidth="2" markerEnd="url(#arrow-purple)"/>

                    {/* Tool Registry */}
                    <rect x="150" y="40" width="130" height="120" rx="8" fill="#0D0D14" stroke="#00E5FF" strokeWidth="2"/>
                    <text x="215" y="75" fill="#00E5FF" fontSize="11" fontWeight="bold" fontFamily="monospace" textAnchor="middle">ToolRegistry</text>
                    <text x="215" y="105" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">Schema validation</text>
                    <text x="215" y="125" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">get_tool_schema</text>

                    {/* Arrow 2 */}
                    <path d="M 280,100 L 330,100" stroke="#00E5FF" strokeWidth="2" markerEnd="url(#arrow-cyan)"/>

                    {/* Executions */}
                    <rect x="330" y="60" width="60" height="80" rx="8" fill="#0D0D14" stroke="#7B61FF" strokeWidth="2"/>
                    <text x="360" y="95" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">App Block/</text>
                    <text x="360" y="115" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">Alarm Set</text>
                  </svg>
                </div>
              </div>
            </div>

            {/* Diagram Layer 9: Cinematic Styling Engine */}
            <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl">
              <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2">
                <Layout className="text-neural-cyan" size={24}/> Appearance & Theme Engine
              </h3>
              <div className="grid lg:grid-cols-2 gap-8 items-center">
                <div className="space-y-4 text-sm text-gray-400">
                  <p>Handles the user&apos;s customized UI. Styling setups dynamically map XML layouts and vector fonts: </p>
                  <ul className="space-y-2">
                    <li><strong className="text-white">Theme Mapping:</strong> Applies custom vector resources and layouts to the UI elements.</li>
                    <li><strong className="text-white">Fonts Integration:</strong> Injects customizable assets to style dashboard consoles.</li>
                  </ul>
                  <div className="pt-2">
                    <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/AppearanceActivity.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">
                      AppearanceActivity.kt <ArrowUpRight size={12}/>
                    </a>
                  </div>
                </div>

                <div className="bg-black/40 p-4 rounded-xl border border-gray-800 overflow-x-auto scrollbar-thin">
                  <svg viewBox="0 0 400 200" className="w-full h-auto min-w-[380px]">
                    {/* User Selection */}
                    <rect x="10" y="60" width="100" height="80" rx="8" fill="#0D0D14" stroke="#7B61FF" strokeWidth="2"/>
                    <text x="60" y="105" fill="#FFF" fontSize="10" fontFamily="monospace" textAnchor="middle">Theme Selection</text>

                    {/* Arrow 1 */}
                    <path d="M 110,100 L 160,100" stroke="#7B61FF" strokeWidth="2" markerEnd="url(#arrow-purple)"/>

                    {/* Theme Manager */}
                    <rect x="160" y="40" width="120" height="120" rx="8" fill="#0D0D14" stroke="#00E5FF" strokeWidth="2"/>
                    <text x="220" y="75" fill="#00E5FF" fontSize="11" fontWeight="bold" fontFamily="monospace" textAnchor="middle">Appearance Engine</text>
                    <text x="220" y="105" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">Load custom font assets</text>
                    <text x="220" y="125" fill="#FFF" fontSize="9" fontFamily="monospace" textAnchor="middle">Map XML colors</text>

                    {/* Arrow 2 */}
                    <path d="M 280,100 L 330,100" stroke="#00E5FF" strokeWidth="2" markerEnd="url(#arrow-cyan)"/>

                    {/* Rendered UI */}
                    <rect x="330" y="60" width="60" height="80" rx="8" fill="#0D0D14" stroke="#7B61FF" strokeWidth="2"/>
                    <text x="360" y="95" fill="#FFF" fontSize="10" fontFamily="monospace" textAnchor="middle">Cinematic</text>
                    <text x="360" y="115" fill="#FFF" fontSize="10" fontFamily="monospace" textAnchor="middle">UI</text>
                  </svg>
                </div>
              </div>
            </div>

          </div>
        </div>
      </section>

      {/* SVG marker definitions */}
      <svg className="hidden">
        <defs>
          <marker id="arrow-purple" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#7B61FF" />
          </marker>
          <marker id="arrow-cyan" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#00E5FF" />
          </marker>
        </defs>
      </svg>

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
