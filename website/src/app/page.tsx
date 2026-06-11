/* eslint-disable react/no-unescaped-entities */
import Image from "next/image";
import Link from "next/link";
import { Download, Shield, Bot, Calendar, HardDrive, Smartphone, Zap, Activity, CheckCircle2 } from "lucide-react";

export default function Home() {
  return (
    <div className="flex flex-col min-h-screen">
      <header className="sticky top-0 z-50 w-full border-b border-neutral-200 bg-white/80 backdrop-blur-md">
        <div className="container mx-auto px-4 h-16 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Image
              src="https://res.cloudinary.com/dnh4fonis/image/upload/v1781091079/ck39669alz53z3vkeiaq.png"
              alt="Reality Logo"
              width={32}
              height={32}
              className="rounded-lg"
            />
            <span className="font-bold text-xl tracking-tight">Reality</span>
          </div>
          <nav className="hidden md:flex gap-6">
            <Link href="#features" className="text-sm font-medium text-neutral-600 hover:text-neutral-900 transition-colors">Features</Link>
            <Link href="#comparison" className="text-sm font-medium text-neutral-600 hover:text-neutral-900 transition-colors">Comparison</Link>
            <Link href="#privacy" className="text-sm font-medium text-neutral-600 hover:text-neutral-900 transition-colors">Privacy</Link>
          </nav>
          <div className="flex items-center gap-4">
            <Link href="https://github.com/pawanwashudev-official/Reality" target="_blank" rel="noopener noreferrer" className="text-neutral-600 hover:text-neutral-900">
              <Activity className="w-5 h-5" />
            </Link>
            <Link
              href="https://github.com/pawanwashudev-official/Reality/releases"
              target="_blank"
              rel="noopener noreferrer"
              className="hidden md:inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:pointer-events-none disabled:opacity-50 bg-neutral-900 text-neutral-50 hover:bg-neutral-900/90 h-9 px-4 py-2"
            >
              <Download className="w-4 h-4 mr-2" /> Download APK
            </Link>
            <a href="https://reality-digital-wellbeing-and-focus.en.uptodown.com/android" target="_blank" rel="noopener noreferrer" title="Download Reality - The Intelligent Life OS">
              <img src="https://stc.utdstc.com/img/mediakit/download-gio-big.png" alt="Download Reality - The Intelligent Life OS" className="h-9 w-auto" />
            </a>
          </div>
        </div>
      </header>

      <main className="flex-1">
        {/* Hero Section */}
        <section className="py-20 md:py-32 px-4">
          <div className="container mx-auto max-w-5xl text-center">
            <div className="inline-flex items-center rounded-full border border-neutral-200 bg-white px-3 py-1 text-sm font-medium mb-8">
              <span className="flex h-2 w-2 rounded-full bg-green-500 mr-2"></span>
              100% Open Source & Free Forever
            </div>

            <h1 className="text-5xl md:text-7xl font-extrabold tracking-tight text-neutral-900 mb-6 leading-tight">
              Stop managing your life. <br className="hidden md:block" />
              <span className="text-blue-600">Start commanding it.</span>
            </h1>

            <p className="text-xl md:text-2xl text-neutral-600 mb-10 max-w-3xl mx-auto">
              The intelligent Life OS that weaponizes Google's tools for your productivity. Zero ads, zero trackers, 100% your data.
            </p>

            <div className="flex flex-col sm:flex-row items-center justify-center gap-4 mb-16">
              <Link
                href="https://github.com/pawanwashudev-official/Reality/releases"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center justify-center rounded-lg text-base font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:pointer-events-none disabled:opacity-50 bg-blue-600 text-white hover:bg-blue-700 h-12 px-8 py-3 w-full sm:w-auto"
              >
                <Download className="w-5 h-5 mr-2" /> Download Latest APK
              </Link>
              <a href="https://reality-digital-wellbeing-and-focus.en.uptodown.com/android" target="_blank" rel="noopener noreferrer" title="Download Reality - The Intelligent Life OS" className="h-12 w-full sm:w-auto flex items-center justify-center">
                <img src="https://stc.utdstc.com/img/mediakit/download-gio-big.png" alt="Download Reality - The Intelligent Life OS" className="h-full w-auto object-contain" />
              </a>
              <Link
                href="https://github.com/pawanwashudev-official/Reality"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center justify-center rounded-lg text-base font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:pointer-events-none disabled:opacity-50 border border-neutral-200 bg-white hover:bg-neutral-100 text-neutral-900 h-12 px-8 py-3 w-full sm:w-auto"
              >
                <Activity className="w-5 h-5 mr-2" /> View Source Code
              </Link>
            </div>

            <div className="flex flex-wrap justify-center gap-3">
              {[
                { text: "Platform: Android", icon: Smartphone, color: "text-green-600", bg: "bg-green-50 border-green-200" },
                { text: "Data: Local + G-Drive", icon: HardDrive, color: "text-blue-600", bg: "bg-blue-50 border-blue-200" },
                { text: "AI: Bring Your Own Key", icon: Bot, color: "text-purple-600", bg: "bg-purple-50 border-purple-200" },
                { text: "Ads & Trackers: ZERO", icon: Shield, color: "text-red-600", bg: "bg-red-50 border-red-200" },
              ].map((badge, i) => (
                <div key={i} className={`flex items-center px-4 py-2 rounded-full border ${badge.bg} ${badge.color} text-sm font-semibold`}>
                  <badge.icon className="w-4 h-4 mr-2" />
                  {badge.text}
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* The Problem Section */}
        <section className="py-20 bg-white border-y border-neutral-200 px-4">
          <div className="container mx-auto max-w-4xl text-center">
            <h2 className="text-3xl md:text-4xl font-bold mb-6">Why Does Reality Exist?</h2>
            <p className="text-lg text-neutral-600 mb-12">We searched for the perfect productivity app. Instead, we found a broken industry.</p>

            <div className="grid md:grid-cols-2 gap-8 text-left">
              <div className="p-6 rounded-2xl bg-neutral-50 border border-neutral-100">
                <div className="text-red-500 mb-4">
                  <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                </div>
                <h3 className="text-xl font-bold mb-2">Paid Subscriptions</h3>
                <p className="text-neutral-600">Apps wanting $50/year just to block Instagram. Productivity shouldn't be a luxury tax.</p>
              </div>
              <div className="p-6 rounded-2xl bg-neutral-50 border border-neutral-100">
                <div className="text-red-500 mb-4">
                  <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                </div>
                <h3 className="text-xl font-bold mb-2">Data Harvesting</h3>
                <p className="text-neutral-600">"Free" apps selling your habits and data to advertisers. If it's free, you're the product.</p>
              </div>
              <div className="p-6 rounded-2xl bg-neutral-50 border border-neutral-100">
                <div className="text-red-500 mb-4">
                  <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                </div>
                <h3 className="text-xl font-bold mb-2">Fragmented Tools</h3>
                <p className="text-neutral-600">A timer here. A to-do list there. A habit tracker somewhere else. Zero integration.</p>
              </div>
              <div className="p-6 rounded-2xl bg-neutral-50 border border-neutral-100">
                <div className="text-red-500 mb-4">
                  <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                </div>
                <h3 className="text-xl font-bold mb-2">Closed Ecosystems</h3>
                <p className="text-neutral-600">Your personal data locked forever in proprietary servers you can't control.</p>
              </div>
            </div>

            <div className="mt-12 p-8 bg-blue-50 border border-blue-100 rounded-2xl text-left">
              <h3 className="text-2xl font-bold text-blue-900 mb-4">The Reality Solution</h3>
              <p className="text-blue-800 text-lg">Reality was born from frustration. Built by developers who lost control of their own time. Designed for students and professionals who want to use their phone <strong>for getting things done</strong>, not doom-scrolling. Most apps solve one problem. Reality solves the entire ecosystem of productivity.</p>
            </div>
          </div>
        </section>

        {/* Features Section */}
        <section id="features" className="py-20 px-4">
          <div className="container mx-auto max-w-6xl">
            <div className="text-center mb-16">
              <h2 className="text-3xl md:text-5xl font-bold mb-4">Unparalleled Capabilities</h2>
              <p className="text-xl text-neutral-600">Everything you need to reclaim your focus.</p>
            </div>

            <div className="grid md:grid-cols-3 gap-8">
              {/* Feature 1 */}
              <div className="bg-white p-8 rounded-3xl shadow-sm border border-neutral-200">
                <div className="w-12 h-12 bg-blue-100 text-blue-600 rounded-xl flex items-center justify-center mb-6">
                  <Calendar className="w-6 h-6" />
                </div>
                <h3 className="text-2xl font-bold mb-3">Google Workspace Sync</h3>
                <p className="text-neutral-600 mb-4">Weaponize Google's tools for your productivity.</p>
                <ul className="space-y-2">
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>Docs:</strong> AI diary & reflection written directly to your G-Docs.</span></li>
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>Tasks & Calendar:</strong> Native 2-way sync instantly.</span></li>
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>Drive:</strong> Automatic PDF reports backed up to YOUR folder.</span></li>
                </ul>
              </div>

              {/* Feature 2 */}
              <div className="bg-white p-8 rounded-3xl shadow-sm border border-neutral-200">
                <div className="w-12 h-12 bg-purple-100 text-purple-600 rounded-xl flex items-center justify-center mb-6">
                  <Bot className="w-6 h-6" />
                </div>
                <h3 className="text-2xl font-bold mb-3">Hybrid Agentic AI</h3>
                <p className="text-neutral-600 mb-4">An AI that actually does things, not just chats.</p>
                <ul className="space-y-2">
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>Active Agent:</strong> Automatically sets your system alarms based on plans.</span></li>
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>BYOK:</strong> Use OpenAI, Gemini, Claude, Groq, or OpenRouter keys.</span></li>
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>Free Preset:</strong> Comes with a powerful GPT-OSS-120B model out of the box.</span></li>
                </ul>
              </div>

              {/* Feature 3 */}
              <div className="bg-white p-8 rounded-3xl shadow-sm border border-neutral-200">
                <div className="w-12 h-12 bg-red-100 text-red-600 rounded-xl flex items-center justify-center mb-6">
                  <Shield className="w-6 h-6" />
                </div>
                <h3 className="text-2xl font-bold mb-3">Armored Strict Mode</h3>
                <p className="text-neutral-600 mb-4">Military-grade app blocking that a 5-year-old can't bypass.</p>
                <ul className="space-y-2">
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>Device Admin:</strong> Blocks uninstall attempts during active focus.</span></li>
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>Anti-Tamper:</strong> Instantly detects changing system time to cheat.</span></li>
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>Accessibility Guard:</strong> Catches sneaky app launches via overlays.</span></li>
                </ul>
              </div>

              {/* Feature 4 */}
              <div className="bg-white p-8 rounded-3xl shadow-sm border border-neutral-200">
                <div className="w-12 h-12 bg-indigo-100 text-indigo-600 rounded-xl flex items-center justify-center mb-6">
                  <Zap className="w-6 h-6" />
                </div>
                <h3 className="text-2xl font-bold mb-3">Gamification & XP</h3>
                <p className="text-neutral-600 mb-4">Turn discipline into a highly rewarding game.</p>
                <ul className="space-y-2">
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>Dynamic XP:</strong> Earn for deep work, lose XP for breaking focus.</span></li>
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>Screen Time Penalty:</strong> Stay under limits? Bonus. Over? Penalty.</span></li>
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>True Levels:</strong> Your level reflects actual digital discipline.</span></li>
                </ul>
              </div>

              {/* Feature 5 */}
              <div className="bg-white p-8 rounded-3xl shadow-sm border border-neutral-200">
                <div className="w-12 h-12 bg-emerald-100 text-emerald-600 rounded-xl flex items-center justify-center mb-6">
                  <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" /></svg>
                </div>
                <h3 className="text-2xl font-bold mb-3">The Nightly Protocol</h3>
                <p className="text-neutral-600 mb-4">Your evening ritual to win tomorrow before it starts.</p>
                <ul className="space-y-2">
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>Reflect:</strong> AI asks dynamic questions based on today's actual data.</span></li>
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>Plan:</strong> Schedule tasks directly into Google Calendar.</span></li>
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>Arm:</strong> AI sets tomorrow's alarm and arms blocking rules.</span></li>
                </ul>
              </div>

              {/* Feature 6 */}
              <div className="bg-white p-8 rounded-3xl shadow-sm border border-neutral-200">
                <div className="w-12 h-12 bg-amber-100 text-amber-600 rounded-xl flex items-center justify-center mb-6">
                  <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /></svg>
                </div>
                <h3 className="text-2xl font-bold mb-3">Engineering Perfection</h3>
                <p className="text-neutral-600 mb-4">Built for performance, not battery drain.</p>
                <ul className="space-y-2">
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>&lt;1% Battery:</strong> Uses native AlarmManager. Zero background drain.</span></li>
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>100% Accurate:</strong> Direct UsageStatsManager access.</span></li>
                  <li className="flex items-start"><CheckCircle2 className="w-5 h-5 text-green-500 mr-2 shrink-0 mt-0.5" /> <span className="text-sm"><strong>Fully Customizable:</strong> Toggle features on/off instantly.</span></li>
                </ul>
              </div>
            </div>
          </div>
        </section>

        {/* Comparison Section */}
        <section id="comparison" className="py-20 bg-neutral-900 text-white px-4">
          <div className="container mx-auto max-w-5xl">
            <div className="text-center mb-16">
              <h2 className="text-3xl md:text-5xl font-bold mb-4">Reality vs. The Industry</h2>
              <p className="text-xl text-neutral-400">Why settle for less when you can have it all for free?</p>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-neutral-800">
                    <th className="p-4 font-semibold text-neutral-400">Feature</th>
                    <th className="p-4 font-bold text-xl text-white bg-blue-900/20 rounded-tl-xl">🌌 Reality</th>
                    <th className="p-4 font-semibold text-neutral-400">🌳 Forest</th>
                    <th className="p-4 font-semibold text-neutral-400">🛡️ Freedom</th>
                    <th className="p-4 font-semibold text-neutral-400">📝 Notion</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-neutral-800">
                  <tr>
                    <td className="p-4 font-medium text-neutral-300">Price</td>
                    <td className="p-4 bg-blue-900/10 font-bold text-green-400">Free Forever</td>
                    <td className="p-4 text-neutral-400">Paid Features</td>
                    <td className="p-4 text-neutral-400">Subscription</td>
                    <td className="p-4 text-neutral-400">Subscription</td>
                  </tr>
                  <tr>
                    <td className="p-4 font-medium text-neutral-300">Open Source</td>
                    <td className="p-4 bg-blue-900/10 font-bold text-green-400">100% Yes</td>
                    <td className="p-4 text-red-400">No</td>
                    <td className="p-4 text-red-400">No</td>
                    <td className="p-4 text-red-400">No</td>
                  </tr>
                  <tr>
                    <td className="p-4 font-medium text-neutral-300">Ads / Trackers</td>
                    <td className="p-4 bg-blue-900/10 font-bold text-green-400">ZERO</td>
                    <td className="p-4 text-red-400">Yes (Free tier)</td>
                    <td className="p-4 text-green-400">ZERO</td>
                    <td className="p-4 text-green-400">ZERO</td>
                  </tr>
                  <tr>
                    <td className="p-4 font-medium text-neutral-300">Data Sovereignty</td>
                    <td className="p-4 bg-blue-900/10 font-bold text-green-400">Local + YOUR G-Drive</td>
                    <td className="p-4 text-red-400">Their Servers</td>
                    <td className="p-4 text-red-400">Their Servers</td>
                    <td className="p-4 text-red-400">Their Servers</td>
                  </tr>
                  <tr>
                    <td className="p-4 font-medium text-neutral-300">Agentic AI</td>
                    <td className="p-4 bg-blue-900/10 font-bold text-green-400">Yes (BYOK)</td>
                    <td className="p-4 text-red-400">No</td>
                    <td className="p-4 text-red-400">No</td>
                    <td className="p-4 text-yellow-400">Passive Writer</td>
                  </tr>
                  <tr>
                    <td className="p-4 font-medium text-neutral-300">Google Ecosystem Sync</td>
                    <td className="p-4 bg-blue-900/10 font-bold text-green-400">Tasks, Calendar, Docs</td>
                    <td className="p-4 text-red-400">No</td>
                    <td className="p-4 text-red-400">No</td>
                    <td className="p-4 text-red-400">No</td>
                  </tr>
                  <tr>
                    <td className="p-4 font-medium text-neutral-300">App Blocker Strength</td>
                    <td className="p-4 bg-blue-900/10 font-bold text-green-400">Armored Strict Mode</td>
                    <td className="p-4 text-yellow-400">Gentle Guilt Trip</td>
                    <td className="p-4 text-green-400">VPN Blocking</td>
                    <td className="p-4 text-neutral-500">N/A</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>

        {/* Privacy Architecture */}
        <section id="privacy" className="py-20 bg-white px-4">
          <div className="container mx-auto max-w-5xl">
            <div className="flex flex-col md:flex-row items-center gap-12">
              <div className="md:w-1/2">
                <h2 className="text-3xl md:text-5xl font-bold mb-6">Zero Trust Privacy Architecture</h2>
                <p className="text-lg text-neutral-600 mb-8">We don't want your data. We built the app so we physically cannot access it.</p>

                <div className="space-y-6">
                  <div className="flex">
                    <div className="flex-shrink-0 mt-1">
                      <div className="w-10 h-10 rounded-full bg-red-100 flex items-center justify-center text-red-600">
                        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" /></svg>
                      </div>
                    </div>
                    <div className="ml-4">
                      <h4 className="text-xl font-bold">No Servers</h4>
                      <p className="text-neutral-600">We have absolutely zero backend infrastructure. Your data never touches our systems.</p>
                    </div>
                  </div>

                  <div className="flex">
                    <div className="flex-shrink-0 mt-1">
                      <div className="w-10 h-10 rounded-full bg-blue-100 flex items-center justify-center text-blue-600">
                        <Smartphone className="w-5 h-5" />
                      </div>
                    </div>
                    <div className="ml-4">
                      <h4 className="text-xl font-bold">Local-First</h4>
                      <p className="text-neutral-600">All blocking logic, XP calculation, and app state runs 100% securely on-device.</p>
                    </div>
                  </div>

                  <div className="flex">
                    <div className="flex-shrink-0 mt-1">
                      <div className="w-10 h-10 rounded-full bg-green-100 flex items-center justify-center text-green-600">
                        <HardDrive className="w-5 h-5" />
                      </div>
                    </div>
                    <div className="ml-4">
                      <h4 className="text-xl font-bold">Your Cloud, Your Rules</h4>
                      <p className="text-neutral-600">Backups and documents go straight to your personal Google Drive. We cannot see them.</p>
                    </div>
                  </div>

                  <div className="flex">
                    <div className="flex-shrink-0 mt-1">
                      <div className="w-10 h-10 rounded-full bg-purple-100 flex items-center justify-center text-purple-600">
                        <Activity className="w-5 h-5" />
                      </div>
                    </div>
                    <div className="ml-4">
                      <h4 className="text-xl font-bold">Open Source Audit</h4>
                      <p className="text-neutral-600">Every line of code is public. Build the APK yourself if you don't trust us.</p>
                    </div>
                  </div>
                </div>
              </div>
              <div className="md:w-1/2 bg-neutral-50 p-8 rounded-3xl border border-neutral-200 shadow-sm relative overflow-hidden">
                <div className="absolute top-0 right-0 p-4 opacity-10">
                  <Shield className="w-48 h-48" />
                </div>
                <h3 className="text-2xl font-bold mb-4 relative z-10">"If you're not paying for the product, you are the product."</h3>
                <p className="text-neutral-600 italic mb-6 relative z-10">- The old internet</p>

                <h3 className="text-2xl font-bold mb-4 text-blue-600 relative z-10">"If it's open source and local, you own the product."</h3>
                <p className="text-neutral-600 italic relative z-10">- The Reality philosophy</p>

                <div className="mt-8 pt-8 border-t border-neutral-200 relative z-10">
                  <p className="font-medium text-neutral-900 mb-2">Developed with transparency by:</p>
                  <p className="text-neutral-600">Pawan Washudev | Neubofy</p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* CTA Section */}
        <section className="py-24 bg-blue-600 text-white text-center px-4">
          <div className="container mx-auto max-w-3xl">
            <h2 className="text-4xl md:text-5xl font-bold mb-6">Ready to command your reality?</h2>
            <p className="text-xl text-blue-100 mb-10">Join the open-source revolution. Free forever. No catch.</p>
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
              <Link
                href="https://github.com/pawanwashudev-official/Reality/releases"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center justify-center rounded-lg text-lg font-bold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white disabled:pointer-events-none disabled:opacity-50 bg-white text-blue-600 hover:bg-neutral-100 h-14 px-8 w-full sm:w-auto shadow-lg"
              >
                <Download className="w-6 h-6 mr-2" /> Download APK
              </Link>
              <a href="https://reality-digital-wellbeing-and-focus.en.uptodown.com/android" target="_blank" rel="noopener noreferrer" title="Download Reality - The Intelligent Life OS" className="h-14 w-full sm:w-auto flex items-center justify-center">
                <img src="https://stc.utdstc.com/img/mediakit/download-gio-big.png" alt="Download Reality - The Intelligent Life OS" className="h-full w-auto object-contain drop-shadow-lg" />
              </a>
            </div>
            <p className="mt-6 text-sm text-blue-200">Requires Android 8.0 or higher.</p>
          </div>
        </section>
      </main>

      <footer className="bg-neutral-950 text-neutral-400 py-12 px-4 border-t border-neutral-800">
        <div className="container mx-auto max-w-6xl">
          <div className="flex flex-col md:flex-row justify-between items-center gap-6">
            <div className="flex items-center gap-2 text-white">
              <Image
                src="https://res.cloudinary.com/dnh4fonis/image/upload/v1781091079/ck39669alz53z3vkeiaq.png"
                alt="Reality Logo"
                width={24}
                height={24}
                className="rounded opacity-80 grayscale"
              />
              <span className="font-bold text-lg tracking-tight">Reality</span>
            </div>

            <div className="flex gap-6 text-sm">
              <Link href="/privacypolicy" className="hover:text-white transition-colors">Privacy Policy</Link>
              <Link href="/termsofservice" className="hover:text-white transition-colors">Terms of Service</Link>
              <Link href="https://github.com/pawanwashudev-official/Reality" target="_blank" rel="noopener noreferrer" className="hover:text-white transition-colors">Activity</Link>
            </div>
          </div>

          <div className="mt-8 pt-8 border-t border-neutral-800 text-center text-sm flex flex-col md:flex-row justify-between items-center">
            <p>&copy; {new Date().getFullYear()} Neubofy. All rights reserved.</p>
            <p className="mt-2 md:mt-0">Open Source under Apache-2.0 License.</p>
          </div>
        </div>
      </footer>
    </div>
  );
}
