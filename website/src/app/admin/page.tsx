import React from 'react';
import { ShieldAlert } from 'lucide-react';
import AdminClient from './AdminClient';

export const revalidate = 0; // Ensure this page is completely dynamic and never cached by Vercel

export default function AdminPage() {
  return (
    <div className="min-h-screen bg-neural-bg font-outfit selection:bg-neural-cyan selection:text-black pt-24 pb-12 px-4 sm:px-6 lg:px-8">
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-neural-cyan/5 via-neural-bg to-neural-bg opacity-50 z-0 pointer-events-none"></div>
      
      <div className="relative z-10 max-w-7xl mx-auto">
        <div className="flex items-center justify-center gap-3 mb-12">
          <ShieldAlert className="text-neural-cyan" size={28} />
          <h1 className="text-3xl font-bold text-white tracking-tight uppercase font-mono">
            Reality System Administration
          </h1>
        </div>

        <AdminClient />
      </div>
    </div>
  );
}
