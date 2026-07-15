import React from 'react';
import Link from 'next/link';
import { ArrowLeft, Shield, Lock, EyeOff, ServerCrash, KeyRound, Activity } from 'lucide-react';

export default function PrivacyPolicy() {
  return (
    <div className="min-h-screen bg-neural-bg font-outfit text-gray-300 py-16 px-4 sm:px-6 lg:px-8 selection:bg-neural-cyan selection:text-black">
      <div className="max-w-4xl mx-auto">
        
        {/* Back Link */}
        <Link href="/" className="inline-flex items-center gap-2 text-neural-cyan hover:text-white font-mono text-sm mb-8 transition-colors duration-200 group">
          <ArrowLeft size={16} className="transform group-hover:-translate-x-1 transition-transform" />
          <span>Return to Dashboard</span>
        </Link>

        {/* Policy Container */}
        <div className="bg-neural-card border border-gray-800 rounded-2xl p-8 sm:p-12 shadow-2xl relative overflow-hidden">
          <div className="absolute top-0 left-0 right-0 h-1.5 bg-gradient-to-r from-neural-cyan to-neural-purple"></div>
          
          {/* Header */}
          <div className="text-center mb-12 border-b border-gray-800 pb-8">
            <h1 className="text-4xl sm:text-5xl font-extrabold text-white tracking-tight mb-3">
              Privacy Policy for <span className="text-transparent bg-clip-text bg-gradient-to-r from-neural-cyan to-neural-purple">Reality</span>
            </h1>
            <p className="text-gray-500 font-mono text-xs uppercase tracking-widest">Effective Date: May 19, 2030</p>
          </div>

          <div className="space-y-10 leading-relaxed text-gray-300">
            
            {/* Core Message */}
            <p className="text-lg text-gray-300 font-light border-l-2 border-neural-cyan pl-4 italic">
              At Neubofy, we believe your focus metrics, calendars, and thoughts are strictly private. Reality is built with a local-first, zero-trust architecture. We run zero developer backend servers, store no personal user profiles, and never intercept your inputs.
            </p>

            {/* TL;DR Highlight Card */}
            <div className="bg-gradient-to-r from-neural-cyan/5 to-neural-purple/5 border border-neural-cyan/20 p-6 rounded-xl flex items-start gap-4">
              <Shield className="text-neural-cyan shrink-0 mt-1" size={24} />
              <div>
                <h3 className="text-md font-bold text-white mb-2">The Absolute Bottom Line</h3>
                <p className="text-xs text-gray-400 leading-relaxed">
                  No personal data is collected or transmitted to Neubofy. Your tasks, journals, alarms, and reflections remain stored on-device inside your private Android Room database or uploaded straight to your personal Google Drive account. We do not sell data, use ads, or intercept keys.
                </p>
              </div>
            </div>

            {/* Detailed sections */}
            <section className="space-y-4">
              <h2 className="text-2xl font-bold text-white flex items-center gap-2 border-b border-gray-800 pb-2">
                <ServerCrash size={20} className="text-neural-cyan" /> 1. Data Isolation & Local Storage
              </h2>
              <p className="text-sm text-gray-400">
                Reality preserves offline integrity. All variables required to run your productivity dashboard reside locally:
              </p>
              <ul className="list-disc pl-6 space-y-2 text-sm text-gray-400">
                <li><strong className="text-white">Room SQLite Database:</strong> Stores focus durations, strict lock limits, XP level states, and alarms locally.</li>
                <li><strong className="text-white">On-Device Encryption:</strong> Key config states and token parameters are locked inside Android&apos;s native <code>EncryptedSharedPreferences</code> container.</li>
                <li><strong className="text-white">Transient Screen Logging:</strong> Real-time screen metrics are cached in memory for up to 3 days to feed stats charts, after which they are deleted.</li>
              </ul>
            </section>

            <section className="space-y-4">
              <h2 className="text-2xl font-bold text-white flex items-center gap-2 border-b border-gray-800 pb-2">
                <KeyRound size={20} className="text-neural-cyan" /> 2. Personal Google Integration & OAuth
              </h2>
              <p className="text-sm text-gray-400">
                Reality integrates with Google Calendar, Tasks, Docs, and Drive using a client-side model. No intermediate servers handle these handshakes:
              </p>
              <ul className="list-disc pl-6 space-y-2 text-sm text-gray-400">
                <li><strong className="text-white">Direct Client Authentication:</strong> The app utilizes credentials you configure in your own Google Cloud project. It requests tokens straight from Google using an on-device socket redirect listener.</li>
                <li><strong className="text-white">Drive & Docs Storage:</strong> Document logs, backup files, and journals are created inside your personal Google Drive space. Neubofy does not have access credentials to view or read these assets.</li>
                <li><strong className="text-white">Permission Scope:</strong> Google Profile scopes (name, profile picture, email address) are retrieved strictly to map active synchronization identities inside your local profile console.</li>
              </ul>
            </section>

            <section className="space-y-4">
              <h2 className="text-2xl font-bold text-white flex items-center gap-2 border-b border-gray-800 pb-2">
                <EyeOff size={20} className="text-neural-cyan" /> 3. JIT Cryptography & Edge Workers
              </h2>
              <p className="text-sm text-gray-400">
                To prevent physical data extractions or credential modifications from the client device:
              </p>
              <ul className="list-disc pl-6 space-y-2 text-sm text-gray-400">
                <li>HMAC-SHA256 calculations are run Just-In-Time on Cloudflare Workers edge nodes.</li>
                <li>These edge servers only compute deterministic cryptographic hash keys using secret peppers and transient logins. No database or configuration metrics are logged or kept on edge node filesystems.</li>
              </ul>
            </section>

            <section className="space-y-4">
              <h2 className="text-2xl font-bold text-white flex items-center gap-2 border-b border-gray-800 pb-2">
                <Lock size={20} className="text-neural-cyan" /> 4. Accessibility API & Permissions
              </h2>
              <p className="text-sm text-gray-400">
                Reality leverages specific Android system controls to enforce blocks and prevent bypass loops:
              </p>
              <ul className="list-disc pl-6 space-y-2 text-sm text-gray-400">
                <li><strong className="text-white">Accessibility Service:</strong> Used to read package change callbacks and block active applications. No inputs, keys, or background messages are tracked or parsed.</li>
                <li><strong className="text-white">Device Administrator:</strong> Utilized strictly to prevent unauthorized app uninstalls during active DND focus hours.</li>
              </ul>
            </section>

            <section className="space-y-4">
              <h2 className="text-2xl font-bold text-white flex items-center gap-2 border-b border-gray-800 pb-2">
                <Activity size={20} className="text-neural-cyan" /> 5. Third-Party Diagnostics
              </h2>
              <p className="text-sm text-gray-400">
                Reality employs Firebase strictly for debugging and message routing:
              </p>
              <ul className="list-disc pl-6 space-y-2 text-sm text-gray-400">
                <li><strong className="text-white">Stability & Performance:</strong> Anonymous usage trends (crashes, session lengths) are tracked to monitor bug outbreaks.</li>
                <li><strong className="text-white">Push Notifications (FCM):</strong> Firebase Cloud Messaging handles silent background triggers to synchronize calendar data changes. No payload content is parsed.</li>
              </ul>
            </section>

            <section className="space-y-4">
              <h2 className="text-2xl font-bold text-white border-b border-gray-800 pb-2">6. Source-Available Integrity</h2>
              <p className="text-sm text-gray-400">
                Reality is a source-available codebase. Every line of implementation is open for public audit on GitHub. We provide this transparency so you can verify that no backend relays or telemetry tracking is added.
              </p>
            </section>

            <section className="space-y-4">
              <h2 className="text-2xl font-bold text-white border-b border-gray-800 pb-2">7. Changes to this Policy</h2>
              <p className="text-sm text-gray-400">
                As Reality runs local-first without cataloging your identity, we cannot send out email updates. Any changes will be updated directly on this page. We encourage you to review this policy periodically.
              </p>
            </section>

            <section className="space-y-4">
              <h2 className="text-2xl font-bold text-white border-b border-gray-800 pb-2">8. Contact Us</h2>
              <p className="text-sm text-gray-400">
                For security audits or privacy queries, reach out directly: <a href="mailto:support@neubofy.in" className="text-neural-cyan hover:underline">support@neubofy.in</a>
              </p>
            </section>

          </div>
        </div>

      </div>
    </div>
  );
}
