import React from 'react';
import Link from 'next/link';
import HeroActions from './HeroActions';

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
  Code
} from 'lucide-react';

export default async function Home() {

  let latestVersion = "1.0.7";
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
                <span className="font-mono text-sm">99.9% Source-Available</span>
             </div>
          </div>

          <HeroActions latestVersion={latestVersion} />

          <div className="mt-8 flex justify-center">
             <a href='https://reality-digital-wellbeing-and-focus.en.uptodown.com/android' title='Download Reality - The Intelligent Life OS' >
                 <img src='https://stc.utdstc.com/img/mediakit/download-aao-big.png' alt='Download Reality - The Intelligent Life OS' className="h-12 opacity-80 hover:opacity-100 transition-opacity" />
             </a>
          </div>
        </div>
      </section>

      {/* Philosophy Section */}
      <section className="py-24 bg-neural-bg relative z-10 border-b border-gray-800">
        <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
            <h2 className="text-3xl font-bold text-white mb-6">Built for Focus, Not Friction.</h2>
            <p className="text-lg text-gray-400 leading-relaxed mb-6">
              In a world optimized to steal your attention, standard app blockers are just speed bumps.
              Reality is an impenetrable fortress. It combines military-grade app blocking with an onboard autonomous AI agent to force intentional living. No cloud dependencies, no bypasses.
            </p>
        </div>
      </section>

      {/* Core Features Deep Dive Section */}
      <section className="py-24 bg-neural-card/30 relative z-10 border-b border-gray-800">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-16">
            <h2 className="text-4xl font-extrabold text-white mb-4">Core Feature Specifications</h2>
            <p className="text-gray-400 max-w-2xl mx-auto font-mono text-sm">Honest documentation of capabilities, use cases, and technical audit links.</p>
            <div className="h-1 w-20 bg-gradient-to-r from-neural-cyan to-neural-purple mx-auto rounded-full mt-4"></div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-12">
            
            {/* Blocker & Strict Mode */}
            <DetailedFeatureCard
              icon={<Lock className="text-blue-500" size={32} />}
              title="Strict Mode App Blocker"
              underTheHood="Operates via accessibility window lifecycle tracking. Intercepts layouts to prevent settings manipulation and force-stops. Utilizes native Device Administration to block uninstalls."
              bestUseCases="Permanent containment of toxic social channels (TikTok, Reels, Shorts) and blocking system-level bypass loopholes."
              fileName="AppBlockerService.kt"
              fileGithubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/AppBlockerService.kt"
              associatedFileName="RealityBlocker.kt"
              associatedFileGithubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/blockers/RealityBlocker.kt"
            />

            {/* Tapasya (Focus Mode) */}
            <DetailedFeatureCard
              icon={<Zap className="text-neural-cyan" size={32} />}
              title="Tapasya (Neural Focus)"
              underTheHood="Amoled-optimized focus sessions. Enforces a 15-minute chunk requirement ('Effective Time'). Any unauthorized exit immediately triggers lock penalties. Supports QR code exports for offline group focus syncing."
              bestUseCases="Aggressive deep work blocks for studying or writing code, where minor digital slips instantly terminate the session."
              fileName="TapasyaService.kt"
              fileGithubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/TapasyaService.kt"
              associatedFileName="TapasyaActivity.kt"
              associatedFileGithubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/TapasyaActivity.kt"
            />

            {/* Nightly Protocol */}
            <DetailedFeatureCard
              icon={<Moon className="text-neural-purple" size={32} />}
              title="The Nightly Protocol"
              underTheHood="A WorkManager-scheduled 6-step evening protocol. Aggregates screen time, Google Tasks, Calendar events, and Health Connect data. Automates journal reflection via AI and sets up tomorrow's Google Doc planning layout."
              bestUseCases="Structuring your evening routines. Replaces manual task auditing with automated daily scorecards and next-day templates."
              fileName="NightlyWorker.kt"
              fileGithubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/workers/NightlyWorker.kt"
              associatedFileName="NightlyActivity.kt"
              associatedFileGithubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/NightlyActivity.kt"
            />

            {/* AI Assistant */}
            <DetailedFeatureCard
              icon={<Brain className="text-pink-500" size={32} />}
              title="Reality Intelligence Assistant"
              underTheHood="An in-app AI assistant built on the Model Context Protocol (MCP). Runs locally with sliding-window context managers and tokens. Calls on-device tools directly to configure blocks, routines, and alarms."
              bestUseCases="In-app automation, context queries, and hands-free planning commands ('schedule study session at 9 AM tomorrow')."
              fileName="AIChatActivity.kt"
              fileGithubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/AIChatActivity.kt"
              associatedFileName="ToolRegistry.kt"
              associatedFileGithubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/utils/ToolRegistry.kt"
            />

            {/* Health Connect */}
            <DetailedFeatureCard
              icon={<HeartPulse className="text-green-500" size={32} />}
              title="Health Connect Integration"
              underTheHood="Queries Android's Health Connect APIs on-device to read step count, active energy expenditures, and sleep stages. Overlays biometric analytics with productivity output graphs without external cloud tracking."
              bestUseCases="Correlating physiological variables (deep sleep duration, heart rate) directly with daytime focus performance metrics."
              fileName="HealthManager.kt"
              fileGithubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/health/HealthManager.kt"
              associatedFileName="HealthDashboardActivity.kt"
              associatedFileGithubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/HealthDashboardActivity.kt"
            />

            {/* Sleep & Math Alarm */}
            <DetailedFeatureCard
              icon={<SmartphoneCharging className="text-orange-500" size={32} />}
              title="Math-Based Wakeup Alarms"
              underTheHood="Native AlarmManager configurations. Intercepts screen-overlays and back presses during ringing. Prompts math challenges that increase in difficulty if configured for early mornings."
              bestUseCases="Ensuring immediate wakeups for users who suffer from severe morning sleep inertia or frequently snooze alarms."
              fileName="WakeupAlarmService.kt"
              fileGithubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt"
              associatedFileName="WakeupAlarmRingingActivity.kt"
              associatedFileGithubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/WakeupAlarmRingingActivity.kt"
            />

          </div>
        </div>
      </section>

      {/* Hidden Features */}
      <section className="py-24 bg-neural-bg border-b border-gray-800">
         <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
             <div className="text-center mb-16">
                <h2 className="text-3xl font-bold text-white mb-4 flex justify-center items-center gap-3">
                   <EyeOff className="text-neural-purple" /> Power User Features
                </h2>
                <p className="text-gray-400 font-mono text-sm">Advanced system hooks and diagnostic utilities.</p>
             </div>

             <div className="grid md:grid-cols-2 gap-6">
                 <HiddenFeatureCard 
                   title="Deep Link Support" 
                   desc="Trigger commands from automation tools via reality:// URLs (e.g. reality://smart_sleep)." 
                   icon={<Layout size={20}/>} 
                   file="ShortcutActivity.kt"
                   githubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/ShortcutActivity.kt"
                 />
                 <HiddenFeatureCard 
                   title="Terminal Logger" 
                   desc="Detailed file-logging systems for power debugging with custom file output capabilities." 
                   icon={<Cpu size={20}/>} 
                   file="CrashLogger.kt"
                   githubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/CrashLogger.kt"
                 />
                 <HiddenFeatureCard 
                   title="QR Focus Sync" 
                   desc="Scan and synchronize focus timetables offline using dynamic camera scanning layouts." 
                   icon={<Smartphone size={20}/>} 
                   file="QRScannerActivity.kt"
                   githubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/QRScannerActivity.kt"
                 />
                 <HiddenFeatureCard 
                   title="Cinematic Customization" 
                   desc="Configure fonts, widgets, and AMOLED-safe dark modes with custom vector configurations." 
                   icon={<Target size={20}/>} 
                   file="AppearanceActivity.kt"
                   githubLink="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/AppearanceActivity.kt"
                 />
             </div>
         </div>
      </section>

      {/* Tech Stack & Requirements */}
      <section className="py-24 bg-neural-card/30 border-b border-gray-800">
         <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="grid lg:grid-cols-2 gap-16">
               {/* Tech Stack */}
               <div>
                  <h2 className="text-2xl font-bold text-white mb-6 flex items-center gap-3 border-b border-gray-800 pb-4">
                     <Cpu className="text-neural-cyan" /> Technical Stack
                  </h2>
                  <div className="bg-black/50 p-6 rounded-xl border border-gray-800 font-mono text-sm space-y-3">
                     <TechRow label="Platform" value="Android 8.0+ (API 26 to 36)" />
                     <TechRow label="Language" value="Kotlin 100% (type-safe)" />
                     <TechRow label="UI Framework" value="AndroidX + Material3" />
                     <TechRow label="Database" value="Room ORM + SQLite" />
                     <TechRow label="Threading" value="Coroutines (Kotlin Flow)" />
                     <TechRow label="Networking" value="OkHttp + Retrofit (Google APIs)" />
                     <TechRow label="Background" value="WorkManager + AlarmManager" />
                     <TechRow label="Parsing" value="GSON, JSoup, Markwon" />
                  </div>
               </div>

               {/* Requirements */}
               <div>
                  <h2 className="text-2xl font-bold text-white mb-6 flex items-center gap-3 border-b border-gray-800 pb-4">
                     <SmartphoneCharging className="text-neural-cyan" /> System Requirements
                  </h2>
                  <ul className="space-y-4">
                     <ListItem text="RAM: 256MB minimum (typical 50-100MB usage)" />
                     <ListItem text="Storage: 150MB app + database" />
                     <ListItem text="Battery Impact: < 1% drain (Military Grade Native Accessibility)" />
                     <ListItem text="Connectivity: Optional (works offline, Google Sync and Pro features require internet)" />
                     <div className="mt-6 pt-6 border-t border-gray-800">
                          <h4 className="text-white font-semibold mb-3">Core Permissions:</h4>
                          <div className="flex flex-wrap gap-2">
                              <span className="px-3 py-1 bg-blue-500/10 text-blue-400 border border-blue-500/20 rounded-md text-xs font-mono">ACCESSIBILITY_SERVICE</span>
                              <span className="px-3 py-1 bg-purple-500/10 text-purple-400 border border-purple-500/20 rounded-md text-xs font-mono">SYSTEM_ALERT_WINDOW</span>
                              <span className="px-3 py-1 bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 rounded-md text-xs font-mono">PACKAGE_USAGE_STATS</span>
                          </div>
                     </div>
                  </ul>
               </div>
            </div>
         </div>
      </section>

      {/* Comparison Section */}
      <section className="py-24 bg-neural-bg border-b border-gray-800">
         <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="text-center mb-16">
                <h2 className="text-3xl font-bold text-white mb-4">Zero-Trust Security Model</h2>
                <p className="text-gray-400">Why Reality&apos;s architecture is different.</p>
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
                     <ListItem text="Source-Available Audit - Every line of code publicly visible" />
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
                     <ListItem text="Intent hijacking detection (catches overlay attacks)" />
                     <ListItem text="Package manager access blocking (prevents ADB uninstalls)" />
                  </ul>
               </div>
            </div>
         </div>
      </section>

      {/* Google Cloud Credential Guide Section */}
      <section className="py-24 bg-neural-card/30 border-b border-gray-800">
         <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
             <div className="text-center mb-12">
                <h2 className="text-3xl font-bold text-white mb-4">Own Your Data</h2>
                <p className="text-gray-400">How to set up your own Google Cloud Credentials for maximum privacy.</p>
             </div>

             <div className="bg-neural-card p-8 rounded-2xl border border-gray-800 shadow-lg">
                 <h3 className="text-2xl font-bold text-neural-cyan mb-6">Bring Your Own Cloud (BYOC)</h3>
                 <p className="text-gray-300 mb-6 leading-relaxed">
                     Reality is built on a local-first philosophy. Rather than trusting a central server with your Tasks, Calendar, and Drive backups, you can connect your own Google Cloud project. This ensures your data goes straight from your device to your Google account, with zero middlemen.
                 </p>

                 <div className="space-y-6">
                     <div className="border-l-2 border-neural-purple pl-6">
                         <h4 className="text-lg font-bold text-white mb-2">1. Create a Google Cloud Project</h4>
                         <p className="text-gray-400">Navigate to the Google Cloud Console, create a new project, and enable the Google Calendar API, Google Drive API, and Google Tasks API.</p>
                     </div>
                     <div className="border-l-2 border-neural-cyan pl-6">
                         <h4 className="text-lg font-bold text-white mb-2">2. Configure OAuth Consent</h4>
                         <p className="text-gray-400">Set up your OAuth consent screen. Add your email as a test user, and add the necessary scopes for Calendar, Drive, and Tasks.</p>
                     </div>
                     <div className="border-l-2 border-pink-500 pl-6">
                         <h4 className="text-lg font-bold text-white mb-2">3. Generate Credentials</h4>
                         <p className="text-gray-400">Go to Credentials &gt; Create Credentials &gt; OAuth client ID. Choose 'Android' or 'Web application' based on the app's prompt, and copy your Client ID.</p>
                     </div>
                     <div className="border-l-2 border-blue-500 pl-6">
                         <h4 className="text-lg font-bold text-white mb-2">4. Connect in Reality</h4>
                         <p className="text-gray-400 mb-4">Open Reality, go to Settings &gt; Integrations &gt; Google Setup. Paste your Client ID and Client Secret, and authenticate. Your personal sync is now active!</p>
                         <p className="text-xs text-gray-500 font-mono">
                           Verified in: <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/google/GoogleAuthManager.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline flex items-center gap-1 inline-flex">GoogleAuthManager.kt <ArrowUpRight size={10}/></a>
                         </p>
                     </div>
                 </div>
             </div>
         </div>
      </section>

      {/* Quote Section */}
      <section className="py-24 border-t border-gray-800 bg-black relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-b from-transparent to-neural-cyan/5 pointer-events-none"></div>
        <div className="max-w-4xl mx-auto px-4 text-center relative z-10">
          <h2 className="text-3xl md:text-5xl font-bold text-white mb-8 leading-tight">
            &quot;Your data. Your focus. Your life.<br/> <span className="text-transparent bg-clip-text bg-gradient-to-r from-neural-cyan to-neural-purple">On YOUR terms.</span>&quot;
          </h2>
          <p className="text-xl text-gray-400 mb-4 italic">
            Built by someone who lost control of their own fingers. Designed for those who want it back.
          </p>
          <p className="text-gray-500 font-mono mt-8">— Pawan Washudev</p>
        </div>
      </section>

    </div>
  );
}

