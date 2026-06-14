"use client";

import React, { useState, useEffect, useMemo } from 'react';
import { QRCodeSVG } from 'qrcode.react';

// Interface for TapasyaSession mapped from Android
export interface TapasyaSession {
  sessionId: string;
  name: string;
  targetTimeMs: number;
  startTime: number;
  endTime: number;
  effectiveTimeMs: number;
  totalPauseMs: number;
  pauseLimitMs: number;
  wasAutoStopped: boolean;
}

export default function TapashyaPage() {
  const [sessions, setSessions] = useState<TapasyaSession[]>([]);
  const [selectedSessions, setSelectedSessions] = useState<Set<string>>(new Set());

  useEffect(() => {
    loadAndPruneSessions();
    // Add dummy data if empty for testing
    const existing = localStorage.getItem('tapashya_sessions');
    if (!existing || JSON.parse(existing).length === 0) {
        const now = Date.now();
        const dummy: TapasyaSession[] = [
            {
                sessionId: `${now - 3600000}_${now - 1800000}`,
                name: "Morning Focus",
                targetTimeMs: 1800000,
                startTime: now - 3600000,
                endTime: now - 1800000,
                effectiveTimeMs: 1800000,
                totalPauseMs: 0,
                pauseLimitMs: 300000,
                wasAutoStopped: false
            },
            {
                sessionId: `${now - 86400000}_${now - 84600000}`,
                name: "Yesterday Work",
                targetTimeMs: 1800000,
                startTime: now - 86400000,
                endTime: now - 84600000,
                effectiveTimeMs: 1800000,
                totalPauseMs: 0,
                pauseLimitMs: 300000,
                wasAutoStopped: false
            }
        ];
        localStorage.setItem('tapashya_sessions', JSON.stringify(dummy));
        loadAndPruneSessions();
    }
  }, []);

  const loadAndPruneSessions = () => {
    try {
      const stored = localStorage.getItem('tapashya_sessions');
      if (stored) {
        const parsed: TapasyaSession[] = JSON.parse(stored);
        const now = Date.now();
        const sevenDaysAgo = now - 7 * 24 * 60 * 60 * 1000;

        // Filter out sessions older than 7 days
        const pruned = parsed.filter(s => s.startTime >= sevenDaysAgo);

        // Update state and storage if pruned
        if (pruned.length !== parsed.length) {
            localStorage.setItem('tapashya_sessions', JSON.stringify(pruned));
        }

        // Sort descending
        pruned.sort((a, b) => b.startTime - a.startTime);
        setSessions(pruned);
      }
    } catch (e) {
      console.error("Failed to parse sessions", e);
    }
  };

  // Group sessions by day string (e.g. "2024-06-14")
  const groupedSessions = useMemo(() => {
      const groups: Record<string, TapasyaSession[]> = {};
      sessions.forEach(session => {
          const date = new Date(session.startTime).toLocaleDateString();
          if (!groups[date]) groups[date] = [];
          groups[date].push(session);
      });
      return groups;
  }, [sessions]);

  const handleToggleSession = (sessionId: string) => {
      setSelectedSessions(prev => {
          const next = new Set(prev);
          if (next.has(sessionId)) {
              next.delete(sessionId);
          } else {
              next.add(sessionId);
          }
          return next;
      });
  };

  const handleToggleDay = (dateStr: string) => {
      const daySessions = groupedSessions[dateStr] || [];
      const allSelected = daySessions.every(s => selectedSessions.has(s.sessionId));

      setSelectedSessions(prev => {
          const next = new Set(prev);
          daySessions.forEach(s => {
              if (allSelected) {
                  next.delete(s.sessionId);
              } else {
                  next.add(s.sessionId);
              }
          });
          return next;
      });
  };

  const handleSelectAll = () => {
      if (selectedSessions.size === sessions.length && sessions.length > 0) {
          setSelectedSessions(new Set());
      } else {
          setSelectedSessions(new Set(sessions.map(s => s.sessionId)));
      }
  };

  const formatTime = (ms: number) => {
      return new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const formatDuration = (ms: number) => {
      return `${Math.floor(ms / 60000)}m`;
  };

  // Generate Compressed Deep Link
  const generateDeepLink = () => {
    if (selectedSessions.size === 0) return null;

    const selectedData = sessions.filter(s => selectedSessions.has(s.sessionId));

    // Schema: id|name|targetTime|startTime|endTime|effectiveTime|totalPause|pauseLimit|wasAutoStopped
    const serialized = selectedData.map(s => {
        return [
            s.sessionId,
            s.name,
            s.targetTimeMs,
            s.startTime,
            s.endTime,
            s.effectiveTimeMs,
            s.totalPauseMs,
            s.pauseLimitMs,
            s.wasAutoStopped ? 1 : 0
        ].join('|');
    }).join('~');

    // Base64 encode to avoid URL parsing issues with special characters
    const encoded = btoa(encodeURIComponent(serialized));
    return `Reality:Tapashya?data=${encoded}`;
  };

  const isAllSelected = sessions.length > 0 && selectedSessions.size === sessions.length;
  const qrData = generateDeepLink();

  return (
    <div className="min-h-screen bg-gray-50 text-gray-900 font-sans pb-24">
      <header className="bg-[#00695C] text-white p-6 shadow-md">
        <div className="max-w-4xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight">Tapashya Sync</h1>
          </div>
          <div className="text-[#E0F2F1] text-sm">7-Day Local Backup</div>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-4 py-8">

        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 mb-8 relative overflow-hidden">
            <div className="absolute top-0 right-0 w-32 h-32 bg-[#E0F2F1] rounded-bl-full opacity-50 pointer-events-none"></div>
            <h2 className="text-xl font-bold text-[#004D40] mb-2 relative z-10 flex items-center gap-2">
               Data Transfer
            </h2>
            <p className="text-gray-600 mb-6 relative z-10 text-sm">
              Select your recent meditation sessions to export them via secure, compressed QR code directly to the Android app. No servers are used.
            </p>

            <div className="bg-gray-50 rounded-xl p-8 border border-gray-200 flex flex-col items-center justify-center min-h-[300px]">
                {qrData ? (
                    <>
                        <div className="p-4 bg-white rounded-xl shadow-sm border border-gray-100 mb-4 inline-block">
                            <QRCodeSVG value={qrData} size={200} level="L" includeMargin={false} />
                        </div>
                        <p className="text-sm text-gray-500 font-medium">Scan with Reality App</p>
                        <p className="text-xs text-gray-400 mt-1">{selectedSessions.size} session(s) ready</p>
                    </>
                ) : (
                    <p className="text-gray-500 italic">Select sessions below to generate QR</p>
                )}
            </div>
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
            <div className="flex justify-between items-center mb-6 pb-4 border-b border-gray-100">
                <h3 className="text-lg font-bold text-gray-800">Available History</h3>
                <button
                    onClick={handleSelectAll}
                    className="text-sm font-medium text-[#00695C] bg-[#E0F2F1] px-4 py-2 rounded-full hover:bg-teal-100 transition-colors">
                    {isAllSelected ? "Deselect All" : "Select All"}
                </button>
            </div>

            {sessions.length === 0 ? (
                <div className="text-center py-8 text-gray-500">No sessions recorded in the last 7 days.</div>
            ) : (
                <div className="space-y-4">
                    {Object.entries(groupedSessions).map(([dateStr, daySessions]) => {
                        const allDaySelected = daySessions.length > 0 && daySessions.every(s => selectedSessions.has(s.sessionId));
                        return (
                            <div key={dateStr} className="border border-gray-100 rounded-xl overflow-hidden">
                                <div className="bg-gray-50 p-4 border-b border-gray-100 flex items-center justify-between">
                                    <label className="flex items-center gap-3 cursor-pointer">
                                        <input
                                            type="checkbox"
                                            checked={allDaySelected}
                                            onChange={() => handleToggleDay(dateStr)}
                                            className="w-5 h-5 text-[#00695C] rounded border-gray-300 focus:ring-[#00695C]"
                                        />
                                        <span className="font-semibold text-gray-700">{dateStr === new Date().toLocaleDateString() ? 'Today' : dateStr}</span>
                                    </label>
                                    <span className="text-xs font-medium text-gray-500 bg-white px-2 py-1 rounded-md border border-gray-200">
                                        {daySessions.length} Session{daySessions.length !== 1 && 's'}
                                    </span>
                                </div>
                                <div className="p-2 space-y-2">
                                     {daySessions.map(session => (
                                         <label key={session.sessionId} className="flex items-center justify-between p-3 hover:bg-gray-50 rounded-lg cursor-pointer transition-colors group">
                                             <div className="flex items-center gap-3">
                                                 <input
                                                    type="checkbox"
                                                    checked={selectedSessions.has(session.sessionId)}
                                                    onChange={() => handleToggleSession(session.sessionId)}
                                                    className="w-4 h-4 text-[#00695C] rounded border-gray-300 focus:ring-[#00695C]"
                                                 />
                                                 <div>
                                                     <div className="font-medium text-gray-800 group-hover:text-[#00695C] transition-colors">{session.name}</div>
                                                     <div className="text-xs text-gray-500">{formatTime(session.startTime)} - {formatTime(session.endTime)}</div>
                                                 </div>
                                             </div>
                                             <div className="text-right">
                                                 <div className="text-sm font-bold text-[#651FFF]">{formatDuration(session.effectiveTimeMs)}</div>
                                                 <div className="text-xs text-gray-400">Effective</div>
                                             </div>
                                         </label>
                                     ))}
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>

      </main>
    </div>
  );
}
