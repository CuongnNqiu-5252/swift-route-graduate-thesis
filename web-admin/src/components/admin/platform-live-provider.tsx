'use client';

import { createContext, useContext, useEffect, useState } from 'react';
import { platformRepository, type PlatformSnapshot } from '@/infrastructure/repositories/platform.repository';
import { useLocale } from '@/i18n/provider';

type Status = 'connecting' | 'live' | 'reconnecting';
const Context = createContext<{ snapshot: PlatformSnapshot | null; status: Status; generation: number }>({ snapshot: null, status: 'connecting', generation: 0 });
export const usePlatformLive = () => useContext(Context);

export function PlatformLiveProvider({ children }: { children: React.ReactNode }) {
  const [snapshot, setSnapshot] = useState<PlatformSnapshot | null>(null);
  const [status, setStatus] = useState<Status>('connecting');
  const [generation, setGeneration] = useState(0);
  useEffect(() => {
    const lifetime = new AbortController();
    let attempts = 0;
    async function connect() {
      while (!lifetime.signal.aborted) {
        const request = new AbortController();
        const abort = () => request.abort();
        lifetime.signal.addEventListener('abort', abort, { once: true });
        let watchdog: number | undefined;
        const alive = () => {
          window.clearTimeout(watchdog);
          watchdog = window.setTimeout(() => request.abort(), 45_000);
        };
        let refreshing = false;
        let pending = false;
        let initial = true;
        let resync = false;
        async function refresh(reset = false) {
          pending = true; resync ||= reset;
          if (refreshing) return;
          refreshing = true;
          try {
            // Serialize/coalesce pushed invalidations, including ones received
            // while the initial snapshot is being read. There is no polling timer.
            while (pending && !request.signal.aborted) {
              pending = false;
              const resetCursor = resync; resync = false;
              const data = await platformRepository.snapshot(request.signal);
              if (request.signal.aborted) return;
              setSnapshot(data);
              if (initial || resetCursor) setGeneration((value) => value + 1);
              initial = false; setStatus('live'); attempts = 0;
            }
          } catch { request.abort(); }
          finally { refreshing = false; }
        }
        try {
          alive();
          await platformRepository.stream(request.signal, (event) => {
            if (lifetime.signal.aborted) return;
            alive();
            if (event === 'connect' || event === 'platform-resync') void refresh(true);
            else if (event === 'platform-audit') void refresh();
          });
        } catch { /* Reconnection is driven by stream failure/end, never a data polling timer. */ }
        finally { request.abort(); window.clearTimeout(watchdog); lifetime.signal.removeEventListener('abort', abort); }
        if (lifetime.signal.aborted) return;
        setStatus('reconnecting');
        const delay = Math.min(30_000, 1000 * 2 ** Math.min(attempts++, 5)) + Math.random() * 400;
        await new Promise<void>((resolve) => {
          const done = () => { window.clearTimeout(timer); lifetime.signal.removeEventListener('abort', done); resolve(); };
          const timer = window.setTimeout(done, delay);
          lifetime.signal.addEventListener('abort', done, { once: true });
        });
      }
    }
    void connect();
    return () => lifetime.abort();
  }, []);
  return <Context.Provider value={{ snapshot, status, generation }}>{children}</Context.Provider>;
}

export function PlatformConnection() {
  const { status } = usePlatformLive();
  const { t } = useLocale();
  return <span role="status" className="inline-flex items-center gap-2 text-xs font-medium text-slate-600">
    <span aria-hidden="true" className={`size-2 rounded-full ${status === 'live' ? 'bg-emerald-600' : 'bg-amber-600'}`} />
    {t(status === 'live' ? 'Đang nhận sự kiện trực tiếp' : status === 'connecting' ? 'Đang kết nối nhật ký…' : 'Mất kết nối · đang kết nối lại')}
  </span>;
}