interface DetailedCardProps {
  icon: React.ReactNode;
  title: string;
  underTheHood: string;
  bestUseCases: string;
  fileName: string;
  fileGithubLink: string;
  associatedFileName: string;
  associatedFileGithubLink: string;
}

function DetailedFeatureCard({ 
  icon, 
  title, 
  underTheHood, 
  bestUseCases, 
  fileName, 
  fileGithubLink,
  associatedFileName,
  associatedFileGithubLink
}: DetailedCardProps) {
  return (
    <div className="bg-neural-card border border-gray-800 p-8 rounded-2xl transition-colors duration-300 hover:border-gray-700 flex flex-col justify-between">
      <div>
        <div className="w-14 h-14 rounded-xl flex items-center justify-center mb-6 bg-neural-bg border border-gray-800">
          {icon}
        </div>
        <h3 className="text-2xl font-bold text-white mb-4">{title}</h3>
        
        <div className="mb-4">
          <span className="text-xs uppercase font-bold text-neural-cyan tracking-wider font-mono">Under the Hood:</span>
          <p className="text-sm text-gray-400 mt-1 leading-relaxed">{underTheHood}</p>
        </div>

        <div className="mb-6">
          <span className="text-xs uppercase font-bold text-neural-purple tracking-wider font-mono">Best Use Case:</span>
          <p className="text-sm text-gray-300 mt-1 leading-relaxed">{bestUseCases}</p>
        </div>
      </div>

      <div className="pt-4 border-t border-gray-900 mt-auto flex flex-col gap-2 font-mono text-xs text-gray-500">
        <div className="flex items-center gap-1.5 justify-between">
          <span className="flex items-center gap-1"><Code size={12}/> File:</span>
          <a href={fileGithubLink} target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline flex items-center gap-0.5">
            {fileName} <ArrowUpRight size={10}/>
          </a>
        </div>

        <div className="flex items-center gap-1.5 justify-between">
          <span className="flex items-center gap-1"><Code size={12}/> Bind:</span>
          <a href={associatedFileGithubLink} target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline flex items-center gap-0.5">
            {associatedFileName} <ArrowUpRight size={10}/>
          </a>
        </div>
      </div>
    </div>
  );
}

