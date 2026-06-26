"use client";

import React, { useState, useEffect, useMemo, useRef } from 'react';
import { createPortal } from 'react-dom';
import { QRCodeSVG } from 'qrcode.react';
import { Settings, Play, Pause, Square, RotateCcw, X, Trash2, Edit2, QrCode, ArrowLeft, ChevronLeft, ChevronRight, Calendar, Maximize2, Minimize2, Clock } from 'lucide-react';
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

interface CalendarEvent {
  id: string;
  title: string;
  startTime: number;
  endTime: number;
}

interface ActiveSessionState {  isActive: boolean;
  isRunning: boolean;
  isPaused: boolean;
  sessionName: string;
  targetTimeMins: number;
  pauseLimitMins: number;
  sessionStart: number;
  runningStart: number;
  pauseStart: number;
  elapsedRunning: number;
  totalPause: number;
}

const DEFAULT_ACTIVE_STATE: ActiveSessionState = {
  isActive: false,
  isRunning: false,
  isPaused: false,
  sessionName: 'Tapasya',
  targetTimeMins: 60,
  pauseLimitMins: 15,
  sessionStart: 0,
  runningStart: 0,
  pauseStart: 0,
  elapsedRunning: 0,
  totalPause: 0
};

export default function TapashyaPage() {
  const [sessions, setSessions] = useState<TapasyaSession[]>([]);
  const [selectedSessions, setSelectedSessions] = useState<Set<string>>(new Set());

  // Clock State
  const [activeState, setActiveState] = useState<ActiveSessionState>(DEFAULT_ACTIVE_STATE);


  // Mini Mode State
  const [isMiniMode, setIsMiniMode] = useState(false);
  const [pipWindow, setPipWindow] = useState<Window | null>(null);
  const [miniPos, setMiniPos] = useState({ x: 20, y: 20 });
  const [isDragging, setIsDragging] = useState(false);
  const dragStartPos = useRef({ x: 0, y: 0 });

  // Dialogs
  const [showSettings, setShowSettings] = useState(false);
  const [showStartDialog, setShowStartDialog] = useState(false);
  const [showRenameDialog, setShowRenameDialog] = useState(false);
  const [showExportDialog, setShowExportDialog] = useState(false);

  // Dialog Form States
  const [formName, setFormName] = useState('Tapasya');
  const [formTargetTime, setFormTargetTime] = useState(60);
  const [formPauseLimit, setFormPauseLimit] = useState(15);
    const [renameInput, setRenameInput] = useState('');
  const [showEditTimeDialog, setShowEditTimeDialog] = useState(false);
  const [editStartTime, setEditStartTime] = useState('');
  const [editEndTime, setEditEndTime] = useState('');
  const [showRunningEditDialog, setShowRunningEditDialog] = useState(false);
  const [runningEditStartTime, setRunningEditStartTime] = useState('');

  // Derived Display Values
  const [displayElapsed, setDisplayElapsed] = useState(0);
  const [displayPause, setDisplayPause] = useState(0);

  // Day Navigator State
  const [selectedDate, setSelectedDate] = useState(() => {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    return d;
  });

  // Calendar State
  const [calendarConnected, setCalendarConnected] = useState<boolean>(false);
  const [calendarEvents, setCalendarEvents] = useState<CalendarEvent[]>([]);
  const [recommendedEvent, setRecommendedEvent] = useState<CalendarEvent | null>(null);

  useEffect(() => {
      // Clean up hash if coming from old auth flow or just to be safe
      if (typeof window !== 'undefined' && window.location.hash.includes('access_token=')) {
          window.location.hash = '';
      }

      const checkConnection = async () => {
          await fetchTodayEvents();
      };

      checkConnection();
  }, []);

  const fetchTodayEvents = async () => {
      try {
          const res = await fetch('/api/calendar/events');

          if (res.status === 401) {
              setCalendarConnected(false);
              return;
          }

          if (res.ok) {
              setCalendarConnected(true);
              const data = await res.json();
              if (data.events) {
                  const now = new Date().getTime();
                  const validEvents = data.events;
                  setCalendarEvents(validEvents);

                  const recommended = validEvents.find((e: CalendarEvent) => e.endTime > now);
                  setRecommendedEvent(recommended || null);
              }
          }
      } catch (err) {
          console.error("Failed to fetch events", err);
      }
  };

  const disconnectCalendar = async () => {
      try {
          await fetch('/api/auth/logout', { method: 'POST' });
          setCalendarConnected(false);
          setCalendarEvents([]);
          setRecommendedEvent(null);
      } catch (e) {
          console.error("Failed to disconnect", e);
      }
  };


  const timerRef = useRef<NodeJS.Timeout | null>(null);
  const clickTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // --- Initial Load & Pruning ---
  useEffect(() => {
    loadAndPruneSessions();
    loadActiveState();
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

  const loadActiveState = () => {
      try {
          const stored = localStorage.getItem('tapashya_active_state');
          if (stored) {
              const state: ActiveSessionState = JSON.parse(stored);

              if (state.isActive) {
                  const now = Date.now();
                  let currentPause = state.totalPause;

                  if (state.isPaused) {
                      currentPause += (now - state.pauseStart);
                  }

                  // Auto-stop check on load
                  if (state.isPaused && currentPause >= state.pauseLimitMins * 60 * 1000) {
                      handleStopInternal(true, state);
                  } else {
                      setActiveState(state);
                      setFormName(state.sessionName);
                      setFormTargetTime(state.targetTimeMins);
                      setFormPauseLimit(state.pauseLimitMins);
                  }
              }
          }
      } catch (e) { console.error("Failed to parse active state", e); }
  };

  const saveActiveState = (newState: ActiveSessionState) => {
      setActiveState(newState);
      if (newState.isActive) {
          localStorage.setItem('tapashya_active_state', JSON.stringify(newState));
      } else {
          localStorage.removeItem('tapashya_active_state');
      }
  };

  // --- Clock Logic ---
  useEffect(() => {
    if (activeState.isRunning) {
        timerRef.current = setInterval(() => {
            const now = Date.now();
            setDisplayElapsed(activeState.elapsedRunning + (now - activeState.runningStart));
        }, 500);
    } else if (activeState.isPaused) {
        timerRef.current = setInterval(() => {
            const now = Date.now();
            const currentPause = activeState.totalPause + (now - activeState.pauseStart);
            setDisplayPause(currentPause);

            // Auto-stop if pause limit exceeded
            if (currentPause >= activeState.pauseLimitMins * 60 * 1000) {
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
  }, [activeState]);

  // Handle Start Click (distinguish single vs double click)
  const onStartClicked = () => {
      if (activeState.isPaused) {
          handleResume();
          return;
      }
      if (clickTimeoutRef.current !== null) {
          // Double click detected! Bypass dialog
          clearTimeout(clickTimeoutRef.current);
          clickTimeoutRef.current = null;
          if (recommendedEvent) {
              const durationMins = Math.floor((recommendedEvent.endTime - recommendedEvent.startTime) / 60000);
              startSessionWithConfig(recommendedEvent.title, durationMins, formPauseLimit);
          } else {
              startSessionWithConfig(formName, formTargetTime, formPauseLimit);
          }
      } else {
          // Single click — wait to see if it becomes a double click
          clickTimeoutRef.current = setTimeout(() => {
              clickTimeoutRef.current = null;
              // Open Dialog
              if (recommendedEvent) {
                  setFormName(recommendedEvent.title);
                  setFormTargetTime(Math.floor((recommendedEvent.endTime - recommendedEvent.startTime) / 60000));
              }
              setShowStartDialog(true);
          }, 300); // 300ms window for double tap
      }
  };

  const startSessionWithConfig = (name: string, targetMins: number, pauseMins: number) => {
      if (activeState.isRunning || activeState.isPaused) {
          alert("Active or paused timers must be stopped first.");
          return;
      }
      const now = Date.now();
      saveActiveState({
          ...activeState,
          isActive: true,
          isRunning: true,
          isPaused: false,
          sessionName: name,
          targetTimeMins: targetMins,
          pauseLimitMins: pauseMins,
          sessionStart: now,
          runningStart: now,
          elapsedRunning: 0,
          totalPause: 0
      });
      setShowStartDialog(false);
  };

  const handleResume = () => {
      const now = Date.now();
      saveActiveState({
          ...activeState,
          isRunning: true,
          isPaused: false,
          totalPause: activeState.totalPause + (now - activeState.pauseStart),
          runningStart: now
      });
  };

  const handlePause = () => {
      const now = Date.now();
      saveActiveState({
          ...activeState,
          isRunning: false,
          isPaused: true,
          elapsedRunning: activeState.elapsedRunning + (now - activeState.runningStart),
          pauseStart: now
      });
  };

  const handleStop = (wasAutoStopped: boolean = false) => {
      handleStopInternal(wasAutoStopped, activeState);
  };

  const handleStopInternal = (wasAutoStopped: boolean, state: ActiveSessionState) => {
      if (!state.isActive) return;

      const now = Date.now();
      let finalElapsed = state.elapsedRunning;
      let finalPause = state.totalPause;

      if (state.isRunning) {
          finalElapsed += (now - state.runningStart);
      } else if (state.isPaused) {
          finalPause += (now - state.pauseStart);
      }

      const pauseLimitMs = state.pauseLimitMins * 60 * 1000;
      const endTime = wasAutoStopped ? (state.pauseStart + (pauseLimitMs - state.totalPause)) : now;

      const fifteenMins = 15 * 60 * 1000;
      const effectiveTimeMs = Math.floor(finalElapsed / fifteenMins) * fifteenMins;

      const newSession: TapasyaSession = {
          sessionId: `${state.sessionStart}_${endTime}`,
          name: state.sessionName,
          targetTimeMs: state.targetTimeMins * 60 * 1000,
          startTime: state.sessionStart,
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

  const resetClock = () => {
      saveActiveState(DEFAULT_ACTIVE_STATE);
  };



  const handleOpenMiniMode = async () => {
    if ('documentPictureInPicture' in window) {
      try {
        const pip = await (window as { documentPictureInPicture?: { requestWindow: (options?: { width?: number; height?: number; }) => Promise<Window> } }).documentPictureInPicture!.requestWindow({
          width: 250,
          height: 150,
        });

        // Copy tailwind styles
        [...document.styleSheets].forEach((styleSheet) => {
          try {
            const cssRules = [...styleSheet.cssRules].map((rule) => rule.cssText).join('');
            const style = document.createElement('style');
            style.textContent = cssRules;
            pip.document.head.appendChild(style);
          } catch {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.type = styleSheet.type;
            link.media = styleSheet.media.mediaText;
            link.href = styleSheet.href || '';
            pip.document.head.appendChild(link);
          }
        });

        // Add font
        const fontLink = document.createElement('link');
        fontLink.rel = 'stylesheet';
        fontLink.href = 'https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;700;900&family=JetBrains+Mono:wght@400;700&display=swap';
        pip.document.head.appendChild(fontLink);

        // Body styling
        pip.document.body.style.backgroundColor = '#05050A';
        pip.document.body.style.margin = '0';
        pip.document.body.style.display = 'flex';
        pip.document.body.style.alignItems = 'center';
        pip.document.body.style.justifyContent = 'center';
        pip.document.body.style.fontFamily = "'Outfit', sans-serif";

        pip.addEventListener('pagehide', () => {
          setPipWindow(null);
        });

        setPipWindow(pip);
      } catch (err) {
        console.error(err);
        setIsMiniMode(true);
      }
    } else {
      setIsMiniMode(true);
    }
  };

  // --- Mini Mode Handlers ---
  const handlePointerDown = (e: React.PointerEvent) => {
      setIsDragging(true);
      dragStartPos.current = { x: e.clientX - miniPos.x, y: e.clientY - miniPos.y };
      (e.target as HTMLElement).setPointerCapture(e.pointerId);
  };
  const handlePointerMove = (e: React.PointerEvent) => {
      if (!isDragging) return;
      setMiniPos({ x: e.clientX - dragStartPos.current.x, y: e.clientY - dragStartPos.current.y });
  };
  const handlePointerUp = (e: React.PointerEvent) => {
      setIsDragging(false);
      (e.target as HTMLElement).releasePointerCapture(e.pointerId);
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
      const targetMs = activeState.targetTimeMins * 60 * 1000;
      return targetMs > 0 ? Math.min(displayElapsed / targetMs, 1) : 0;
  };

  // --- History & Actions Logic ---
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

  const handlePrevDay = () => {
      setSelectedDate(prev => {
          const d = new Date(prev);
          d.setDate(d.getDate() - 1);
          return d;
      });
  };

  const handleNextDay = () => {
      setSelectedDate(prev => {
          const d = new Date(prev);
          d.setDate(d.getDate() + 1);
          const today = new Date();
          today.setHours(0, 0, 0, 0);
          return d > today ? today : d;
      });
  };

  const isToday = (date: Date) => {
      const today = new Date();
      return date.getDate() === today.getDate() &&
             date.getMonth() === today.getMonth() &&
             date.getFullYear() === today.getFullYear();
  };

  const handleDeleteSelected = () => {
      if (selectedSessions.size === 0) return;
      if (confirm(`Delete ${selectedSessions.size} session(s)?`)) {
          const newSessions = sessions.filter(s => !selectedSessions.has(s.sessionId));
          localStorage.setItem('tapashya_sessions', JSON.stringify(newSessions));
          setSessions(newSessions);
          setSelectedSessions(new Set());
      }
  };

  const handleRenameClick = () => {
      if (selectedSessions.size !== 1) return;
      const id = Array.from(selectedSessions)[0];
      const session = sessions.find(s => s.sessionId === id);
      if (session) {
          setRenameInput(session.name);
          setShowRenameDialog(true);
      }
  };


  const handleEditTimeClick = () => {
      if (selectedSessions.size !== 1) return;
      const id = Array.from(selectedSessions)[0];
      const session = sessions.find(s => s.sessionId === id);
      if (session) {
          // Format as HH:MM for input[type="time"]
          const start = new Date(session.startTime);
          const end = new Date(session.endTime);
          setEditStartTime(`${start.getHours().toString().padStart(2, '0')}:${start.getMinutes().toString().padStart(2, '0')}`);
          setEditEndTime(`${end.getHours().toString().padStart(2, '0')}:${end.getMinutes().toString().padStart(2, '0')}`);
          setShowEditTimeDialog(true);
      }
  };


  const handleRunningEditClick = () => {
      const start = new Date(activeState.sessionStart);
      setRunningEditStartTime(`${start.getHours().toString().padStart(2, '0')}:${start.getMinutes().toString().padStart(2, '0')}`);
      setShowRunningEditDialog(true);
  };

  const saveRunningEditTime = () => {
      const startSplit = runningEditStartTime.split(':');
      const newStart = new Date(activeState.sessionStart);
      newStart.setHours(parseInt(startSplit[0], 10), parseInt(startSplit[1], 10), 0, 0);

      saveActiveState({
          ...activeState,
          sessionStart: newStart.getTime(),
          runningStart: newStart.getTime(),
          elapsedRunning: 0,
          totalPause: 0
      });
      setShowRunningEditDialog(false);
  };

  const saveEditTime = () => {
      if (selectedSessions.size !== 1) return;
      const id = Array.from(selectedSessions)[0];

      const newSessions = sessions.map(s => {
          if (s.sessionId === id) {
              const startSplit = editStartTime.split(':');
              const endSplit = editEndTime.split(':');

              const newStart = new Date(s.startTime);
              newStart.setHours(parseInt(startSplit[0], 10), parseInt(startSplit[1], 10), 0, 0);

              const newEnd = new Date(s.endTime);
              newEnd.setHours(parseInt(endSplit[0], 10), parseInt(endSplit[1], 10), 0, 0);


              // Recalculate effective time based on new bounds
              let newElapsed = (newEnd.getTime() - newStart.getTime()) - s.totalPauseMs;
              if (newElapsed < 0) newElapsed = 0;
              const newEffectiveTimeMs = Math.floor(newElapsed / (15 * 60 * 1000)) * (15 * 60 * 1000);

              return { ...s, startTime: newStart.getTime(), endTime: newEnd.getTime(), effectiveTimeMs: newEffectiveTimeMs };

          }
          return s;
      });
      localStorage.setItem('tapashya_sessions', JSON.stringify(newSessions));
      setSessions(newSessions);
      setShowEditTimeDialog(false);
  };

  const saveRename = () => {
      if (selectedSessions.size !== 1) return;
      const id = Array.from(selectedSessions)[0];
      const newSessions = sessions.map(s => {
          if (s.sessionId === id) return { ...s, name: renameInput };
          return s;
      });
      localStorage.setItem('tapashya_sessions', JSON.stringify(newSessions));
      setSessions(newSessions);
      setShowRenameDialog(false);
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

  const qrData = generateDeepLink();

  // --- UI Colors & States ---
  const colorPrimary = "#00E5FF"; // Teal
  const colorAmber = "#FFC107";

  const statusText = activeState.isRunning ? `Focusing: ${activeState.sessionName}` : (activeState.isPaused ? "Paused" : "Ready to Focus");
  const waveColor = activeState.isPaused ? colorAmber : colorPrimary;
  const progressPercent = calculateProgress() * 100;

  const effectiveMinutesLive = Math.floor((Math.floor(displayElapsed / (15 * 60 * 1000)) * 15 * 60 * 1000) / 60000);
  const currentFragmentLive = Math.floor(effectiveMinutesLive / 15);
  const currentXpLive = currentFragmentLive * 10;

  return (
    <div className="min-h-screen bg-[#05050A] text-white font-sans pb-24 pt-8">
      <main className="max-w-md mx-auto px-4">
        {/* Android-like Header */}
        <div className="flex items-center justify-between pb-8">
          <div className="flex items-center gap-3">
            <Link href="/" className="p-2 -ml-2 rounded-full hover:bg-white/20 transition-colors text-gray-300">
              <ArrowLeft size={24} />
            </Link>
            <h1 className="text-2xl font-bold tracking-tight text-[#00E5FF] font-mono">Neural Focus</h1>
          </div>
          <div className="flex gap-2">
            {!calendarConnected ? (
              <a href="/api/auth/google" className="flex items-center gap-2 px-4 py-2 bg-[#00E5FF]/10 text-[#00E5FF] rounded-full text-sm font-bold hover:bg-[#B2DFDB] transition-colors">
                 <Calendar size={16} /> Connect
              </a>
            ) : (
              <button onClick={disconnectCalendar} className="flex items-center gap-2 px-4 py-2 bg-red-500/10 text-red-500 rounded-full text-sm font-bold hover:bg-red-500/20 transition-colors">
                 <Calendar size={16} /> Disconnect
              </button>
            )}
            <button onClick={handleOpenMiniMode} className="p-2 rounded-full hover:bg-white/20 transition-colors text-gray-300">
               <Minimize2 size={24} />
            </button>
            <button onClick={() => setShowSettings(true)} className="p-2 rounded-full hover:bg-white/20 transition-colors text-gray-300">
               <Settings size={24} />
            </button>
          </div>
        </div>

        {/* Clock View Area */}
        <div className="flex flex-col items-center mb-10">
            <div className="relative w-64 h-64 rounded-full bg-white/5 backdrop-blur-md shadow-2xl shadow-black/80 border border-white/10 flex items-center justify-center overflow-hidden mb-6">
                {/* Simulated Wave Background */}
                <div
                    className="absolute bottom-0 w-full transition-all duration-1000 ease-in-out opacity-20"
                    style={{ height: `${progressPercent}%`, backgroundColor: waveColor }}
                />
                <div className="relative z-10 flex flex-col items-center">
                    <span className="text-4xl font-mono font-bold tracking-tight text-gray-100">
                        {formatTime(displayElapsed)}
                    </span>
                    <span className="text-sm font-medium text-gray-400 mt-2 text-center px-4" style={{ color: activeState.isPaused ? colorAmber : colorPrimary }}>
                        {statusText}
                    </span>
                </div>
            </div>

            {/* Live Stats */}
            {(activeState.isRunning || activeState.isPaused) && (
                <div className="bg-white/5 backdrop-blur-md px-6 py-3 rounded-full shadow-2xl shadow-black/80 border border-white/10 flex items-center gap-6 mb-6">
                    <span className="text-sm font-bold text-gray-300">⚡ {currentXpLive} XP</span>
                    <span className="text-sm font-medium text-gray-400 border-l pl-6">Fragment {currentFragmentLive}</span>
                </div>
            )}

            {/* Rest Timer */}
            {activeState.isPaused && (
                <div className="text-sm font-bold text-amber-600 mb-6 bg-amber-50 px-4 py-2 rounded-lg border border-amber-100">
                    Pause left: {formatTime((activeState.pauseLimitMins * 60 * 1000) - displayPause)}
                </div>
            )}

            {/* Control Buttons */}
            <div className="flex items-center gap-4">
                {(!activeState.isRunning && !activeState.isPaused) && (
                    <button
                        onClick={onStartClicked}
                        className="flex items-center gap-2 bg-[#00E5FF] text-white px-8 py-4 rounded-2xl font-bold shadow-md hover:bg-[#00B8D4] transition-transform active:scale-95 select-none"
                    >
                        <Play size={20} fill="currentColor" />
                        Start
                    </button>
                )}

                {activeState.isRunning && (
                    <button onClick={handlePause} className="flex items-center gap-2 bg-[#7B61FF] text-white px-8 py-4 rounded-2xl font-bold shadow-md hover:bg-[#5E48D6] transition-transform active:scale-95">
                        <Pause size={20} fill="currentColor" />
                        Pause
                    </button>
                )}

                {activeState.isPaused && (
                    <button onClick={handleResume} className="flex items-center gap-2 bg-[#00E5FF] text-white px-8 py-4 rounded-2xl font-bold shadow-md hover:bg-[#00B8D4] transition-transform active:scale-95">
                        <Play size={20} fill="currentColor" />
                        Resume
                    </button>
                )}

                {(activeState.isRunning || activeState.isPaused) && (
                    <>
                        <button onClick={() => handleStop(false)} className="p-4 rounded-2xl bg-[#B3261E] text-white shadow-md hover:bg-[#8C1D18] transition-transform active:scale-95">
                            <Square size={20} fill="currentColor" />
                        </button>
                        <button onClick={resetClock} className="p-4 rounded-2xl bg-white/10 text-gray-300 shadow-2xl shadow-black/80 hover:bg-gray-300 transition-transform active:scale-95">
                            <RotateCcw size={20} />
                        </button>
                        <button onClick={handleRunningEditClick} className="p-4 rounded-2xl bg-[#00E5FF]/20 text-[#00E5FF] shadow-2xl shadow-black/80 hover:bg-[#00B8D4]/30 transition-transform active:scale-95" title="Edit Start Time">
                            <Edit2 size={20} />
                        </button>
                    </>
                )}
            </div>
        </div>

        {/* Scheduled Sessions Section */}
        <div className="mt-8 mb-6">
            <h3 className="text-lg font-bold text-gray-100 px-2 mb-4 font-mono">Scheduled Sessions</h3>
            {!calendarConnected ? (
                <div className="bg-white/5 backdrop-blur-md rounded-2xl p-6 shadow-2xl shadow-black/80 border border-white/10 flex flex-col items-center justify-center text-center">
                    <Calendar size={32} className="text-gray-400 mb-3" />
                    <p className="text-gray-300 font-medium mb-4">Sync with your calendar to see study blocks.</p>
                    <a href="/api/auth/google" className="px-6 py-2 bg-[#00E5FF] text-white rounded-full font-bold shadow hover:bg-[#00B8D4] transition-colors">
                        Connect Calendar
                    </a>
                </div>
            ) : calendarEvents.length === 0 ? (
                <div className="bg-white/5 backdrop-blur-md rounded-2xl p-6 shadow-2xl shadow-black/80 border border-white/10 flex flex-col items-center text-center">
                    <p className="text-gray-400 font-medium">No events for today.</p>
                </div>
            ) : (
                <div className="space-y-3">
                    {calendarEvents.map((evt) => {
                        const isRecommended = recommendedEvent?.id === evt.id;
                        return (
                            <div key={evt.id} className={`bg-white/5 backdrop-blur-md rounded-2xl p-4 shadow-2xl shadow-black/80 border ${isRecommended ? 'border-[#00E5FF]' : 'border-white/10'} flex items-center justify-between`}>
                                <div>
                                    <h4 className="font-bold text-gray-100 text-sm">{evt.title}</h4>
                                    <p className="text-xs text-gray-400 font-medium mt-1">
                                        {new Date(evt.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} - {new Date(evt.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                    </p>
                                    {isRecommended && <span className="text-[10px] bg-[#00E5FF]/10 text-[#00E5FF] font-bold px-2 py-0.5 rounded uppercase mt-2 inline-block">Recommended</span>}
                                </div>
                                <button
                                    onClick={() => startSessionWithConfig(evt.title, Math.floor((evt.endTime - evt.startTime) / 60000), formPauseLimit)}
                                    className="p-2 bg-[#00E5FF] text-white rounded-full hover:bg-[#00B8D4] transition-colors"
                                >
                                    <Play size={16} fill="currentColor" />
                                </button>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>

        {/* Session History Section */}
        <div className="mt-8">
            {/* Day Navigator */}
            <div className="flex items-center justify-between mb-4">
                <button
                    onClick={handlePrevDay}
                    className="p-2 rounded-full hover:bg-white/20 transition-colors text-gray-300"
                    title="Previous Day"
                >
                    <ChevronLeft size={24} />
                </button>
                <div className="text-center flex-1">
                    <span className="text-lg font-bold text-[#00E5FF] font-mono">
                        {isToday(selectedDate) ? 'Today' : selectedDate.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })}
                    </span>
                </div>
                <button
                    onClick={handleNextDay}
                    disabled={isToday(selectedDate)}
                    className={`p-2 rounded-full transition-colors ${isToday(selectedDate) ? 'text-gray-300 cursor-not-allowed' : 'hover:bg-white/20 text-gray-300'}`}
                    title="Next Day"
                >
                    <ChevronRight size={24} />
                </button>
            </div>

            {/* Context Menu or Empty Header */}
            {selectedSessions.size > 0 && (
                <div className="bg-[#00E5FF]/10 rounded-2xl p-4 flex justify-between items-center mb-4 shadow-2xl shadow-black/80 border border-[#00E5FF]/20">
                    <div className="flex items-center gap-2">
                        <button onClick={() => setSelectedSessions(new Set())} className="p-2 -ml-2 rounded-full hover:bg-teal-200 text-[#00B8D4]">
                            <X size={20} />
                        </button>
                        <span className="font-bold text-[#00B8D4]">{selectedSessions.size} Selected</span>
                    </div>
                    <div className="flex items-center gap-1">
                        {selectedSessions.size === 1 && (
                            <>
                                <button onClick={handleRenameClick} className="p-2 rounded-full hover:bg-teal-200 text-[#00E5FF]" title="Rename">
                                    <Edit2 size={20} />
                                </button>
                                <button onClick={handleEditTimeClick} className="p-2 rounded-full hover:bg-teal-200 text-[#00E5FF]" title="Edit Time">
                                    <Clock size={20} />
                                </button>
                            </>
                        )}
                        <button onClick={() => setShowExportDialog(true)} className="p-2 rounded-full hover:bg-teal-200 text-[#00E5FF]" title="Export QR">
                            <QrCode size={20} />
                        </button>
                        <button onClick={handleDeleteSelected} className="p-2 rounded-full hover:bg-red-100 text-red-600" title="Delete">
                            <Trash2 size={20} />
                        </button>
                    </div>
                </div>
            )}

            {/* Sessions List for Selected Day */}
            <div className="space-y-3">
                {(() => {
                    const daySessions = groupedSessions[selectedDate.toLocaleDateString()] || [];

                    if (daySessions.length === 0) {
                        return (
                            <div className="text-center py-10">
                                <p className="text-gray-400 font-medium">No sessions recorded</p>
                            </div>
                        );
                    }

                    return daySessions.map(session => (
                        <div key={session.sessionId} className="bg-white/5 backdrop-blur-md rounded-2xl shadow-2xl shadow-black/80 border border-white/10 overflow-hidden">
                            <label className="flex items-center justify-between p-4 cursor-pointer hover:bg-[#05050A] transition-colors group">
                                <div className="flex items-center gap-4">
                                    <input
                                        type="checkbox"
                                        checked={selectedSessions.has(session.sessionId)}
                                        onChange={() => handleToggleSession(session.sessionId)}
                                        className="w-5 h-5 text-[#00E5FF] rounded border-white/20 focus:ring-[#00E5FF]"
                                    />
                                    <div>
                                        <div className="font-bold text-gray-100 text-base group-hover:text-[#00E5FF] transition-colors">{session.name}</div>
                                        <div className="text-xs text-gray-400 font-medium mt-1">
                                            {new Date(session.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} - {new Date(session.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                        </div>
                                    </div>
                                </div>
                                <div className="text-right">
                                    <div className="text-sm font-black text-[#7B61FF]">{formatMinutes(Math.floor(session.effectiveTimeMs / 60000))}</div>
                                    <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Effective</div>
                                </div>
                            </label>
                        </div>
                    ));
                })()}
            </div>
        </div>

      </main>


      {/* PiP Mini Mode */}
      {pipWindow && createPortal(
          <div className="flex flex-col items-center justify-center p-4 w-full h-full">
              <div className="text-sm font-bold text-[#00E5FF] mb-1 font-mono">{activeState.sessionName}</div>
              <div className="text-3xl font-mono font-bold text-white mb-4">{formatTime(displayElapsed)}</div>

              <div className="flex gap-4">
                  {activeState.isRunning ? (
                      <button onClick={handlePause} className="p-3 bg-[#7B61FF] text-white rounded-full hover:bg-[#5E48D6]">
                          <Pause size={20} fill="currentColor" />
                      </button>
                  ) : activeState.isPaused ? (
                      <button onClick={handleResume} className="p-3 bg-[#00E5FF] text-white rounded-full hover:bg-[#00B8D4]">
                          <Play size={20} fill="currentColor" />
                      </button>
                  ) : (
                      <button onClick={onStartClicked} className="p-3 bg-[#00E5FF] text-white rounded-full hover:bg-[#00B8D4]">
                          <Play size={20} fill="currentColor" />
                      </button>
                  )}
                  {(activeState.isRunning || activeState.isPaused) && (
                      <button onClick={() => handleStop(false)} className="p-3 bg-[#B3261E] text-white rounded-full hover:bg-[#8C1D18]">
                          <Square size={20} fill="currentColor" />
                      </button>
                  )}
              </div>
          </div>,
          pipWindow.document.body
      )}

      {/* Mini Mode Overlay */}
      {isMiniMode && (
          <div
              className="fixed z-[200] bg-[#05050A]/90 backdrop-blur-xl border border-white/10 rounded-3xl p-4 shadow-2xl flex flex-col items-center cursor-move"
              style={{ left: miniPos.x, top: miniPos.y, touchAction: 'none' }}
              onPointerDown={handlePointerDown}
              onPointerMove={handlePointerMove}
              onPointerUp={handlePointerUp}
              onPointerCancel={handlePointerUp}
          >
              <button onClick={(e) => { e.stopPropagation(); setIsMiniMode(false); }} className="absolute top-2 right-2 p-1 text-gray-400 hover:text-white rounded-full hover:bg-white/10">
                  <Maximize2 size={16} />
              </button>
              <div className="text-sm font-bold text-[#00E5FF] mb-1 font-mono">{activeState.sessionName}</div>
              <div className="text-2xl font-mono font-bold text-white mb-2">{formatTime(displayElapsed)}</div>

              <div className="flex gap-2" onPointerDown={(e) => e.stopPropagation()}>
                  {activeState.isRunning ? (
                      <button onClick={handlePause} className="p-2 bg-[#7B61FF] text-white rounded-full hover:bg-[#5E48D6]">
                          <Pause size={16} fill="currentColor" />
                      </button>
                  ) : activeState.isPaused ? (
                      <button onClick={handleResume} className="p-2 bg-[#00E5FF] text-white rounded-full hover:bg-[#00B8D4]">
                          <Play size={16} fill="currentColor" />
                      </button>
                  ) : (
                      <button onClick={onStartClicked} className="p-2 bg-[#00E5FF] text-white rounded-full hover:bg-[#00B8D4]">
                          <Play size={16} fill="currentColor" />
                      </button>
                  )}
                  {(activeState.isRunning || activeState.isPaused) && (
                      <button onClick={() => handleStop(false)} className="p-2 bg-[#B3261E] text-white rounded-full hover:bg-[#8C1D18]">
                          <Square size={16} fill="currentColor" />
                      </button>
                  )}
              </div>
          </div>
      )}


      {/* Start Session Dialog */}
      {showStartDialog && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
              <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={() => setShowStartDialog(false)}></div>
              <div className="relative bg-white/5 backdrop-blur-md w-full max-w-sm rounded-3xl shadow-2xl shadow-black/80 p-6 animate-in zoom-in-95 duration-200">
                  <h3 className="text-xl font-bold text-white mb-6">Start Session</h3>

                  <div className="space-y-6">
                      <div>
                          <label className="block text-sm font-bold text-gray-300 mb-2">Session Name</label>
                          <input
                              type="text"
                              value={formName}
                              onChange={(e) => setFormName(e.target.value)}
                              className="w-full bg-[#05050A]/50 text-white border border-white/20 rounded-xl px-4 py-3 focus:ring-2 focus:ring-[#00E5FF] focus:border-[#00E5FF] outline-none transition-shadow"
                          />
                      </div>

                      <div>
                          <div className="flex justify-between items-end mb-2">
                              <label className="text-sm font-bold text-gray-300">Target Time</label>
                              <span className="text-[#00E5FF] font-black">{formatMinutes(formTargetTime)}</span>
                          </div>
                          <input
                              type="range"
                              min="15" max="360" step="15"
                              value={formTargetTime}
                              onChange={(e) => setFormTargetTime(Number(e.target.value))}
                              className="w-full accent-[#00E5FF]"
                          />
                      </div>

                      <div>
                          <div className="flex justify-between items-end mb-2">
                              <label className="text-sm font-bold text-gray-300">Pause Limit</label>
                              <span className="text-[#00E5FF] font-black">{formatMinutes(formPauseLimit)}</span>
                          </div>
                          <input
                              type="range"
                              min="1" max="60" step="1"
                              value={formPauseLimit}
                              onChange={(e) => setFormPauseLimit(Number(e.target.value))}
                              className="w-full accent-[#00E5FF]"
                          />
                      </div>

                      <div className="flex gap-3 pt-2">
                          <button onClick={() => setShowStartDialog(false)} className="flex-1 py-3 text-gray-300 font-bold hover:bg-white/10 rounded-xl transition-colors">Cancel</button>
                          <button onClick={() => startSessionWithConfig(formName, formTargetTime, formPauseLimit)} className="flex-1 py-3 bg-[#00E5FF] text-white rounded-xl font-bold hover:bg-[#00B8D4] transition-colors">Start</button>
                      </div>
                  </div>
              </div>
          </div>
      )}

      {/* Rename Dialog */}
      {showRenameDialog && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
              <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={() => setShowRenameDialog(false)}></div>
              <div className="relative bg-white/5 backdrop-blur-md w-full max-w-sm rounded-3xl shadow-2xl shadow-black/80 p-6 animate-in zoom-in-95 duration-200">
                  <h3 className="text-xl font-bold text-white mb-4">Rename Session</h3>
                  <input
                      type="text"
                      value={renameInput}
                      onChange={(e) => setRenameInput(e.target.value)}
                      className="w-full bg-[#05050A]/50 text-white border border-white/20 rounded-xl px-4 py-3 mb-6 focus:ring-2 focus:ring-[#00E5FF] focus:border-[#00E5FF] outline-none"
                      autoFocus
                  />
                  <div className="flex gap-3">
                      <button onClick={() => setShowRenameDialog(false)} className="flex-1 py-3 text-gray-300 font-bold hover:bg-white/10 rounded-xl transition-colors">Cancel</button>
                      <button onClick={saveRename} className="flex-1 py-3 bg-[#00E5FF] text-white rounded-xl font-bold hover:bg-[#00B8D4] transition-colors">Save</button>
                  </div>
              </div>
          </div>
      )}

                  {/* Edit Running Time Dialog */}
      {showRunningEditDialog && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
              <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={() => setShowRunningEditDialog(false)}></div>
              <div className="relative bg-white/5 backdrop-blur-md w-full max-w-sm rounded-3xl shadow-2xl shadow-black/80 p-6 animate-in zoom-in-95 duration-200">
                  <h3 className="text-xl font-bold text-white mb-6">Edit Active Start Time</h3>
                  <div className="space-y-4 mb-6">
                      <div>
                          <label className="block text-sm font-bold text-gray-300 mb-2">Start Time</label>
                          <input
                              type="time"
                              value={runningEditStartTime}
                              onChange={(e) => setRunningEditStartTime(e.target.value)}
                              className="w-full bg-[#05050A]/50 text-white border border-white/20 rounded-xl px-4 py-3 focus:ring-2 focus:ring-[#00E5FF] focus:border-[#00E5FF] outline-none"
                          />
                      </div>
                  </div>
                  <div className="flex gap-3">
                      <button onClick={() => setShowRunningEditDialog(false)} className="flex-1 py-3 text-gray-300 font-bold hover:bg-white/10 rounded-xl transition-colors">Cancel</button>
                      <button onClick={saveRunningEditTime} className="flex-1 py-3 bg-[#00E5FF] text-white rounded-xl font-bold hover:bg-[#00B8D4] transition-colors">Save</button>
                  </div>
              </div>
          </div>
      )}

      {/* Edit Time Dialog */}
      {showEditTimeDialog && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
              <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={() => setShowEditTimeDialog(false)}></div>
              <div className="relative bg-white/5 backdrop-blur-md w-full max-w-sm rounded-3xl shadow-2xl shadow-black/80 p-6 animate-in zoom-in-95 duration-200">
                  <h3 className="text-xl font-bold text-white mb-6">Edit Time</h3>
                  <div className="space-y-4 mb-6">
                      <div>
                          <label className="block text-sm font-bold text-gray-300 mb-2">Start Time</label>
                          <input
                              type="time"
                              value={editStartTime}
                              onChange={(e) => setEditStartTime(e.target.value)}
                              className="w-full bg-[#05050A]/50 text-white border border-white/20 rounded-xl px-4 py-3 focus:ring-2 focus:ring-[#00E5FF] focus:border-[#00E5FF] outline-none"
                          />
                      </div>
                      <div>
                          <label className="block text-sm font-bold text-gray-300 mb-2">End Time</label>
                          <input
                              type="time"
                              value={editEndTime}
                              onChange={(e) => setEditEndTime(e.target.value)}
                              className="w-full bg-[#05050A]/50 text-white border border-white/20 rounded-xl px-4 py-3 focus:ring-2 focus:ring-[#00E5FF] focus:border-[#00E5FF] outline-none"
                          />
                      </div>
                  </div>
                  <div className="flex gap-3">
                      <button onClick={() => setShowEditTimeDialog(false)} className="flex-1 py-3 text-gray-300 font-bold hover:bg-white/10 rounded-xl transition-colors">Cancel</button>
                      <button onClick={saveEditTime} className="flex-1 py-3 bg-[#00E5FF] text-white rounded-xl font-bold hover:bg-[#00B8D4] transition-colors">Save</button>
                  </div>
              </div>
          </div>
      )}

      {/* Export QR Dialog */}
      {showExportDialog && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
              <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={() => setShowExportDialog(false)}></div>
              <div className="relative bg-white/5 backdrop-blur-md w-full max-w-sm rounded-3xl shadow-2xl shadow-black/80 p-8 animate-in zoom-in-95 duration-200 flex flex-col items-center text-center">
                  <button onClick={() => setShowExportDialog(false)} className="absolute top-4 right-4 p-2 bg-white/10 rounded-full hover:bg-white/20">
                      <X size={20} />
                  </button>
                  <h3 className="text-xl font-bold text-[#00B8D4] mb-2">App Sync</h3>
                  <p className="text-sm text-gray-400 mb-6">Scan with Reality app to import {selectedSessions.size} session(s).</p>

                  {qrData && (
                      <div className="p-4 bg-white/5 backdrop-blur-md rounded-2xl shadow-2xl shadow-black/80 border border-white/10 mb-2">
                          <QRCodeSVG value={qrData} size={200} level="L" includeMargin={false} />
                      </div>
                  )}
              </div>
          </div>
      )}

      {/* Settings Bottom Sheet */}
      {showSettings && (
          <div className="fixed inset-0 z-[100] flex items-end justify-center sm:items-center p-4">
              <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={() => setShowSettings(false)}></div>
              <div className="relative bg-white/5 backdrop-blur-md w-full max-w-md rounded-t-3xl sm:rounded-3xl shadow-2xl shadow-black/80 p-6 animate-in slide-in-from-bottom-8">
                  <div className="flex justify-between items-center mb-6">
                      <h3 className="text-xl font-bold text-white">Default Settings</h3>
                      <button onClick={() => setShowSettings(false)} className="p-2 bg-white/10 rounded-full hover:bg-white/20">
                          <X size={20} />
                      </button>
                  </div>

                  <div className="space-y-6">
                      <div>
                          <div className="flex justify-between items-end mb-2">
                              <label className="font-bold text-gray-300">Target Duration</label>
                              <span className="text-[#00E5FF] font-black">{formatMinutes(formTargetTime)}</span>
                          </div>
                          <input
                              type="range"
                              min="15" max="360" step="15"
                              value={formTargetTime}
                              onChange={(e) => setFormTargetTime(Number(e.target.value))}
                              className="w-full accent-[#00E5FF]"
                          />
                      </div>

                      <div>
                          <div className="flex justify-between items-end mb-2">
                              <label className="font-bold text-gray-300">Pause Limit</label>
                              <span className="text-[#00E5FF] font-black">{formatMinutes(formPauseLimit)}</span>
                          </div>
                          <input
                              type="range"
                              min="1" max="60" step="1"
                              value={formPauseLimit}
                              onChange={(e) => setFormPauseLimit(Number(e.target.value))}
                              className="w-full accent-[#00E5FF]"
                          />
                      </div>

                      <button
                          onClick={() => setShowSettings(false)}
                          className="w-full py-4 bg-[#00E5FF] text-white rounded-2xl font-bold shadow-md hover:bg-[#00B8D4] active:scale-95 transition-transform"
                      >
                          Save Defaults
                      </button>
                  </div>
              </div>
          </div>
      )}
    </div>
  );
}
