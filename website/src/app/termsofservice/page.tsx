import React from 'react';
import Link from 'next/link';
import { ArrowLeft, Scale, Lock, BookOpen, AlertTriangle, ShieldAlert } from 'lucide-react';

export default function TermsOfService() {
  return (
    <div className="min-h-screen bg-neural-bg font-outfit text-gray-300 py-16 px-4 sm:px-6 lg:px-8 selection:bg-neural-cyan selection:text-black">
      <div className="max-w-4xl mx-auto">
        
        {/* Back Link */}
        <Link href="/" className="inline-flex items-center gap-2 text-neural-cyan hover:text-white font-mono text-sm mb-8 transition-colors duration-200 group">
          <ArrowLeft size={16} className="transform group-hover:-translate-x-1 transition-transform" />
          <span>Return to Dashboard</span>
        </Link>

        {/* Terms Container */}
        <div className="bg-neural-card border border-gray-800 rounded-2xl p-8 sm:p-12 shadow-2xl relative overflow-hidden">
          <div className="absolute top-0 left-0 right-0 h-1.5 bg-gradient-to-r from-neural-cyan to-neural-purple"></div>
          
          {/* Header */}
          <div className="text-center mb-12 border-b border-gray-800 pb-8">
            <h1 className="text-4xl sm:text-5xl font-extrabold text-white tracking-tight mb-3">
              Terms of Service for <span className="text-transparent bg-clip-text bg-gradient-to-r from-neural-cyan to-neural-purple">Reality</span>
            </h1>
            <p className="text-gray-500 font-mono text-xs uppercase tracking-widest">Effective Date: May 19, 2030</p>
          </div>

          <div className="space-y-10 leading-relaxed text-gray-300">
            
            {/* Core Disclaimer */}
            <p className="text-lg text-gray-300 font-light border-l-2 border-neural-purple pl-4 italic">
              By downloading, building, or using Reality, you agree to be bound by these Terms of Service. If you do not agree to these terms, please do not use the application.
            </p>

            {/* License Warning Box */}
            <div className="bg-gradient-to-r from-neural-purple/5 to-neural-cyan/5 border border-neural-purple/20 p-6 rounded-xl flex items-start gap-4">
              <ShieldAlert className="text-neural-purple shrink-0 mt-1" size={24} />
              <div>
                <h3 className="text-md font-bold text-white mb-2">Source-Available License Restrictions</h3>
                <p className="text-xs text-gray-400 leading-relaxed">
                  Reality is source-available strictly for audit and security review purposes. You are prohibited from cloning, rebranding, custom-compiling, or distributing secondary versions of this application. pre-compiled binaries can only be shared in their original unmodified state from the official GitHub Release channel.
                </p>
              </div>
            </div>

            {/* Detailed sections */}
            <section className="space-y-4">
              <h2 className="text-2xl font-bold text-white flex items-center gap-2 border-b border-gray-800 pb-2">
                <BookOpen size={20} className="text-neural-cyan" /> 1. Acceptance & Description of Service
              </h2>
              <p className="text-sm text-gray-400">
                Reality is a focus-tracking and distraction blocker application running local-first on Android. It leverages on-device tools to restrict distraction access, log metrics, scale alarms, and execute synchronizations directly with your private cloud space.
              </p>
            </section>

            <section className="space-y-4">
              <h2 className="text-2xl font-bold text-white flex items-center gap-2 border-b border-gray-800 pb-2">
                <Lock size={20} className="text-neural-cyan" /> 2. System Permissions & Blockers
              </h2>
              <p className="text-sm text-gray-400">
                Certain core tools inside Reality interact deeply with your device configurations to prevent DND override behaviors:
              </p>
              <ul className="list-disc pl-6 space-y-2 text-sm text-gray-400">
                <li><strong className="text-white">Device Admin & Accessibility:</strong> Enforcing blockers requires enabling Android System services. You agree that enabling these is done voluntarily with full understanding of lockout parameters.</li>
                <li><strong className="text-white">Strict Lock Mode:</strong> When active, strict mode shuts down settings access, time setting overrides, and blocker disable prompts. You accept that Neubofy is not responsible if you lock yourself out of required settings or apps during active lock sessions.</li>
              </ul>
            </section>

            <section className="space-y-4">
              <h2 className="text-2xl font-bold text-white flex items-center gap-2 border-b border-gray-800 pb-2">
                <Scale size={20} className="text-neural-cyan" /> 3. Data Sync & Backup Responsibility
              </h2>
              <p className="text-sm text-gray-400">
                Reality has no server backends to store, retrieve, or recover your configurations. You are solely responsible for managing backups:
              </p>
              <ul className="list-disc pl-6 space-y-2 text-sm text-gray-400">
                <li>Your backup database is saved to your personal Google Drive account. If you lose your keys, delete the Google Drive folder, or uninstall the app without syncing, your database is permanently lost.</li>
                <li>Neubofy holds no liability for lost metrics, wiped journals, or alarm failures resulting from device crashes.</li>
              </ul>
            </section>

            <section className="space-y-4">
              <h2 className="text-2xl font-bold text-white flex items-center gap-2 border-b border-gray-800 pb-2">
                <AlertTriangle size={20} className="text-neural-cyan" /> 4. Disclaimers & Warranties
              </h2>
              <p className="text-sm text-gray-400">
                The App is provided &quot;AS IS&quot; and &quot;AS AVAILABLE&quot; without warranties of any kind. We do not warrant that the application will run error-free, uninterrupted on all Android API layers, or wake you up without fail.
              </p>
              <p className="text-sm text-gray-400">
                Authentication and database syncing rely on Google Cloud Services. We bear no liability for connectivity losses, API model updates, or endpoint blockages triggered by third-party platforms.
              </p>
            </section>

            <section className="space-y-4">
              <h2 className="text-2xl font-bold text-white border-b border-gray-800 pb-2">5. Limitations of Liability</h2>
              <p className="text-sm text-gray-400">
                To the maximum extent permitted by law, Neubofy and its developers shall not be liable for any special, incidental, direct, indirect, or consequential damages (including, but not limited to, damages for loss of profits, loss of focus data, or setting locks) arising out of the use or inability to use this software.
              </p>
            </section>

            <section className="space-y-4">
              <h2 className="text-2xl font-bold text-white border-b border-gray-800 pb-2">6. Changes to Terms</h2>
              <p className="text-sm text-gray-400">
                We reserve the right to modify these Terms of Service. Check the top of this document for active Effective Dates. Your continued usage after modifications constitutes agreement to the updated clauses.
              </p>
            </section>

            <section className="space-y-4">
              <h2 className="text-2xl font-bold text-white border-b border-gray-800 pb-2">7. Contact Information</h2>
              <p className="text-sm text-gray-400">
                For license auditing requests or inquiries: <a href="mailto:support@neubofy.in" className="text-neural-cyan hover:underline">support@neubofy.in</a>
              </p>
            </section>

          </div>
        </div>

      </div>
    </div>
  );
}
