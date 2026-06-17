import React from 'react';
import Link from 'next/link';

export default function Home() {
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

          <div className="inline-block mb-4 px-3 py-1 rounded-full border border-neural-cyan/30 bg-neural-cyan/10 text-neural-cyan text-xs font-mono tracking-widest uppercase">
            SYSTEM ONLINE V1.0.6
          </div>

          <h1 className="text-5xl md:text-7xl font-extrabold tracking-tight text-white mb-4 drop-shadow-md">
            Reality <span className="text-transparent bg-clip-text bg-gradient-to-r from-neural-cyan to-neural-purple">Engine</span>
          </h1>
          <h2 className="text-2xl md:text-3xl font-medium text-gray-400 mb-6 font-mono">
            The Intelligent Life OS
          </h2>
          <p className="max-w-2xl mx-auto text-xl text-gray-500 mb-10 italic">
            &quot;Stop managing your life. Start commanding it.&quot;
          </p>

          <div className="flex flex-wrap justify-center gap-3 mb-12 font-mono">
            <span className="px-4 py-1.5 rounded-full text-sm font-semibold border border-neural-cyan text-neural-cyan bg-neural-cyan/10 shadow-neon">Platform: Android</span>
            <span className="px-4 py-1.5 rounded-full text-sm font-semibold border border-gray-600 text-gray-300 bg-gray-800">Data: Local + G-Cloud</span>
            <span className="px-4 py-1.5 rounded-full text-sm font-semibold border border-neural-purple text-neural-purple bg-neural-purple/10">AI: BYOK Neural Core</span>
          </div>

          <div className="flex flex-col sm:flex-row justify-center items-center gap-4">
            <Link
              href="https://github.com/pawanwashudev-official/Reality/releases/latest"
              className="w-full sm:w-auto px-8 py-4 bg-white text-black text-lg font-bold rounded-xl hover:bg-gray-200 transition-colors shadow-lg"
            >
              Get Professional APK
            </Link>
            <Link
              href="https://github.com/pawanwashudev-official/Reality"
              className="w-full sm:w-auto px-8 py-4 bg-neural-card border border-gray-700 text-white text-lg font-bold rounded-xl hover:border-gray-500 transition-colors shadow-lg"
            >
              View Source Code
            </Link>
          </div>
          <p className="mt-6 text-sm text-gray-500 max-w-xl mx-auto">
            100% Open Source. Official APKs require a one-time &quot;Reality Pro&quot; contribution to support development.
          </p>
        </div>
      </section>

      {/* Features Grid */}
      <section className="py-20 bg-neural-bg relative z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-16">
            <h2 className="text-3xl font-bold text-white mb-4">Core Architecture</h2>
            <div className="h-1 w-20 bg-gradient-to-r from-neural-cyan to-neural-purple mx-auto rounded-full"></div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            {/* Feature 1 */}
            <div className="bg-neural-card border border-gray-800 p-8 rounded-2xl hover:border-neural-cyan transition-colors group">
              <div className="w-12 h-12 bg-neural-cyan/20 rounded-xl flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                <span className="text-2xl text-neural-cyan">🔥</span>
              </div>
              <h3 className="text-xl font-bold text-white mb-3">Tapasya (Focus Mode)</h3>
              <p className="text-gray-400">
                Amoled-optimized, military-grade distraction blocking. Neural sync allows automated Do Not Disturb sessions.
              </p>
            </div>

            {/* Feature 2 */}
            <div className="bg-neural-card border border-gray-800 p-8 rounded-2xl hover:border-neural-purple transition-colors group">
              <div className="w-12 h-12 bg-neural-purple/20 rounded-xl flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                <span className="text-2xl text-neural-purple">🌙</span>
              </div>
              <h3 className="text-xl font-bold text-white mb-3">Nightly Protocol</h3>
              <p className="text-gray-400">
                End-of-day sequence that forces reflection, auto-generates professional PDF logs, and syncs tasks to G-Drive.
              </p>
            </div>

            {/* Feature 3 */}
            <div className="bg-neural-card border border-gray-800 p-8 rounded-2xl hover:border-blue-500 transition-colors group">
              <div className="w-12 h-12 bg-blue-500/20 rounded-xl flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                <span className="text-2xl text-blue-500">🔒</span>
              </div>
              <h3 className="text-xl font-bold text-white mb-3">Strict Mode OS</h3>
              <p className="text-gray-400">
                Impossible-to-bypass app blocking, screen-overlay prevention, and anti-uninstall security layer.
              </p>
            </div>

             {/* Feature 4 */}
             <div className="bg-neural-card border border-gray-800 p-8 rounded-2xl hover:border-pink-500 transition-colors group">
              <div className="w-12 h-12 bg-pink-500/20 rounded-xl flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                <span className="text-2xl text-pink-500">🤖</span>
              </div>
              <h3 className="text-xl font-bold text-white mb-3">Neural Assistant</h3>
              <p className="text-gray-400">
                Bring Your Own Key (BYOK) AI chat integrated into your daily workflow without the subscription fees.
              </p>
            </div>

            {/* Feature 5 */}
            <div className="bg-neural-card border border-gray-800 p-8 rounded-2xl hover:border-green-500 transition-colors group">
              <div className="w-12 h-12 bg-green-500/20 rounded-xl flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                <span className="text-2xl text-green-500">📊</span>
              </div>
              <h3 className="text-xl font-bold text-white mb-3">Health Connect</h3>
              <p className="text-gray-400">
                Deep sync with Android Health Connect to overlay your productivity stats with sleep and biometric data.
              </p>
            </div>

            {/* Feature 6 */}
            <div className="bg-neural-card border border-gray-800 p-8 rounded-2xl hover:border-orange-500 transition-colors group">
              <div className="w-12 h-12 bg-orange-500/20 rounded-xl flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                <span className="text-2xl text-orange-500">☁️</span>
              </div>
              <h3 className="text-xl font-bold text-white mb-3">G-Cloud BYOK</h3>
              <p className="text-gray-400">
                Use your own Desktop OAuth credentials to securely sync with Tasks, Calendar, Drive, and Docs locally.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Philosophy Section */}
      <section className="py-20 border-t border-gray-800 bg-neural-card/50">
        <div className="max-w-4xl mx-auto px-4 text-center">
           <h2 className="text-3xl font-bold text-white mb-6">Open Source, Pro Optimized</h2>
           <p className="text-lg text-gray-400 mb-8">
             Reality is 100% open source. You can clone the repo and build the Android APK yourself for free.
             However, the <span className="text-white font-semibold">Official Pro APK</span> is heavily optimized, includes standard pre-compiled assets, and buying it supports the continuous maintenance of the project.
           </p>
           <a href="mailto:support@neubofy.in" className="text-neural-cyan hover:underline font-mono">support@neubofy.in</a>
        </div>
      </section>

      {/* Quote Section */}
      <section className="py-20 border-t border-gray-800 bg-black">
        <div className="max-w-4xl mx-auto px-4 text-center">
          <h2 className="text-3xl font-bold text-white mb-6">
            "Your data. Your focus. Your life. On YOUR terms."
          </h2>
          <p className="text-lg text-gray-400 mb-8">
            Built by someone who lost control of their own fingers. Designed for those who want it back.
          </p>
        </div>
      </section>

      {/* Footer */}
      <footer className="bg-black py-8 border-t border-gray-900">
        <div className="max-w-7xl mx-auto px-4 text-center text-gray-600 text-sm">
          <p>© {new Date().getFullYear()} Neubofy. All rights reserved.</p>
          <p className="mt-2">Reality Engine is an open-source project managed by Pawan Washudev.</p>
        </div>
      </footer>
    </div>
  );
}
