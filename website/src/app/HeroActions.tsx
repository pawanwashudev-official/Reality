'use client';

import React from 'react';
import { Download, BookOpen } from 'lucide-react';
import Link from 'next/link';

export default function HeroActions({ latestVersion }: { latestVersion: string }) {
  return (
    <div className="flex flex-col items-center gap-4 w-full">
      <div className="flex flex-col sm:flex-row justify-center items-center gap-4 w-full">
        <Link
          href="/download"
          className="w-full sm:w-auto px-8 py-4 bg-white text-black text-lg font-bold rounded-xl hover:bg-gray-200 transition-colors shadow-lg flex items-center justify-center gap-2"
        >
          <Download size={20} />
          Download APK
        </Link>
        <a
          href="https://github.com/pawanwashudev-official/Reality"
          target="_blank" rel="noreferrer"
          className="w-full sm:w-auto px-8 py-4 bg-neural-card border border-gray-700 text-white text-lg font-bold rounded-xl hover:border-gray-500 transition-colors shadow-lg text-center"
        >
          View Source Code
        </a>
      </div>
      <Link
        href="/readme"
        className="text-neural-cyan hover:text-white flex items-center gap-2 mt-2 transition-colors border-b border-transparent hover:border-neural-cyan pb-1"
      >
        <BookOpen size={18} />
        Read About Our Project
      </Link>
    </div>
  );
}
