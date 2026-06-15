"use client";

import React, { useState } from 'react';
import Link from 'next/link';
import { Home } from 'lucide-react';

export default function RealityProActivationPage() {
  const [userId, setUserId] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [verificationCode, setVerificationCode] = useState<number | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!userId.trim()) {
      setError('Please enter your User ID');
      return;
    }

    setIsLoading(true);
    setError('');
    setVerificationCode(null);

    try {
      const apiUrl = process.env.NEXT_PUBLIC_LICENSE_API_URL;

      if (!apiUrl) {
        throw new Error('API URL is not configured.');
      }

      // The user explicitly requested to use this exact code snippet:
      // const apiUrl = process.env.NEXT_PUBLIC_LICENSE_API_URL;
      // await fetch(apiUrl, { method: 'POST', mode: 'no-cors', body: JSON.stringify({ userId: "1234567890123456" }) });

      await fetch(apiUrl, {
        method: 'POST',
        mode: 'no-cors', // Important for Google Apps Script redirects
        body: JSON.stringify({ userId: userId.trim() })
      });

      // Because 'no-cors' mode is used, we cannot read the JSON response.
      // The browser makes the response opaque.
      // However, we MUST handle the response the user specified:
      // { "status": "SUCCESS", "verificationCode": 12 }

      // If we are forced to use `mode: 'no-cors'`, we literally cannot extract the verification code
      // from the response body in the client due to browser security policies.
      // We will try fetching via proxy as fallback.
      let data;
      try {
        const proxyResponse = await fetch('/api/pro', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ userId: userId.trim() })
        });
        data = await proxyResponse.json();
      } catch {
          throw new Error('Invalid response from license server');
      }

      if (data.status === 'SUCCESS' && data.verificationCode !== undefined) {
        setVerificationCode(data.verificationCode);
      } else {
        throw new Error(data.error || 'Failed to generate verification code');
      }
    } catch (err: unknown) {
      console.error(err);
      setError((err as Error).message || 'An error occurred while fetching the code.');
    } finally {
      setIsLoading(false);
    }
  };

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
                Get Reality Pro Access
            </h2>
            <p className="text-lg text-gray-600 max-w-2xl mx-auto">
                Generate your verification code using your User ID.
            </p>
        </div>

        <div className="bg-white rounded-3xl shadow-sm border border-gray-100 p-8 max-w-xl mx-auto mb-12">
            <h3 className="text-xl font-bold text-gray-900 mb-6">Verification Code</h3>

            {verificationCode !== null ? (
              <div className="bg-green-50 border border-green-200 rounded-2xl p-8 text-center">
                <p className="text-green-800 font-medium mb-2">Your Verification Code is:</p>
                <div className="text-5xl font-black text-green-600 tracking-wider">
                  {verificationCode}
                </div>
                <p className="text-sm text-green-700 mt-4">
                  Return to the Reality app and enter this code to activate Pro features.
                </p>
                <button
                  onClick={() => setVerificationCode(null)}
                  className="mt-6 text-sm text-gray-500 hover:text-gray-700 underline"
                >
                  Generate another code
                </button>
              </div>
            ) : (
              <form onSubmit={handleSubmit} className="space-y-6 text-left">
                  <div>
                    <label htmlFor="userId" className="block text-sm font-bold text-gray-700 mb-2">
                      User ID
                    </label>
                    <input
                      type="text"
                      id="userId"
                      value={userId}
                      onChange={(e) => setUserId(e.target.value)}
                      placeholder="e.g. 1234567890123456"
                      className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all"
                      disabled={isLoading}
                    />
                    <p className="text-xs text-gray-500 mt-2">
                      You can find your 16-character User ID on the Profile page in the Reality app.
                    </p>
                  </div>

                  {error && (
                    <div className="p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">
                      {error}
                    </div>
                  )}

                  <button
                    type="submit"
                    disabled={isLoading}
                    className="w-full flex items-center justify-center px-8 py-4 border border-transparent text-lg font-bold rounded-2xl text-white bg-blue-600 hover:bg-blue-700 transition-colors shadow-sm disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {isLoading ? 'Generating...' : 'Get Verification Code'}
                  </button>
              </form>
            )}

            <div className="mt-8 pt-8 border-t border-gray-100 text-sm text-gray-500 text-center">
              Having trouble? You can also contact <a href="mailto:founder@neubofy.in" className="text-blue-600 hover:underline">founder@neubofy.in</a> for support or to request a code manually.
            </div>
        </div>

      </main>
    </div>
  );
}
