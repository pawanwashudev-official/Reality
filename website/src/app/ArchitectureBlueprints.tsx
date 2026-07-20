import React from 'react';
import { 
  Lock, 
  Database, 
  FolderLock, 
  RefreshCw, 
  Cpu, 
  Zap, 
  SmartphoneCharging, 
  Brain, 
  Layout,
  ArrowUpRight,
  Shield,
  Smartphone,
  EyeOff,
  Cloud,
  Globe,
  Code,
  ArrowRight,
  Clock,
  Server
} from 'lucide-react';

// Reusable animated flowchart node
const FlowNode = ({ 
  icon: Icon, 
  title, 
  subtitles = [], 
  colorClass = "text-white", 
  borderColor = "border-gray-700",
  shadowColor = "shadow-none",
  bgStyle = "bg-neural-card",
  className = ""
}: { 
  icon?: React.ElementType, 
  title: string, 
  subtitles?: string[], 
  colorClass?: string,
  borderColor?: string,
  shadowColor?: string,
  bgStyle?: string,
  className?: string 
}) => (
  <div className={`relative flex flex-col items-center justify-center p-4 rounded-xl border-2 ${borderColor} ${bgStyle} ${shadowColor} backdrop-blur-md z-10 transition-transform hover:scale-105 duration-300 ${className}`}>
    {Icon && <Icon className={`mb-2 ${colorClass}`} size={28} />}
    <span className={`text-sm font-bold font-mono text-center ${colorClass}`}>{title}</span>
    {subtitles.map((sub, idx) => (
      <span key={idx} className="text-[10px] text-gray-400 font-mono mt-1 text-center leading-tight max-w-[120px]">{sub}</span>
    ))}
  </div>
);

// Reusable animated connection line (Directional Arrow)
const FlowConnector = ({ colorClass = "text-gray-600", animated = false }) => (
  <div className="flex-1 relative z-0 flex items-center justify-center mx-2 min-w-[30px]">
    <ArrowRight className={`${colorClass} ${animated ? 'animate-pulse' : ''}`} size={24} />
  </div>
);