function HiddenFeatureCard({ 
  icon, 
  title, 
  desc, 
  file, 
  githubLink 
}: { 
  icon: React.ReactNode, 
  title: string, 
  desc: string, 
  file: string, 
  githubLink: string 
}) {
  return (
    <div className="bg-neural-card/50 border border-gray-800 p-6 rounded-xl flex gap-4 hover:bg-neural-card transition-colors items-start justify-between">
      <div className="flex gap-4">
        <div className="mt-1 text-gray-400">{icon}</div>
        <div>
          <h4 className="text-white font-bold mb-1">{title}</h4>
          <p className="text-sm text-gray-400 leading-relaxed">{desc}</p>
        </div>
      </div>
      <div className="text-right flex-shrink-0">
        <a href={githubLink} target="_blank" rel="noopener noreferrer" className="text-[10px] font-mono text-neural-cyan hover:underline flex items-center gap-0.5 mt-1">
          {file} <ArrowUpRight size={8}/>
        </a>
      </div>
    </div>
  );
}

function TechRow({ label, value }: { label: string, value: string }) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-center justify-between border-b border-gray-800/50 pb-2 last:border-0 last:pb-0">
      <span className="text-gray-500">{label}</span>
      <span className="text-gray-300 text-right">{value}</span>
    </div>
  );
}

function ListItem({ text }: { text: string }) {
  return (
    <li className="flex items-start gap-3">
      <span className="text-neural-cyan mt-1"><CheckCircle size={16} /></span>
      <span className="text-gray-300">{text}</span>
    </li>
  );
}
