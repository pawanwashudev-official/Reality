'use client';

import React, { useState } from 'react';
import { Lock, LogIn } from 'lucide-react';
import { loginAction } from './actions';

export default function LoginClient({ onAuthenticated }: { onAuthenticated: () => void }) {
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    const formData = new FormData(e.currentTarget);
    const result = await loginAction(formData);

    if (result.success) {
      onAuthenticated();
    } else {
      setError(result.error || 'Authentication failed');
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col items-center justify-center mt-20">
      <div className="w-16 h-16 rounded-full bg-neural-card border border-gray-800 flex items-center justify-center mb-6 shadow-xl">
        <Lock className="text-neural-cyan" size={32} />
      </div>
      <h1 className="text-3xl font-extrabold text-white mb-2 tracking-tight">Admin Gateway</h1>
      <p className="text-gray-400 font-mono text-sm mb-8">Secure restricted access area</p>

      <form onSubmit={handleSubmit} className="w-full max-w-sm bg-neural-card/30 border border-gray-800 p-6 rounded-2xl backdrop-blur-sm space-y-4">
        <div>
          <label htmlFor="username" className="sr-only">Username</label>
          <input
            id="username"
            name="username"
            type="text"
            required
            autoComplete="username"
            placeholder="Username"
            className="w-full bg-black/40 border border-gray-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-neural-cyan focus:ring-1 focus:ring-neural-cyan transition-all"
          />
        </div>
        <div>
          <label htmlFor="password" className="sr-only">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            required
            autoComplete="current-password"
            placeholder="Password"
            className="w-full bg-black/40 border border-gray-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-neural-cyan focus:ring-1 focus:ring-neural-cyan transition-all"
          />
        </div>

        {error && (
          <div className="bg-red-500/10 border border-red-500/30 text-red-400 text-sm font-mono p-3 rounded-lg text-center">
            {error}
          </div>
        )}

        <button
          type="submit"
          disabled={loading}
          className="w-full flex items-center justify-center gap-2 bg-white text-black font-bold py-3 rounded-xl hover:bg-gray-200 transition-all disabled:opacity-50"
        >
          {loading ? (
            <div className="w-5 h-5 rounded-full border-2 border-black border-t-transparent animate-spin" />
          ) : (
            <>
              <LogIn size={18} />
              Authenticate
            </>
          )}
        </button>
      </form>
    </div>
  );
}
