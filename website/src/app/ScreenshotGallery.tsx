'use client';

import React, { useState } from 'react';
import { 
  Layout, 
  Zap, 
  Moon, 
  Settings, 
  Bell, 
  Activity, 
  TrendingUp, 
  User, 
  Brain, 
  Sliders, 
  ShieldCheck, 
  SlidersHorizontal 
} from 'lucide-react';

interface ScreenItem {
  id: string;
  title: string;
  img: string;
  desc: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  icon: any;
}

const SCREENS: ScreenItem[] = [
  { id: 'dashboard', title: 'Life OS Dashboard', img: '/dashboard_mockup.png', desc: 'Central metric console showing active focus scores, XP delta levels, and local database sync trackers.', icon: Layout },
  { id: 'tapasya', title: 'Tapasya Focus Timer', img: '/tapasya_timer_mockup.png', desc: 'Rigid focus screen enforcing Amoled DND parameters and tracking Effective Focus in 15-minute segments.', icon: Zap },
  { id: 'nightly', title: 'Nightly Protocol', img: '/nightly_protocol_mockup.png', desc: 'The step-by-step evening checklist interface managing Google workspace enqueues.', icon: Moon },
  { id: 'settings', title: 'Settings Console', img: '/settings_mockup.png', desc: 'Clean menu categorizing features, blocking configurations, sleep mode, and AI setups.', icon: Settings },
  { id: 'reminders', title: 'Alarms & Reminders', img: '/reminders_mockup.png', desc: 'Setup for notifications and task schedules linked directly with Google accounts.', icon: Bell },
  { id: 'usage', title: 'Usage Statistics', img: '/usage_stats_mockup.png', desc: 'Real-time on-device analytics tracking todays and 7-day screen limits per package.', icon: Activity },
  { id: 'progress', title: 'XP Analytics', img: '/progress_mockup.png', desc: 'Visualized user level metrics showing progression histories and focus segments.', icon: TrendingUp },
  { id: 'profile', title: 'Google Profile Sync', img: '/profile_mockup.png', desc: 'Status trackers for Google Calendar, Docs, Tasks, Drive integration and Cloudflare Secure IDs.', icon: User },
  { id: 'ai', title: 'Reality AI Assistant', img: '/ai_chat_mockup.png', desc: 'Direct chat dialogue module with the local assistant resolving queries.', icon: Brain },
  { id: 'tapasya_settings', title: 'Tapasya Settings', img: '/tapasya_settings_mockup.png', desc: 'Target time, pause limit limits, and app blocking checkboxes.', icon: Sliders },
  { id: 'permissions', title: 'Permission Board', img: '/permissions_mockup.png', desc: 'Central authorization checks verifying Accessibility service and Device Admin tools.', icon: ShieldCheck },
  { id: 'nightly_settings', title: 'Protocol Settings', img: '/nightly_settings_mockup.png', desc: 'Configurator managing synchronization windows and toggling individual execution steps.', icon: SlidersHorizontal },
];

export default function ScreenshotGallery() {
  const [activeTab, setActiveTab] = useState<string>('dashboard');
  const [loading, setLoading] = useState<boolean>(false);
  const currentScreen = SCREENS.find(s => s.id === activeTab) || SCREENS[0];

  const handleTabChange = (tabId: string) => {
    if (tabId !== activeTab) {
      setLoading(true);
      setActiveTab(tabId);
    }
  };

  return (
    <div className="grid lg:grid-cols-12 gap-8 items-center max-w-6xl mx-auto">
      
      {/* Selector Buttons: Left side on desktop, wrapped buttons on mobile */}
      <div className="lg:col-span-7 space-y-3 order-2 lg:order-1">
        <h3 className="hidden lg:block text-2xl font-extrabold text-white mb-6">Explore the Interface</h3>
        
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-2 gap-2">
          {SCREENS.map((screen) => {
            const Icon = screen.icon;
            const isActive = screen.id === activeTab;
            return (
              <button
                key={screen.id}
                onClick={() => handleTabChange(screen.id)}
                className={`flex items-center gap-3 px-4 py-3 rounded-xl border text-left transition duration-300 ${
                  isActive 
                    ? 'bg-gradient-to-r from-neural-cyan/20 to-neural-purple/20 border-neural-cyan text-white font-semibold' 
                    : 'bg-neural-card/50 border-gray-800 text-gray-400 hover:border-gray-700 hover:text-gray-300'
                }`}
              >
                <Icon size={16} className={isActive ? 'text-neural-cyan' : 'text-gray-500'} />
                <span className="text-xs font-medium truncate">{screen.title}</span>
              </button>
            );
          })}
        </div>

        {/* Feature Detail Text Box */}
        <div className="bg-neural-card/40 border border-gray-800 p-5 rounded-2xl mt-6 transition-all duration-300">
          <h4 className="text-lg font-bold text-white flex items-center gap-2 mb-2">
            <span className="w-1.5 h-1.5 rounded-full bg-neural-cyan animate-pulse"></span>
            {currentScreen.title}
          </h4>
          <p className="text-gray-400 text-sm leading-relaxed">{currentScreen.desc}</p>
        </div>
      </div>

      {/* Simulator Device Frame: Right side on desktop, top on mobile */}
      <div className="lg:col-span-5 flex justify-center order-1 lg:order-2">
        <div className="relative w-full max-w-[280px]">
          {/* Glowing Aura Background */}
          <div className="absolute -inset-1 bg-gradient-to-r from-neural-cyan to-neural-purple rounded-[36px] blur-md opacity-35"></div>
          
          {/* Phone Mockup Frame */}
          <div className="relative rounded-[32px] border-4 border-gray-800 bg-[#09090D] p-1.5 shadow-2xl overflow-hidden aspect-[9/16] h-[500px] flex items-center justify-center">
            {/* Camera Notch Indicator */}
            <div className="absolute top-3 left-1/2 transform -translate-x-1/2 w-20 h-4 bg-black rounded-full z-20 flex items-center justify-center">
              <span className="w-1.5 h-1.5 rounded-full bg-blue-900 animate-pulse"></span>
            </div>
            
            {/* Screen Image container */}
            <div className="w-full h-full bg-[#0a0a0f] rounded-[24px] overflow-hidden relative flex items-center justify-center p-1">
              {loading && (
                <div className="absolute inset-0 flex items-center justify-center bg-[#0a0a0f]/90 z-10">
                  <div className="flex flex-col items-center gap-3">
                    <div className="w-10 h-10 border-4 border-neural-cyan/30 border-t-neural-cyan rounded-full animate-spin"></div>
                    <span className="text-[10px] font-mono tracking-wider text-neural-cyan uppercase">Syncing View...</span>
                  </div>
                </div>
              )}
              <img
                src={currentScreen.img}
                onLoad={() => setLoading(false)}
                alt={`Reality Mobile App simulator displaying ${currentScreen.title}`}
                className={`max-w-full max-h-full object-contain rounded-[20px] transition-opacity duration-300 ${loading ? 'opacity-0' : 'opacity-100'}`}
              />
            </div>
          </div>
        </div>
      </div>

    </div>
  );
}
