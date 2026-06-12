import React from 'react';
import Link from 'next/link';

export default function PrivacyPolicy() {
  return (
    <div className="min-h-screen bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-3xl mx-auto">
        <Link href="/" className="inline-flex items-center text-blue-600 hover:text-blue-800 mb-8 transition-colors">
          <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" /></svg>
          Back to Home
        </Link>

        <div className="bg-white shadow-sm rounded-xl p-8 sm:p-12 border border-gray-100">
          <div className="text-center mb-10 border-b border-gray-100 pb-8">
            <h1 className="text-4xl font-extrabold text-gray-900 tracking-tight mb-2">Privacy Policy for Reality</h1>
            <p className="text-gray-500 font-medium">Effective Date: June 11, 2024</p>
          </div>

          <div className="prose prose-blue max-w-none text-gray-600">
            <p className="text-lg leading-relaxed mb-8">
              At Neubofy, we believe your data is yours. The Reality app was built with a fundamental &quot;Zero Trust&quot; architecture, meaning we do not want your data, we do not store your data, and we have no servers to send your data to.
            </p>

            <div className="bg-blue-50 border-l-4 border-blue-500 p-6 rounded-r-lg mb-10">
              <h3 className="text-xl font-bold text-blue-900 mb-3 mt-0">The TL;DR</h3>
              <p className="text-blue-800 font-medium m-0">
                We have no servers. We don&apos;t collect your data. Everything stays on your device or in your personal Google Drive. There are no ads, no trackers, and no analytics SDKs sending your behavior to third parties.
              </p>
            </div>

            <div className="space-y-10">
              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">1. Data Collection & Storage</h2>
                <p className="mb-4">Reality operates exclusively on your local device. All app state, usage statistics, XP calculations, and blocking logic run 100% on-device.</p>
                <ul className="list-disc pl-6 space-y-2">
                  <li><strong>Local-First:</strong> Data required for the app to function is stored locally on your device in secure app storage.</li>
                  <li><strong>No Backend Servers:</strong> Neubofy does not operate any backend databases or APIs to collect user data. Your information never touches our systems.</li>
                  <li><strong>Transient Storage:</strong> Detailed analytical data (like screen time statistics) lives locally for a maximum of 3 days before being wiped or synced exclusively to your personal Google Drive (if enabled).</li>
                </ul>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">2. Google Account Integration</h2>
                <p className="mb-4">Reality offers powerful integration with Google Workspace (Docs, Tasks, Calendar, Drive). To enable these features, you must manually authorize the app via OAuth (Bring Your Own Key).</p>
                <ul className="list-disc pl-6 space-y-2">
                  <li><strong>Your Cloud, Your Rules:</strong> If you enable Google Drive backup or Google Docs integration, your data (daily reports, AI plans, journals) is uploaded directly to <strong>your personal Google Drive</strong>. We cannot access, view, or modify these files.</li>
                  <li><strong>Scopes Used:</strong> Reality requests access to your Google Profile (email and name) solely for displaying it within the app&apos;s profile page. It may request access to Drive, Tasks, and Calendar strictly to create and manage files/tasks/events on your behalf as part of the app&apos;s core functionality.</li>
                </ul>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">3. Third-Party AI Services</h2>
                <p className="mb-4">Reality uses a &quot;Bring Your Own Key&quot; (BYOK) model for AI features.</p>
                <ul className="list-disc pl-6 space-y-2">
                  <li>When you use the AI Agent (for planning, reflections, etc.), prompts and relevant local context are sent directly from your device to the API provider you configure (e.g., OpenAI, Gemini, Groq, Claude, OpenRouter).</li>
                  <li>We do not proxy these requests. The privacy of your data during AI processing is governed by the respective AI provider you choose.</li>
                </ul>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">4. Advertising and Tracking</h2>
                <p>Reality contains <strong>zero advertisements</strong> and <strong>zero third-party tracking SDKs</strong>. We do not sell, rent, or share your data with advertisers or data brokers.</p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">5. Open Source Transparency</h2>
                <p>Reality is 100% open-source. Every line of code is publicly available on GitHub for audit. You can compile the application yourself to ensure no hidden data collection mechanisms exist.</p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">6. Changes to this Policy</h2>
                <p>If we update this privacy policy, the changes will be reflected on this page. Because we do not collect your email address, you must review this page periodically for updates.</p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">7. Contact Us</h2>
                <p>If you have any questions about this Privacy Policy, please contact us at: <a href="mailto:founder@neubofy.in" className="text-blue-600 font-medium hover:underline">founder@neubofy.in</a></p>
              </section>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}