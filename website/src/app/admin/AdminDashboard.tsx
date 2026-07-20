'use client';

import React, { useState } from 'react';
import { Send, AlertCircle, CheckCircle2 } from 'lucide-react';

export default function AdminDashboard() {
  const [workerUrl, setWorkerUrl] = useState('https://reality.neubofy.in/api/send-notification');
  const [secret, setSecret] = useState('');
  const [userId, setUserId] = useState('');
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');
  const [status, setStatus] = useState<{ type: 'idle' | 'loading' | 'success' | 'error'; msg: string }>({
    type: 'idle',
    msg: '',
  });

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    setStatus({ type: 'loading', msg: 'Sending notification...' });

    try {
      const res = await fetch(workerUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          notificationSecret: secret,
          userId,
          title,
          message,
        }),
      });

      const data = await res.json();

      if (res.ok && data.success) {
        setStatus({ type: 'success', msg: 'Notification sent successfully!' });
        setTitle('');
        setMessage('');
      } else {
        setStatus({ type: 'error', msg: data.error || 'Failed to send notification' });
      }
    } catch (err: any) {
      setStatus({ type: 'error', msg: err.message || 'Network error occurred' });
    }
  };

  return (
    <div className="w-full max-w-2xl mx-auto bg-neural-card/50 border border-gray-800 p-8 rounded-2xl shadow-xl backdrop-blur-sm mt-8">
      <div className="mb-8">
        <h2 className="text-2xl font-bold text-white mb-2">Send Push Notification</h2>
        <p className="text-gray-400 font-mono text-sm">
          Execute a direct POST request from your local browser to the Cloudflare Worker.
        </p>
      </div>

      <form onSubmit={handleSend} className="space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="space-y-2">
            <label htmlFor="workerUrl" className="text-sm font-medium text-gray-300 font-mono">Worker URL</label>
            <input
              id="workerUrl"
              name="workerUrl"
              type="url"
              required
              value={workerUrl}
              onChange={(e) => setWorkerUrl(e.target.value)}
              className="w-full bg-black/40 border border-gray-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-neural-cyan focus:ring-1 focus:ring-neural-cyan transition-all"
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="notificationSecret" className="text-sm font-medium text-gray-300 font-mono">Notification Secret</label>
            <input
              id="notificationSecret"
              name="notificationSecret"
              type="password"
              required
              autoComplete="off"
              value={secret}
              onChange={(e) => setSecret(e.target.value)}
              className="w-full bg-black/40 border border-gray-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-neural-cyan focus:ring-1 focus:ring-neural-cyan transition-all"
            />
          </div>
        </div>

        <div className="space-y-2">
          <label htmlFor="targetUserId" className="text-sm font-medium text-gray-300 font-mono">Target User ID</label>
          <input
            id="targetUserId"
            name="targetUserId"
            type="text"
            required
            autoComplete="off"
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            placeholder="e.g. 550e8400-e29b-41d4-a716-446655440000"
            className="w-full bg-black/40 border border-gray-700 rounded-lg px-4 py-3 text-white font-mono focus:outline-none focus:border-neural-cyan focus:ring-1 focus:ring-neural-cyan transition-all"
          />
        </div>

        <div className="space-y-2">
          <label htmlFor="notificationTitle" className="text-sm font-medium text-gray-300 font-mono">Notification Title</label>
          <input
            id="notificationTitle"
            name="notificationTitle"
            type="text"
            required
            autoComplete="off"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Reality Update"
            className="w-full bg-black/40 border border-gray-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-neural-cyan focus:ring-1 focus:ring-neural-cyan transition-all"
          />
        </div>

        <div className="space-y-2">
          <label htmlFor="notificationMessage" className="text-sm font-medium text-gray-300 font-mono">Notification Message</label>
          <textarea
            id="notificationMessage"
            name="notificationMessage"
            required
            autoComplete="off"
            rows={4}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder="Your custom report is now ready to view."
            className="w-full bg-black/40 border border-gray-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-neural-cyan focus:ring-1 focus:ring-neural-cyan transition-all resize-none"
          />
        </div>

        {status.type !== 'idle' && (
          <div
            className={`flex items-center gap-3 p-4 rounded-lg font-mono text-sm border ${
              status.type === 'error'
                ? 'bg-red-500/10 border-red-500/30 text-red-400'
                : status.type === 'success'
                ? 'bg-green-500/10 border-green-500/30 text-green-400'
                : 'bg-yellow-500/10 border-yellow-500/30 text-yellow-400'
            }`}
          >
            {status.type === 'error' ? (
              <AlertCircle size={18} />
            ) : status.type === 'success' ? (
              <CheckCircle2 size={18} />
            ) : (
              <div className="w-4 h-4 rounded-full border-2 border-yellow-500 border-t-transparent animate-spin" />
            )}
            {status.msg}
          </div>
        )}

        <button
          type="submit"
          disabled={status.type === 'loading'}
          className="w-full flex items-center justify-center gap-2 bg-neural-cyan text-black font-bold py-4 rounded-xl hover:bg-neural-cyan/90 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <Send size={20} />
          {status.type === 'loading' ? 'Dispatching...' : 'Dispatch Notification'}
        </button>
      </form>
    </div>
  );
}
