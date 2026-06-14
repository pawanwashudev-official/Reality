"use client";

import React, { useState, useEffect, useMemo, useRef } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { Settings, Play, Pause, Square, RotateCcw, X, Home } from 'lucide-react';
import Link from 'next/link';

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

  // Clock State
  const [isRunning, setIsRunning] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const [elapsedRunning, setElapsedRunning] = useState(0);
  const [totalPause, setTotalPause] = useState(0);
  const [runningStart, setRunningStart] = useState(0);
  const [pauseStart, setPauseStart] = useState(0);
  const [sessionStart, setSessionStart] = useState(0);

  // Settings
  const [targetTimeMins, setTargetTimeMins] = useState(60);
  const [pauseLimitMins, setPauseLimitMins] = useState(15);
  const [showSettings, setShowSettings] = useState(false);

  // Derived Display Values
  const [displayElapsed, setDisplayElapsed] = useState(0);
  const [displayPause, setDisplayPause] = useState(0);

  const timerRef = useRef<NodeJS.Timeout | null>(null);

  // --- Initial Load & Pruning ---
  useEffect(() => {
    loadAndPruneSessions();
  }, []);

  const loadAndPruneSessions = () => {
    try {
      const stored = localStorage.getItem('tapashya_sessions');
      if (stored) {
        const parsed: TapasyaSession[] = JSON.parse(stored);
        const now = Date.now();
        const sevenDaysAgo = now - 7 * 24 * 60 * 60 * 1000;

        const pruned = parsed.filter(s => s.startTime >= sevenDaysAgo);

        if (pruned.length !== parsed.length) {
            localStorage.setItem('tapashya_sessions', JSON.stringify(pruned));
        }

        pruned.sort((a, b) => b.startTime - a.startTime);
        setSessions(pruned);
      }
    } catch (e) {
      console.error("Failed to parse sessions", e);
    }
  };

  // --- Clock Logic ---
  useEffect(() => {
    if (isRunning) {
        timerRef.current = setInterval(() => {
            const now = Date.now();
            setDisplayElapsed(elapsedRunning + (now - runningStart));
        }, 500);
    } else if (isPaused) {
        timerRef.current = setInterval(() => {
            const now = Date.now();
            const currentPause = totalPause + (now - pauseStart);
            setDisplayPause(currentPause);

            // Auto-stop if pause limit exceeded
            if (currentPause >= pauseLimitMins * 60 * 1000) {
                handleStop(true);
            }
        }, 500);
    } else {
        setDisplayElapsed(0);
        setDisplayPause(0);
    }

    return () => {
        if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [isRunning, isPaused, elapsedRunning, totalPause, runningStart, pauseStart, pauseLimitMins]);

  const handleStart = () => {
      const now = Date.now();
      if (!isRunning && !isPaused) {
          // Fresh start
          setSessionStart(now);
      } else if (isPaused) {
          // Resume from pause
          setTotalPause(prev => prev + (now - pauseStart));
      }
      setRunningStart(now);
      setIsRunning(true);
      setIsPaused(false);
  };

  const handlePause = () => {
      const now = Date.now();
      setElapsedRunning(prev => prev + (now - runningStart));
      setPauseStart(now);
      setIsRunning(false);
      setIsPaused(true);
  };

  const handleStop = (wasAutoStopped: boolean = false) => {
      const now = Date.now();
      let finalElapsed = elapsedRunning;
      let finalPause = totalPause;

      if (isRunning) {
          finalElapsed += (now - runningStart);
      } else if (isPaused) {
          finalPause += (now - pauseStart);
      }

      const pauseLimitMs = pauseLimitMins * 60 * 1000;

      const endTime = wasAutoStopped ? (pauseStart + (pauseLimitMs - totalPause)) : now;

      // Calculate effective time (floored to 15 mins)
      const fifteenMins = 15 * 60 * 1000;
      const effectiveTimeMs = Math.floor(finalElapsed / fifteenMins) * fifteenMins;

      const newSession: TapasyaSession = {
          sessionId: `${sessionStart}_${endTime}`,
          name: "Tapasya Web",
          targetTimeMs: targetTimeMins * 60 * 1000,
          startTime: sessionStart,
          endTime: endTime,
          effectiveTimeMs: effectiveTimeMs,
          totalPauseMs: Math.min(finalPause, pauseLimitMs),
          pauseLimitMs: pauseLimitMs,
          wasAutoStopped: wasAutoStopped
      };

      try {
          const stored = localStorage.getItem('tapashya_sessions');
          const parsed = stored ? JSON.parse(stored) : [];
          parsed.push(newSession);
          localStorage.setItem('tapashya_sessions', JSON.stringify(parsed));
          loadAndPruneSessions();
      } catch(e) { console.error("Failed to save", e); }

      resetClock();
  };

  const handleReset = () => {
      resetClock();
  };

  const resetClock = () => {
      setIsRunning(false);
      setIsPaused(false);
      setElapsedRunning(0);
      setTotalPause(0);
      setRunningStart(0);
      setPauseStart(0);
      setDisplayElapsed(0);
      setDisplayPause(0);
  };

  // --- UI Formatting ---
  const formatTime = (ms: number) => {
      const totalSecs = Math.floor(ms / 1000);
      const seconds = totalSecs % 60;
      const minutes = Math.floor(totalSecs / 60) % 60;
      const hours = Math.floor(totalSecs / 3600);
      return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  };

  const formatMinutes = (mins: number) => {
      const h = Math.floor(mins / 60);
      const m = mins % 60;
      return h > 0 ? `${h}h ${m}m` : `${m}m`;
  };

  const calculateProgress = () => {
      const targetMs = targetTimeMins * 60 * 1000;
      return targetMs > 0 ? Math.min(displayElapsed / targetMs, 1) : 0;
  };

  // --- Export Logic ---
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
          if (next.has(sessionId)) next.delete(sessionId);
          else next.add(sessionId);
          return next;
      });
  };

  const handleToggleDay = (dateStr: string) => {
      const daySessions = groupedSessions[dateStr] || [];
      const allSelected = daySessions.every(s => selectedSessions.has(s.sessionId));

      setSelectedSessions(prev => {
          const next = new Set(prev);
          daySessions.forEach(s => {
              if (allSelected) next.delete(s.sessionId);
              else next.add(s.sessionId);
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

  const generateDeepLink = () => {
    if (selectedSessions.size === 0) return null;
    const selectedData = sessions.filter(s => selectedSessions.has(s.sessionId));
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
    return `Reality:Tapashya?data=${btoa(encodeURIComponent(serialized))}`;
  };

  const isAllSelected = sessions.length > 0 && selectedSessions.size === sessions.length;
  const qrData = generateDeepLink();

  // --- UI Colors & States ---
  const colorPrimary = "#00695C"; // Teal
  const colorAmber = "#FFC107";


  const statusText = isRunning ? "Focusing: Tapasya Web" : (isPaused ? "Paused" : "Ready to Focus");
  const waveColor = isPaused ? colorAmber : colorPrimary;
  const progressPercent = calculateProgress() * 100;

  const effectiveMinutesLive = Math.floor((Math.floor(displayElapsed / (15 * 60 * 1000)) * 15 * 60 * 1000) / 60000);
  const currentFragmentLive = Math.floor(effectiveMinutesLive / 15);
  // Basic XP dummy calc matching android (currentFragment * 15) -> basic mapping
  const currentXpLive = currentFragmentLive * 10;

  return (
    <div className="min-h-screen bg-gray-50 text-gray-900 font-sans pb-24">
      {/* Header */}
      <header className="bg-[#00695C] text-white p-4 shadow-md sticky top-0 z-50">
        <div className="max-w-md mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link href="/" className="p-2 -ml-2 rounded-full hover:bg-[#004D40] transition-colors">
              <Home size={20} />
            </Link>
            <h1 className="text-xl font-bold tracking-tight">Tapashya</h1>
          </div>
          <button onClick={() => setShowSettings(true)} className="p-2 rounded-full hover:bg-[#004D40] transition-colors">
             <Settings size={20} />
          </button>
        </div>
      </header>

      <main className="max-w-md mx-auto px-4 py-8">

        {/* Clock View Area (Matches Android TapasyaActivity top section) */}
        <div className="flex flex-col items-center mb-10">
            <div className="relative w-64 h-64 rounded-full bg-white shadow-lg border border-gray-100 flex items-center justify-center overflow-hidden mb-6">
                {/* Simulated Wave Background */}
                <div
                    className="absolute bottom-0 w-full transition-all duration-1000 ease-in-out opacity-20"
                    style={{ height: `${progressPercent}%`, backgroundColor: waveColor }}
                />
                <div className="relative z-10 flex flex-col items-center">
                    <span className="text-4xl font-mono font-bold tracking-tight text-gray-800">
                        {formatTime(displayElapsed)}
                    </span>
                    <span className="text-sm font-medium text-gray-500 mt-2" style={{ color: isPaused ? colorAmber : colorPrimary }}>
                        {statusText}
                    </span>
                </div>
            </div>

            {/* Live Stats */}
            {(isRunning || isPaused) && (
                <div className="bg-white px-6 py-3 rounded-full shadow-sm border border-gray-100 flex items-center gap-6 mb-6">
                    <span className="text-sm font-bold text-gray-700">⚡ {currentXpLive} XP</span>
                    <span className="text-sm font-medium text-gray-500 border-l pl-6">Fragment {currentFragmentLive}</span>
                </div>
            )}

            {/* Rest Timer */}
            {isPaused && (
                <div className="text-sm font-bold text-amber-600 mb-6 bg-amber-50 px-4 py-2 rounded-lg border border-amber-100">
                    Pause left: {formatTime((pauseLimitMins * 60 * 1000) - displayPause)}
                </div>
            )}

            {/* Control Buttons */}
            <div className="flex items-center gap-4">
                {(!isRunning && !isPaused) && (
                    <button onClick={handleStart} className="flex items-center gap-2 bg-[#00695C] text-white px-8 py-4 rounded-2xl font-bold shadow-md hover:bg-[#004D40] transition-transform active:scale-95">
                        <Play size={20} fill="currentColor" />
                        Start
                    </button>
                )}

                {isRunning && (
                    <button onClick={handlePause} className="flex items-center gap-2 bg-[#651FFF] text-white px-8 py-4 rounded-2xl font-bold shadow-md hover:bg-[#311B92] transition-transform active:scale-95">
                        <Pause size={20} fill="currentColor" />
                        Pause
                    </button>
                )}

                {isPaused && (
                    <button onClick={handleStart} className="flex items-center gap-2 bg-[#00695C] text-white px-8 py-4 rounded-2xl font-bold shadow-md hover:bg-[#004D40] transition-transform active:scale-95">
                        <Play size={20} fill="currentColor" />
                        Resume
                    </button>
                )}

                {(isRunning || isPaused) && (
                    <>
                        <button onClick={() => handleStop(false)} className="p-4 rounded-2xl bg-[#B3261E] text-white shadow-md hover:bg-[#8C1D18] transition-transform active:scale-95">
                            <Square size={20} fill="currentColor" />
                        </button>
                        <button onClick={handleReset} className="p-4 rounded-2xl bg-gray-200 text-gray-700 shadow-sm hover:bg-gray-300 transition-transform active:scale-95">
                            <RotateCcw size={20} />
                        </button>
                    </>
                )}
            </div>
        </div>

        <hr className="border-gray-200 mb-8" />

        {/* Sync Card */}
        <div className="bg-white rounded-3xl shadow-sm border border-gray-100 p-6 mb-8 relative overflow-hidden">
            <div className="absolute top-0 right-0 w-32 h-32 bg-[#E0F2F1] rounded-bl-full opacity-50 pointer-events-none"></div>
            <h2 className="text-xl font-bold text-[#004D40] mb-2 relative z-10 flex items-center gap-2">
               App Sync
            </h2>
            <p className="text-gray-600 mb-6 relative z-10 text-sm leading-relaxed">
              Select local sessions below to export via secure QR code.
            </p>

            <div className="bg-gray-50 rounded-2xl p-6 border border-gray-200 flex flex-col items-center justify-center min-h-[250px]">
                {qrData ? (
                    <>
                        <div className="p-3 bg-white rounded-2xl shadow-sm border border-gray-100 mb-3">
                            <QRCodeSVG value={qrData} size={180} level="L" includeMargin={false} />
                        </div>
                        <p className="text-sm font-bold text-[#00695C]">Scan with Reality App</p>
                        <p className="text-xs text-gray-500 mt-1">{selectedSessions.size} session(s) selected</p>
                    </>
                ) : (
                    <div className="text-center opacity-50">
                        <div className="w-24 h-24 border-2 border-dashed border-gray-300 rounded-xl mb-3 mx-auto"></div>
                        <p className="text-sm font-medium text-gray-500">Select sessions below</p>
                    </div>
                )}
            </div>
        </div>

        {/* Sessions List */}
        <div className="bg-white rounded-3xl shadow-sm border border-gray-100 p-6">
            <div className="flex justify-between items-center mb-6">
                <h3 className="text-lg font-bold text-gray-800">History (7 Days)</h3>
                <button
                    onClick={handleSelectAll}
                    className="text-xs font-bold text-[#00695C] bg-[#E0F2F1] px-4 py-2 rounded-full active:scale-95 transition-transform">
                    {isAllSelected ? "Deselect All" : "Select All"}
                </button>
            </div>

            {sessions.length === 0 ? (
                <div className="text-center py-10">
                    <p className="text-gray-500 font-medium">No sessions recorded yet.</p>
                    <p className="text-xs text-gray-400 mt-1">Start focusing to generate local history.</p>
                </div>
            ) : (
                <div className="space-y-4">
                    {Object.entries(groupedSessions).map(([dateStr, daySessions]) => {
                        const allDaySelected = daySessions.length > 0 && daySessions.every(s => selectedSessions.has(s.sessionId));
                        return (
                            <div key={dateStr} className="border border-gray-100 rounded-2xl overflow-hidden">
                                <div className="bg-gray-50 px-4 py-3 border-b border-gray-100 flex items-center justify-between">
                                    <label className="flex items-center gap-3 cursor-pointer">
                                        <input
                                            type="checkbox"
                                            checked={allDaySelected}
                                            onChange={() => handleToggleDay(dateStr)}
                                            className="w-5 h-5 text-[#00695C] rounded border-gray-300 focus:ring-[#00695C]"
                                        />
                                        <span className="font-bold text-gray-700 text-sm">{dateStr === new Date().toLocaleDateString() ? 'Today' : dateStr}</span>
                                    </label>
                                    <span className="text-xs font-bold text-gray-400">
                                        {daySessions.length} Session{daySessions.length !== 1 && 's'}
                                    </span>
                                </div>
                                <div className="p-2 space-y-1">
                                     {daySessions.map(session => (
                                         <label key={session.sessionId} className="flex items-center justify-between p-3 hover:bg-gray-50 rounded-xl cursor-pointer transition-colors group">
                                             <div className="flex items-center gap-3">
                                                 <input
                                                    type="checkbox"
                                                    checked={selectedSessions.has(session.sessionId)}
                                                    onChange={() => handleToggleSession(session.sessionId)}
                                                    className="w-5 h-5 text-[#00695C] rounded border-gray-300 focus:ring-[#00695C]"
                                                 />
                                                 <div>
                                                     <div className="font-bold text-gray-800 text-sm group-hover:text-[#00695C] transition-colors">{session.name}</div>
                                                     <div className="text-xs text-gray-500 font-medium mt-0.5">
                                                        {new Date(session.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} - {new Date(session.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                                     </div>
                                                 </div>
                                             </div>
                                             <div className="text-right">
                                                 <div className="text-sm font-black text-[#651FFF]">{formatMinutes(Math.floor(session.effectiveTimeMs / 60000))}</div>
                                                 <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Effective</div>
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

      {/* Settings Bottom Sheet (Simulated) */}
      {showSettings && (
          <div className="fixed inset-0 z-[100] flex items-end justify-center sm:items-center p-4">
              <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={() => setShowSettings(false)}></div>
              <div className="relative bg-white w-full max-w-md rounded-t-3xl sm:rounded-3xl shadow-2xl p-6 animate-in slide-in-from-bottom-8">
                  <div className="flex justify-between items-center mb-6">
                      <h3 className="text-xl font-bold text-gray-900">Tapasya Settings</h3>
                      <button onClick={() => setShowSettings(false)} className="p-2 bg-gray-100 rounded-full hover:bg-gray-200">
                          <X size={20} />
                      </button>
                  </div>

                  <div className="space-y-6">
                      <div>
                          <div className="flex justify-between items-end mb-2">
                              <label className="font-bold text-gray-700">Target Duration</label>
                              <span className="text-[#00695C] font-black">{formatMinutes(targetTimeMins)}</span>
                          </div>
                          <input
                              type="range"
                              min="15" max="360" step="15"
                              value={targetTimeMins}
                              onChange={(e) => setTargetTimeMins(Number(e.target.value))}
                              className="w-full accent-[#00695C]"
                          />
                      </div>

                      <div>
                          <div className="flex justify-between items-end mb-2">
                              <label className="font-bold text-gray-700">Pause Limit</label>
                              <span className="text-[#00695C] font-black">{formatMinutes(pauseLimitMins)}</span>
                          </div>
                          <input
                              type="range"
                              min="1" max="60" step="1"
                              value={pauseLimitMins}
                              onChange={(e) => setPauseLimitMins(Number(e.target.value))}
                              className="w-full accent-[#00695C]"
                          />
                      </div>

                      <button
                          onClick={() => setShowSettings(false)}
                          className="w-full py-4 bg-[#00695C] text-white rounded-2xl font-bold shadow-md hover:bg-[#004D40] active:scale-95 transition-transform"
                      >
                          Save Settings
                      </button>
                  </div>
              </div>
          </div>
      )}
    </div>
  );
}
