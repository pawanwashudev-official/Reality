import React from 'react';
import Link from 'next/link';
import { Home } from 'lucide-react';

export default function RealityProActivationPage() {
  return (
    <div className="min-h-screen bg-gray-50 font-sans text-gray-900 pb-24">
      {/* Header */}
      <header className="bg-gray-900 text-white p-4 shadow-md sticky top-0 z-50">
        <div className="max-w-4xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link href="/" className="p-2 -ml-2 rounded-full hover:bg-gray-800 transition-colors">
              <Home size={20} />
            </Link>
            <h1 className="text-xl font-bold tracking-tight">Reality Pro</h1>
          </div>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-4 py-12 text-center">

        <div className="mb-8">
            <div className="w-24 h-24 bg-blue-100 rounded-full flex items-center justify-center mx-auto mb-6">
                <span className="text-4xl">🌟</span>
            </div>
            <h2 className="text-4xl font-extrabold tracking-tight text-gray-900 mb-4">
                Upgrade to Reality Pro
            </h2>
            <p className="text-lg text-gray-600 max-w-2xl mx-auto">
                Unlock the full power of Reality including the Nightly Protocol, Gamification (XP & Levels), Deep AI Chat Integrations, and Google Workspace Sync (Tasks, Calendar, Drive).
            </p>
        </div>

        <div className="bg-white rounded-3xl shadow-sm border border-gray-100 p-8 max-w-xl mx-auto mb-12">
            <h3 className="text-xl font-bold text-gray-900 mb-6">How to get an Activation Code</h3>

            <div className="space-y-6 text-left">
                <div className="flex gap-4">
                    <div className="w-8 h-8 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center font-bold flex-shrink-0">1</div>
                    <div>
                        <h4 className="font-bold text-gray-800">Support the Project</h4>
                        <p className="text-sm text-gray-600 mt-1">Reality Pro is a premium module to help support the continuous open-source development of the app.</p>
                    </div>
                </div>

                <div className="flex gap-4">
                    <div className="w-8 h-8 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center font-bold flex-shrink-0">2</div>
                    <div>
                        <h4 className="font-bold text-gray-800">Contact the Founder</h4>
                        <p className="text-sm text-gray-600 mt-1">Send an email to <strong>founder@neubofy.in</strong> with your User ID (found in the Profile page) to request an activation code.</p>
                    </div>
                </div>

                <div className="flex gap-4">
                    <div className="w-8 h-8 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center font-bold flex-shrink-0">3</div>
                    <div>
                        <h4 className="font-bold text-gray-800">Activate in App</h4>
                        <p className="text-sm text-gray-600 mt-1">Once you receive the code, open Reality, navigate to any Pro feature, and enter your code in the popup.</p>
                    </div>
                </div>
            </div>
        </div>

        <a href="mailto:founder@neubofy.in?subject=Reality%20Pro%20Activation%20Request" className="inline-flex items-center justify-center px-8 py-4 border border-transparent text-lg font-bold rounded-2xl text-white bg-blue-600 hover:bg-blue-700 transition-colors shadow-sm">
            Contact Us for a Code
        </a>

      </main>
    </div>
  );
}