export default function ArchitectureBlueprints() {
  return (
    <section id="visual-blueprints" className="py-24 bg-neural-bg border-b border-gray-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center mb-20 space-y-4">
          <span className="text-xs uppercase font-mono tracking-widest text-neural-purple font-bold">Architecture Blueprints</span>
          <h2 className="text-4xl md:text-6xl font-extrabold text-white">Technical Connections & Visual Flows</h2>
          <p className="text-gray-400 max-w-3xl mx-auto text-sm">Complete visual mappings explaining Reality&apos;s security policies, database ORM relations, on-device managers, and Cloudflare Worker endpoints.</p>
          <div className="h-1 w-24 bg-gradient-to-r from-neural-cyan to-neural-purple mx-auto rounded-full mt-4"></div>
        </div>

        <div className="space-y-20">

          {/* Layer 1: App Blocker Security */}
          <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl relative overflow-hidden group">
            <div className="absolute -inset-10 bg-neural-cyan/5 rounded-full blur-3xl opacity-0 group-hover:opacity-100 transition duration-1000"></div>
            <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2 relative z-10">
              <Lock className="text-neural-cyan" size={24}/> Block Verification & Tamper Loop
            </h3>
            <div className="grid lg:grid-cols-2 gap-8 items-center relative z-10">
              <div className="space-y-4 text-sm text-gray-400">
                <p>Reality secures your focus using a combination of local system hooks and remote verification to prevent tampering:</p>
                <ul className="space-y-2">
                  <li><strong className="text-white">Accessibility Watchdog:</strong> Instantly hooks layout update triggers to scan package titles against forbidden paths.</li>
                  <li><strong className="text-white">Secure Time Verification:</strong> Reality requires an internet connection to sync with Cloudflare NTP, ensuring users cannot bypass blocks by changing device time.</li>
                </ul>
                <div className="flex flex-wrap gap-x-4 gap-y-2 pt-2">
                  <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/AppBlockerService.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">AppBlockerService.kt <ArrowUpRight size={12}/></a>
                  <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/StrictModeActivity.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">StrictModeActivity.kt <ArrowUpRight size={12}/></a>
                </div>
              </div>
              <div className="bg-black/60 p-6 rounded-xl border border-gray-800 flex items-center justify-between overflow-x-auto">
                <FlowNode title="Window Callback" icon={Smartphone} borderColor="border-neural-purple" colorClass="text-neural-purple" shadowColor="shadow-[0_0_15px_rgba(123,97,255,0.3)]" />
                <FlowConnector colorClass="text-neural-purple" animated />
                <FlowNode title="AppBlockerService" subtitles={["O(1) SettingsBox", "Cloudflare NTP Time", "Device Admin"]} icon={Shield} borderColor="border-neural-cyan" colorClass="text-neural-cyan" shadowColor="shadow-[0_0_20px_rgba(0,229,255,0.4)]" className="mx-2" />
                <FlowConnector colorClass="text-neural-cyan" animated />
                <FlowNode title="Render Lockscreen" icon={EyeOff} borderColor="border-red-500" colorClass="text-red-500" shadowColor="shadow-[0_0_15px_rgba(239,68,68,0.3)]" />
              </div>
            </div>
          </div>

          {/* Layer 2: Room ORM Database Architecture */}
          <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl relative overflow-hidden group">
            <div className="absolute -inset-10 bg-neural-cyan/5 rounded-full blur-3xl opacity-0 group-hover:opacity-100 transition duration-1000"></div>
            <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2 relative z-10">
              <Database className="text-neural-cyan" size={24}/> Database schema (Room ORM)
            </h3>
            <div className="grid lg:grid-cols-2 gap-8 items-center relative z-10">
              <div className="space-y-4 text-sm text-gray-400">
                <p>Reality records local state using Room ORM mapping directly to an isolated database file, while relying on cloud connectivity for secure AI features and verifiable time syncs:</p>
                <ul className="space-y-2">
                  <li><strong className="text-white">Alarms & Tasks:</strong> Store scheduled times, snooze counts, and bidirectional task identifiers.</li>
                  <li><strong className="text-white">AI Memories & Reflections:</strong> Log sliding-window facts and user introduction variables required for remote AI processing.</li>
                </ul>
                <div className="pt-2">
                  <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/data/" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">Reality Room Database Package <ArrowUpRight size={12}/></a>
                </div>
              </div>
              <div className="bg-black/60 p-6 rounded-xl border border-gray-800 flex flex-col items-center justify-center">
                <FlowNode title="RealityDatabase" icon={Database} borderColor="border-neural-cyan" colorClass="text-neural-cyan" shadowColor="shadow-[0_0_20px_rgba(0,229,255,0.4)]" className="w-full max-w-[200px] mb-8" />
                <div className="flex w-full justify-between max-w-sm relative">
                  {/* Fake vertical connectors for visual effect */}
                  <div className="absolute top-[-30px] left-1/2 w-full h-[30px] border-t-2 border-l-2 border-r-2 border-neural-purple/50 rounded-t-lg -translate-x-1/2"></div>
                  
                  <FlowNode title="TaskEntity" subtitles={["Google Sync ID"]} borderColor="border-neural-purple/50" />
                  <FlowNode title="AlarmEntity" subtitles={["Math Difficulty"]} borderColor="border-neural-purple/50" />
                  <FlowNode title="MemoryEntity" subtitles={["Fact Vectors"]} borderColor="border-neural-purple/50" />
                </div>
              </div>
            </div>
          </div>

          {/* Layer 3: Cloud Backup Architecture */}
          <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl relative overflow-hidden group">
            <div className="absolute -inset-10 bg-neural-cyan/5 rounded-full blur-3xl opacity-0 group-hover:opacity-100 transition duration-1000"></div>
            <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2 relative z-10">
              <FolderLock className="text-neural-cyan" size={24}/> Google Drive Encrypted Backups
            </h3>
            <div className="grid lg:grid-cols-2 gap-8 items-center relative z-10">
              <div className="space-y-4 text-sm text-gray-400">
                <p>Your backups stay in your control. The application encrypts local data packages before initiating uploads directly to the user&apos;s Drive folder:</p>
                <ul className="space-y-2">
                  <li><strong className="text-white">Local Encryption:</strong> Database schemas and shared preferences are compiled and encrypted on-device.</li>
                  <li><strong className="text-white">Drive Transfer:</strong> Interacts directly with the personal Google Drive API endpoint.</li>
                </ul>
                <div className="pt-2">
                  <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/google/GoogleDriveManager.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">GoogleDriveManager.kt <ArrowUpRight size={12}/></a>
                </div>
              </div>
              <div className="bg-black/60 p-6 rounded-xl border border-gray-800 flex items-center justify-between overflow-x-auto">
                <FlowNode title="Local SQLite" subtitles={["+ Preferences"]} icon={Database} borderColor="border-neural-purple" colorClass="text-white" />
                <FlowConnector colorClass="text-neural-purple" animated />
                <FlowNode title="JIT Encryption" subtitles={["(AES key seed)"]} icon={Lock} borderColor="border-neural-cyan" colorClass="text-neural-cyan" shadowColor="shadow-[0_0_20px_rgba(0,229,255,0.4)]" className="mx-2" />
                <FlowConnector colorClass="text-neural-cyan" animated />
                <FlowNode title="Google Drive" subtitles={["(/appDataFolder)"]} icon={Cloud} borderColor="border-neural-purple" colorClass="text-white" />
              </div>
            </div>
          </div>

          {/* Layer 4: Google Workspace Handshake */}
          <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl relative overflow-hidden group">
            <div className="absolute -inset-10 bg-neural-cyan/5 rounded-full blur-3xl opacity-0 group-hover:opacity-100 transition duration-1000"></div>
            <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2 relative z-10">
              <RefreshCw className="text-neural-cyan" size={24}/> Google Calendar & Tasks Handshake
            </h3>
            <div className="grid lg:grid-cols-2 gap-8 items-center relative z-10">
              <div className="space-y-4 text-sm text-gray-400">
                <p>Integrates calendar routines and tasks securely on your terms. Direct client-side OAuth handles authentication without middle servers:</p>
                <ul className="space-y-2">
                  <li><strong className="text-white">Credentials Validation:</strong> Interacts with Google Auth server using your client ID configs.</li>
                  <li><strong className="text-white">Synchronized writing:</strong> Automates tasks/events updates in Google Tasks and Calendar.</li>
                </ul>
                <div className="flex flex-wrap gap-x-4 gap-y-2 pt-2">
                  <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/google/GoogleCalendarManager.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">GoogleCalendarManager.kt <ArrowUpRight size={12}/></a>
                  <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/google/GoogleTasksManager.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">GoogleTasksManager.kt <ArrowUpRight size={12}/></a>
                </div>
              </div>
              <div className="bg-black/60 p-6 rounded-xl border border-gray-800 flex items-center justify-between overflow-x-auto">
                <FlowNode title="Reality App" icon={Smartphone} borderColor="border-neural-purple" colorClass="text-white" />
                <FlowConnector colorClass="text-neural-purple" animated />
                <FlowNode title="Google Server" subtitles={["OAuth 2.0 Auth", "Access/Refresh"]} icon={Globe} borderColor="border-neural-cyan" colorClass="text-neural-cyan" shadowColor="shadow-[0_0_20px_rgba(0,229,255,0.4)]" className="mx-2" />
                <FlowConnector colorClass="text-neural-cyan" animated />
                <FlowNode title="Workspace API" subtitles={["Calendar / Tasks"]} icon={RefreshCw} borderColor="border-neural-purple" colorClass="text-white" />
              </div>
            </div>
          </div>

          {/* Layer 5: Cloudflare Worker Edge Identity */}
          <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl relative overflow-hidden group">
            <div className="absolute -inset-10 bg-neural-cyan/5 rounded-full blur-3xl opacity-0 group-hover:opacity-100 transition duration-1000"></div>
            <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2 relative z-10">
              <Server className="text-neural-cyan" size={24}/> Cloudflare Edge: Secure Time & AI Model
            </h3>
            <div className="grid lg:grid-cols-2 gap-8 items-center relative z-10">
              <div className="space-y-4 text-sm text-gray-400">
                <p>Provides critical cloud infrastructure for Reality, ensuring high performance, security, and tamper resistance:</p>
                <ul className="space-y-2">
                  <li><strong className="text-white">Secure Edge AI Model:</strong> Runs the Reality Intelligence Assistant securely on Cloudflare Workers with generous usage limits, protecting prompt logic.</li>
                  <li><strong className="text-white">Cryptographic JIT Identity:</strong> Processes user-specific identity maps via server-side HMAC-SHA256, protecting AES key seeds from client-side exploitation.</li>
                  <li><strong className="text-white">Time Verification:</strong> Uses Cloudflare NTP to guarantee accurate session times independent of the device clock.</li>
                </ul>
                <div className="pt-2">
                  <a href="https://github.com/pawanwashudev-official/Reality/blob/main/workers/identity/worker.js" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">workers/identity/worker.js <ArrowUpRight size={12}/></a>
                </div>
              </div>
              <div className="bg-black/60 p-6 rounded-xl border border-gray-800 flex items-center justify-between overflow-x-auto">
                <FlowNode title="Reality App" subtitles={["(Device)"]} icon={Smartphone} borderColor="border-neural-purple" colorClass="text-white" />
                <FlowConnector colorClass="text-neural-purple" animated />
                <FlowNode title="Cloudflare Edge" subtitles={["Secure AI Model", "HMAC-SHA256", "NTP Verification"]} icon={Server} borderColor="border-neural-cyan" colorClass="text-neural-cyan" shadowColor="shadow-[0_0_20px_rgba(0,229,255,0.4)]" className="mx-2" />
                <FlowConnector colorClass="text-neural-cyan" animated />
                <FlowNode title="AI/Time Response" subtitles={["Verified Data"]} icon={FolderLock} borderColor="border-neural-purple" colorClass="text-white" />
              </div>
            </div>
          </div>

          {/* Layer 6: Tapasya focus mechanics */}
          <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl relative overflow-hidden group">
            <div className="absolute -inset-10 bg-neural-cyan/5 rounded-full blur-3xl opacity-0 group-hover:opacity-100 transition duration-1000"></div>
            <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2 relative z-10">
              <Zap className="text-neural-cyan" size={24}/> Tapasya focus logic
            </h3>
            <div className="grid lg:grid-cols-2 gap-8 items-center relative z-10">
              <div className="space-y-4 text-sm text-gray-400">
                <p>Tapasya monitors work blocks dynamically. If focus is interrupted, penalties are applied and the active work chunk is reset:</p>
                <ul className="space-y-2">
                  <li><strong className="text-white">Effective Focus Calculation:</strong> Aggregates focus in strict 15-minute segments.</li>
                  <li><strong className="text-white">Tamper penalty:</strong> Distraction activities trigger penalty lockouts.</li>
                </ul>
                <div className="pt-2">
                  <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/TapasyaManager.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">TapasyaManager.kt <ArrowUpRight size={12}/></a>
                </div>
              </div>
              <div className="bg-black/60 p-6 rounded-xl border border-gray-800 flex items-center justify-between overflow-x-auto">
                <FlowNode title="Focus Start" icon={Zap} borderColor="border-neural-purple" colorClass="text-white" />
                <FlowConnector colorClass="text-neural-purple" animated />
                <FlowNode title="TapasyaManager" subtitles={["15-min segment math", "App-switch checks"]} icon={Code} borderColor="border-neural-cyan" colorClass="text-neural-cyan" shadowColor="shadow-[0_0_20px_rgba(0,229,255,0.4)]" className="mx-2" />
                <FlowConnector colorClass="text-neural-cyan" animated />
                <FlowNode title="Grade / Penalty" icon={Shield} borderColor="border-neural-purple" colorClass="text-white" />
              </div>
            </div>
          </div>

          {/* Layer 7: Wakeup Alarm scaling */}
          <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl relative overflow-hidden group">
            <div className="absolute -inset-10 bg-neural-cyan/5 rounded-full blur-3xl opacity-0 group-hover:opacity-100 transition duration-1000"></div>
            <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2 relative z-10">
              <SmartphoneCharging className="text-neural-cyan" size={24}/> Math Alarm Difficulty Scaling
            </h3>
            <div className="grid lg:grid-cols-2 gap-8 items-center relative z-10">
              <div className="space-y-4 text-sm text-gray-400">
                <p>Alarms dynamically scale question complexity depending on the hour to prevent snooze-skips on early mornings:</p>
                <ul className="space-y-2">
                  <li><strong className="text-white">Time Check Trigger:</strong> AlarmManager checks current hour.</li>
                  <li><strong className="text-white">Scale multiplier:</strong> Earlier hours generate complex arithmetic challenges.</li>
                </ul>
                <div className="pt-2">
                  <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">WakeupAlarmService.kt <ArrowUpRight size={12}/></a>
                </div>
              </div>
              <div className="bg-black/60 p-6 rounded-xl border border-gray-800 flex items-center justify-between overflow-x-auto">
                <FlowNode title="Alarm Ring" subtitles={["(Triggered)"]} icon={SmartphoneCharging} borderColor="border-neural-purple" colorClass="text-white" />
                <FlowConnector colorClass="text-neural-purple" animated />
                <FlowNode title="Hour Checker" subtitles={["4-6 AM (Hard)", "7-9 AM (Normal)"]} icon={Cpu} borderColor="border-neural-cyan" colorClass="text-neural-cyan" shadowColor="shadow-[0_0_20px_rgba(0,229,255,0.4)]" className="mx-2" />
                <FlowConnector colorClass="text-neural-cyan" animated />
                <FlowNode title="Math Challenge" icon={Code} borderColor="border-neural-purple" colorClass="text-white" />
              </div>
            </div>
          </div>

          {/* Layer 8: MCP Assistant Routing */}
          <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl relative overflow-hidden group">
            <div className="absolute -inset-10 bg-neural-cyan/5 rounded-full blur-3xl opacity-0 group-hover:opacity-100 transition duration-1000"></div>
            <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2 relative z-10">
              <Brain className="text-neural-cyan" size={24}/> Model Context Protocol (MCP) Tool Routing
            </h3>
            <div className="grid lg:grid-cols-2 gap-8 items-center relative z-10">
              <div className="space-y-4 text-sm text-gray-400">
                <p>Reality Intelligence Assistant operates on the Model Context Protocol (MCP) to run tools securely based on user instructions:</p>
                <ul className="space-y-2">
                  <li><strong className="text-white">Command Parsing:</strong> Chat prompts are parsed to identify tool requirements.</li>
                  <li><strong className="text-white">Registry execution:</strong> Binds actions directly to `ToolRegistry.kt` for automation.</li>
                </ul>
                <div className="pt-2">
                  <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/utils/ToolRegistry.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">ToolRegistry.kt <ArrowUpRight size={12}/></a>
                </div>
              </div>
              <div className="bg-black/60 p-6 rounded-xl border border-gray-800 flex items-center justify-between overflow-x-auto">
                <FlowNode title="User Prompt" subtitles={["Voice / Text"]} icon={Brain} borderColor="border-neural-purple" colorClass="text-white" />
                <FlowConnector colorClass="text-neural-purple" animated />
                <FlowNode title="ToolRegistry" subtitles={["Schema validation", "get_tool_schema"]} icon={Code} borderColor="border-neural-cyan" colorClass="text-neural-cyan" shadowColor="shadow-[0_0_20px_rgba(0,229,255,0.4)]" className="mx-2" />
                <FlowConnector colorClass="text-neural-cyan" animated />
                <FlowNode title="App Block / Alarm" icon={Shield} borderColor="border-neural-purple" colorClass="text-white" />
              </div>
            </div>
          </div>

          {/* Layer 9: Cinematic Styling Engine */}
          <div className="bg-neural-card border border-gray-800 p-4 sm:p-8 rounded-2xl relative overflow-hidden group">
            <div className="absolute -inset-10 bg-neural-cyan/5 rounded-full blur-3xl opacity-0 group-hover:opacity-100 transition duration-1000"></div>
            <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-2 relative z-10">
              <Layout className="text-neural-cyan" size={24}/> Appearance & Theme Engine
            </h3>
            <div className="grid lg:grid-cols-2 gap-8 items-center relative z-10">
              <div className="space-y-4 text-sm text-gray-400">
                <p>Handles the user&apos;s customized UI. Styling setups dynamically map XML layouts and vector fonts: </p>
                <ul className="space-y-2">
                  <li><strong className="text-white">Theme Mapping:</strong> Applies custom vector resources and layouts to the UI elements.</li>
                  <li><strong className="text-white">Fonts Integration:</strong> Injects customizable assets to style dashboard consoles.</li>
                </ul>
                <div className="pt-2">
                  <a href="https://github.com/pawanwashudev-official/Reality/blob/main/app/src/main/java/com/neubofy/reality/ui/activity/AppearanceActivity.kt" target="_blank" rel="noopener noreferrer" className="text-neural-cyan hover:underline font-mono text-xs flex items-center gap-1">AppearanceActivity.kt <ArrowUpRight size={12}/></a>
                </div>
              </div>
              <div className="bg-black/60 p-6 rounded-xl border border-gray-800 flex items-center justify-between overflow-x-auto">
                <FlowNode title="Theme Selection" icon={Layout} borderColor="border-neural-purple" colorClass="text-white" />
                <FlowConnector colorClass="text-neural-purple" animated />
                <FlowNode title="Appearance Engine" subtitles={["Load custom fonts", "Map XML colors"]} icon={Code} borderColor="border-neural-cyan" colorClass="text-neural-cyan" shadowColor="shadow-[0_0_20px_rgba(0,229,255,0.4)]" className="mx-2" />
                <FlowConnector colorClass="text-neural-cyan" animated />
                <FlowNode title="Cinematic UI" icon={Smartphone} borderColor="border-neural-purple" colorClass="text-white" />
              </div>
            </div>
          </div>

        </div>
      </div>
    </section>
  );
}
