import React from 'react';
import Link from 'next/link';

export default function Home() {
  return (
    <div className="min-h-screen bg-gray-50 font-sans text-gray-900">
      {/* Hero Section */}
      <section className="bg-white border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-24 text-center">
          <div className="mb-8 flex justify-center">
            <img
              src="https://res.cloudinary.com/dnh4fonis/image/upload/v1781091079/ck39669alz53z3vkeiaq.png"
              alt="Reality Logo"
              className="w-32 h-32 rounded-3xl shadow-lg border border-gray-100"
            />
          </div>
          <h1 className="text-5xl md:text-6xl font-extrabold tracking-tight text-gray-900 mb-4">
            Reality
          </h1>
          <h2 className="text-2xl md:text-3xl font-medium text-gray-600 mb-6">
            The Intelligent Life OS
          </h2>
          <p className="max-w-2xl mx-auto text-xl text-gray-500 mb-10 italic">
            &quot;Stop managing your life. Start commanding it.&quot;
          </p>

          <div className="flex flex-wrap justify-center gap-3 mb-12">
            <span className="px-4 py-1.5 rounded-full text-sm font-semibold bg-green-100 text-green-800">Platform: Android</span>
            <span className="px-4 py-1.5 rounded-full text-sm font-semibold bg-teal-100 text-teal-800">Data: Local + G-Drive</span>
            <span className="px-4 py-1.5 rounded-full text-sm font-semibold bg-purple-100 text-purple-800">AI: BYO Key</span>
            <span className="px-4 py-1.5 rounded-full text-sm font-semibold bg-red-100 text-red-800">Ads: ZERO</span>
          </div>

          <div className="flex flex-col items-center gap-4">
            <p className="font-semibold text-gray-700 text-lg">
              🌟 100% Open Source • No Ads • No Trackers • Any AI
            </p>
            <div className="flex flex-col sm:flex-row gap-4 mt-6">
              <a
                href="https://github.com/pawanwashudev-official/Reality/releases"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center justify-center px-8 py-3 border border-transparent text-base font-medium rounded-xl text-white bg-blue-600 hover:bg-blue-700 md:py-4 md:text-lg transition-colors shadow-sm"
              >
                ⬇️ Download APK
              </a>
              <a
                href="https://reality-digital-wellbeing-and-focus.en.uptodown.com/android"
                title="Download Reality - The Intelligent Life OS"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-block transition-transform hover:scale-105"
              >
                <img
                  src="https://stc.utdstc.com/img/mediakit/download-aao-big.png"
                  alt="Download Reality - The Intelligent Life OS"
                  className="h-14 object-contain"
                />
              </a>
              <Link
                href="/tapashya"
                className="inline-flex items-center justify-center px-8 py-3 border border-transparent text-base font-medium rounded-xl text-white bg-[#00695C] hover:bg-[#004D40] md:py-4 md:text-lg transition-colors shadow-sm"
              >
                ⏳ Try Tapashya Web
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Why Reality Section */}
      <section className="py-20 bg-gray-50">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-16">
            <h2 className="text-3xl font-bold text-gray-900 mb-4">🤔 Why Does Reality Exist?</h2>
            <p className="text-lg text-gray-600">We searched for the perfect productivity app. We found:</p>
          </div>

          <div className="grid sm:grid-cols-2 gap-6 mb-12">
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
              <div className="text-red-500 text-2xl mb-2">❌ Paid Apps</div>
              <p className="text-gray-600">Want $50/year just to block Instagram? No thanks.</p>
            </div>
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
              <div className="text-red-500 text-2xl mb-2">❌ &quot;Free&quot; Apps</div>
              <p className="text-gray-600">Selling your data to advertisers. You&apos;re the product.</p>
            </div>
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
              <div className="text-red-500 text-2xl mb-2">❌ One-Trick Apps</div>
              <p className="text-gray-600">A timer here. A to-do list there. No integration.</p>
            </div>
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
              <div className="text-red-500 text-2xl mb-2">❌ Closed Ecosystems</div>
              <p className="text-gray-600">Your data locked in proprietary servers.</p>
            </div>
          </div>

          <div className="bg-blue-50 p-8 rounded-2xl border border-blue-100 text-center">
            <p className="text-lg text-blue-900 font-medium leading-relaxed">
              <strong>Reality was born from frustration.</strong> Built by two friends who lost control of their own fingers. Designed for students and working professionals who want to use their phone <strong>for getting things done</strong>, not doom-scrolling.
            </p>
          </div>
        </div>
      </section>

      {/* Google Integration Section */}
      <section className="py-20 bg-white border-t border-gray-200">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-16">
            <h2 className="text-3xl font-bold text-gray-900 mb-4">💡 The Google Ecosystem Advantage</h2>
            <p className="text-lg text-gray-600">Reality doesn&apos;t reinvent the wheel. It weaponizes Google&apos;s own tools for your productivity.</p>
          </div>

          <div className="grid md:grid-cols-3 gap-8">
            <div className="bg-gray-50 p-8 rounded-2xl border border-gray-100">
              <h3 className="text-xl font-bold text-gray-900 mb-4 flex items-center">
                <span className="text-2xl mr-2">📄</span> Docs
              </h3>
              <ul className="space-y-3 text-gray-600">
                <li><strong>AI Diary:</strong> Your journal written directly to Google Docs.</li>
                <li><strong>AI Plans:</strong> Tomorrow&apos;s plan as a structured Drive document.</li>
              </ul>
            </div>
            <div className="bg-gray-50 p-8 rounded-2xl border border-gray-100">
              <h3 className="text-xl font-bold text-gray-900 mb-4 flex items-center">
                <span className="text-2xl mr-2">✅</span> Tasks & Calendar
              </h3>
              <ul className="space-y-3 text-gray-600">
                <li><strong>Native Sync:</strong> Tasks appear in Google Tasks instantly.</li>
                <li><strong>Schedule XP:</strong> Earn XP for attending calendar blocks.</li>
              </ul>
            </div>
            <div className="bg-gray-50 p-8 rounded-2xl border border-gray-100">
              <h3 className="text-xl font-bold text-gray-900 mb-4 flex items-center">
                <span className="text-2xl mr-2">☁️</span> Drive Backup
              </h3>
              <ul className="space-y-3 text-gray-600">
                <li><strong>Auto Reports:</strong> PDFs uploaded to your personal folder.</li>
                <li><strong>Zero Servers:</strong> Your data never touches our systems.</li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* Architecture Section */}
      <section className="py-20 bg-gray-900 text-white">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <h2 className="text-3xl font-bold mb-12">🔐 Privacy Architecture: Zero Trust</h2>
          <div className="grid sm:grid-cols-2 gap-8 text-left">
            <div>
              <h4 className="text-xl font-bold mb-2 text-blue-400">No Servers</h4>
              <p className="text-gray-300">We have zero backend infrastructure. Your data never touches our systems.</p>
            </div>
            <div>
              <h4 className="text-xl font-bold mb-2 text-blue-400">Local-First</h4>
              <p className="text-gray-300">All blocking logic, XP calculation, and app state runs 100% on-device.</p>
            </div>
            <div>
              <h4 className="text-xl font-bold mb-2 text-blue-400">Your Cloud, Your Rules</h4>
              <p className="text-gray-300">Backups go to your personal Google Drive. We can&apos;t access it.</p>
            </div>
            <div>
              <h4 className="text-xl font-bold mb-2 text-blue-400">Open Source</h4>
              <p className="text-gray-300">Every line of code is public. Build the APK yourself if you don&apos;t trust us.</p>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
