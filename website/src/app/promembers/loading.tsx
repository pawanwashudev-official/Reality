import React from 'react';
import { Crown, Database } from 'lucide-react';

export default function Loading() {
  return (
    <div className="min-h-screen bg-neural-bg font-outfit text-gray-100">
      {/* Header Skeleton */}
      <section className="relative overflow-hidden border-b border-gray-800 pb-12 pt-24">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-yellow-500/10 via-neural-bg to-neural-bg opacity-50 z-0"></div>
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center relative z-10">
          <div className="inline-flex items-center gap-2 mb-4 px-3 py-1 rounded-full border border-yellow-500/30 bg-yellow-500/10 text-yellow-500 text-xs font-mono tracking-widest uppercase opacity-70">
             <Crown size={14} />
             <span>ELITE TIER</span>
          </div>
          <h1 className="text-4xl md:text-6xl font-extrabold tracking-tight text-white mb-4 drop-shadow-md flex justify-center items-center gap-4">
            Pro Members
          </h1>
          <p className="max-w-2xl mx-auto text-lg text-gray-400 mb-8 font-mono">
            Loading directory...
          </p>

          <div className="flex justify-center gap-6">
            <div className="flex flex-col items-center p-4 bg-neural-card/50 border border-gray-800 rounded-xl min-w-[120px] animate-pulse">
              <Database className="text-neural-cyan mb-2" size={24} />
              <div className="h-8 w-16 bg-gray-700 rounded mb-1 mt-1"></div>
              <span className="text-xs text-gray-500 font-mono uppercase mt-1">Total Active</span>
            </div>
          </div>
        </div>
      </section>

      {/* Control Bar Skeleton */}
      <section className="py-6 border-b border-gray-800 bg-neural-card/30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex flex-col sm:flex-row justify-between items-center gap-4 animate-pulse">
                <div className="h-10 w-full sm:w-64 bg-gray-800 rounded-xl"></div>
                <div className="h-10 w-full sm:w-48 bg-gray-800 rounded-xl"></div>
            </div>
        </div>
      </section>

      {/* Grid Skeleton */}
      <section className="py-16 relative z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {[...Array(12)].map((_, i) => (
              <div key={i} className="bg-neural-card border border-gray-800 p-6 rounded-2xl shadow-lg animate-pulse">
                <div className="flex items-center gap-4 mb-4">
                  <div className="w-12 h-12 rounded-xl bg-gray-800"></div>
                  <div className="space-y-2">
                    <div className="h-4 w-20 bg-gray-800 rounded"></div>
                    <div className="h-5 w-32 bg-gray-700 rounded"></div>
                  </div>
                </div>
                <div className="pt-4 border-t border-gray-800/50 flex justify-between">
                  <div className="h-4 w-16 bg-gray-800 rounded"></div>
                  <div className="h-4 w-24 bg-gray-800 rounded"></div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
