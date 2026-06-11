/* eslint-disable react/no-unescaped-entities */
import Link from "next/link";
import { ArrowLeft, ShieldCheck, Database, Cloud, Bot, EyeOff, Code } from "lucide-react";

export default function PrivacyPolicy() {
  return (
    <div className="min-h-screen bg-neutral-50 text-neutral-900 py-12 px-4">
      <div className="container mx-auto max-w-3xl">
        <Link href="/" className="inline-flex items-center text-blue-600 hover:text-blue-800 font-medium mb-8 transition-colors">
          <ArrowLeft className="w-4 h-4 mr-2" /> Back to Home
        </Link>

        <div className="bg-white rounded-3xl p-8 md:p-12 shadow-sm border border-neutral-200">
          <div className="text-center mb-10 border-b border-neutral-100 pb-10">
            <h1 className="text-4xl md:text-5xl font-extrabold tracking-tight mb-4 text-neutral-900">Privacy Policy</h1>
            <p className="text-neutral-500 font-medium">Effective Date: June 11, 2024</p>
          </div>

          <div className="prose prose-lg prose-neutral max-w-none">
            <p className="text-xl text-neutral-600 leading-relaxed mb-8">
              At Neubofy, we believe your data is yours. The Reality app was built with a fundamental "Zero Trust" architecture, meaning we do not want your data, we do not store your data, and we have no servers to send your data to.
            </p>

            <div className="bg-blue-50 border-l-4 border-blue-600 p-6 rounded-r-xl mb-12">
              <h3 className="flex items-center text-xl font-bold text-blue-900 mt-0 mb-3">
                <ShieldCheck className="w-6 h-6 mr-2 text-blue-600" /> The TL;DR
              </h3>
              <p className="text-blue-800 m-0 font-medium">
                We have no servers. We don't collect your data. Everything stays on your device or in your personal Google Drive. There are no ads, no trackers, and no analytics SDKs sending your behavior to third parties.
              </p>
            </div>

            <div className="space-y-12">
              <section>
                <h2 className="flex items-center text-2xl font-bold text-neutral-900 mb-4 pb-2 border-b border-neutral-100">
                  <Database className="w-6 h-6 mr-3 text-neutral-400" /> 1. Data Collection & Storage
                </h2>
                <p>Reality operates exclusively on your local device. All app state, usage statistics, XP calculations, and blocking logic run 100% on-device.</p>
                <ul className="list-disc pl-6 space-y-2 text-neutral-700">
                  <li><strong>Local-First:</strong> Data required for the app to function is stored locally on your device in secure app storage.</li>
                  <li><strong>No Backend Servers:</strong> Neubofy does not operate any backend databases or APIs to collect user data. Your information never touches our systems.</li>
                  <li><strong>Transient Storage:</strong> Detailed analytical data (like screen time statistics) lives locally for a maximum of 3 days before being wiped or synced exclusively to your personal Google Drive (if enabled).</li>
                </ul>
              </section>

              <section>
                <h2 className="flex items-center text-2xl font-bold text-neutral-900 mb-4 pb-2 border-b border-neutral-100">
                  <Cloud className="w-6 h-6 mr-3 text-neutral-400" /> 2. Google Account Integration
                </h2>
                <p>Reality offers powerful integration with Google Workspace (Docs, Tasks, Calendar, Drive). To enable these features, you must manually authorize the app via OAuth (Bring Your Own Key).</p>
                <ul className="list-disc pl-6 space-y-2 text-neutral-700">
                  <li><strong>Your Cloud, Your Rules:</strong> If you enable Google Drive backup or Google Docs integration, your data (daily reports, AI plans, journals) is uploaded directly to <strong>your personal Google Drive</strong>. We cannot access, view, or modify these files.</li>
                  <li><strong>Scopes Used:</strong> Reality requests access to your Google Profile (email and name) solely for displaying it within the app's profile page. It may request access to Drive, Tasks, and Calendar strictly to create and manage files/tasks/events on your behalf as part of the app's core functionality.</li>
                </ul>
              </section>

              <section>
                <h2 className="flex items-center text-2xl font-bold text-neutral-900 mb-4 pb-2 border-b border-neutral-100">
                  <Bot className="w-6 h-6 mr-3 text-neutral-400" /> 3. Third-Party AI Services
                </h2>
                <p>Reality uses a "Bring Your Own Key" (BYOK) model for AI features.</p>
                <ul className="list-disc pl-6 space-y-2 text-neutral-700">
                  <li>When you use the AI Agent (for planning, reflections, etc.), prompts and relevant local context are sent directly from your device to the API provider you configure (e.g., OpenAI, Gemini, Groq, Claude, OpenRouter).</li>
                  <li>We do not proxy these requests. The privacy of your data during AI processing is governed by the respective AI provider you choose.</li>
                </ul>
              </section>

              <section>
                <h2 className="flex items-center text-2xl font-bold text-neutral-900 mb-4 pb-2 border-b border-neutral-100">
                  <EyeOff className="w-6 h-6 mr-3 text-neutral-400" /> 4. Advertising and Tracking
                </h2>
                <p>Reality contains <strong>zero advertisements</strong> and <strong>zero third-party tracking SDKs</strong>. We do not sell, rent, or share your data with advertisers or data brokers.</p>
              </section>

              <section>
                <h2 className="flex items-center text-2xl font-bold text-neutral-900 mb-4 pb-2 border-b border-neutral-100">
                  <Code className="w-6 h-6 mr-3 text-neutral-400" /> 5. Open Source Transparency
                </h2>
                <p>Reality is 100% open-source. Every line of code is publicly available on GitHub for audit. You can compile the application yourself to ensure no hidden data collection mechanisms exist.</p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-neutral-900 mb-4 pb-2 border-b border-neutral-100">6. Changes to this Policy</h2>
                <p>If we update this privacy policy, the changes will be reflected on this page. Because we do not collect your email address, you must review this page periodically for updates.</p>
              </section>

              <section className="bg-neutral-50 p-6 rounded-xl border border-neutral-200">
                <h2 className="text-2xl font-bold text-neutral-900 mt-0 mb-3">7. Contact Us</h2>
                <p className="m-0">If you have any questions about this Privacy Policy, please contact us at: <a href="mailto:founder@neubofy.in" className="text-blue-600 font-bold hover:underline">founder@neubofy.in</a></p>
              </section>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
