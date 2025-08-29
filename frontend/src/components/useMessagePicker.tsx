// src/redaction/useMessagePicker.tsx
import React from 'react';
import { getMessages } from '../services/api';
import type { MessageDto } from '../types/types';

function isAbortError(err: unknown) {
  return !!err && (err as any).name === 'AbortError';
}

export function useMessagePicker(topic: string | undefined) {
  const [open, setOpen] = React.useState(false);
  const resolver = React.useRef<((v: string | undefined) => void) | null>(null);

  const openPicker = React.useCallback(() => {
    return new Promise<string | undefined>((resolve) => {
      resolver.current = resolve;
      setOpen(true);
    });
  }, []);

  const closePicker = React.useCallback((value?: string) => {
    setOpen(false);
    resolver.current?.(value);
    resolver.current = null;
  }, []);

  const modal = open ? (
    <MessagePickerModal topic={topic} onClose={closePicker} />
  ) : null;

  return { openPicker, modal };
}

function pretty(s: string) {
  try { return JSON.stringify(JSON.parse(s), null, 2); } catch { return s; }
}

function MessagePickerModal({
  topic,
  onClose,
}: {
  topic?: string;
  onClose: (value?: string) => void;
}) {
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [items, setItems] = React.useState<MessageDto[]>([]);
  const [selected, setSelected] = React.useState<MessageDto | null>(null);
  const [filter, setFilter] = React.useState('');

  React.useEffect(() => {
    if (!topic) return;
    const ac = new AbortController();
    setLoading(true);
    setError(null);
    getMessages(topic, 200, { signal: ac.signal })
      .then((msgs) => {
        setItems(msgs);
        setSelected(msgs[0] ?? null);
      })
      .catch((e) => { if (!isAbortError(e)) setError((e as Error).message || String(e)); })
      .finally(() => { if (!ac.signal.aborted) setLoading(false); });
    return () => ac.abort();
  }, [topic]);

  // Close on ESC
  React.useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(undefined); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const filtered = React.useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return items;
    return items.filter((m) =>
      (m.keyUtf8 ?? '').toLowerCase().includes(q) ||
      String(m.offset).includes(q) ||
      String(m.partition).includes(q)
    );
  }, [items, filter]);

  const primaryBtn =
    "rounded-lg bg-indigo-600 px-3 py-1.5 text-sm text-white hover:bg-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed";
  const ghostBtn =
    "rounded-md border border-white/10 px-3 py-1.5 text-sm text-slate-200 hover:bg-white/5";

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="picker-title"
    >
      <div className="w-full max-w-5xl rounded-2xl border border-white/10 bg-[#0F162B] shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-white/10 p-4">
          <div>
            <h2 id="picker-title" className="text-lg font-semibold text-slate-100">Pick a DLQ message</h2>
            <p className="text-xs text-slate-400">
              Topic:{' '}
              <code className="rounded border border-white/10 bg-black/20 px-1.5 py-0.5">
                {topic}
              </code>
            </p>
          </div>
          <button className={ghostBtn} onClick={() => onClose(undefined)}>
            Cancel
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-5">
          {/* Left: list */}
          <div className="md:col-span-2 border-r border-white/10 p-3">
            <div className="mb-2 flex items-center gap-2">
              <input
                className="w-full rounded-md border border-white/10 bg-white/5 px-3 py-1.5 text-sm text-slate-100 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                placeholder="Filter by key / offset / partition…"
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
              />
            </div>

            {loading && (
              <div className="rounded-lg border border-white/10 bg-[#0F162B] p-4 text-sm text-slate-300">
                Loading…
              </div>
            )}
            {error && (
              <div className="rounded-lg border border-rose-800/40 bg-rose-900/30 p-3 text-sm text-rose-200">
                {error}
              </div>
            )}

            {!loading && !error && (
              <div className="max-h-[420px] overflow-auto rounded-lg border border-white/10 bg-[#0F162B]">
                <table className="w-full text-left text-sm">
                  <thead className="sticky top-0 z-10 bg-[#0F162B]">
                    <tr className="border-b border-white/10">
                      <th className="px-3 py-2 text-xs font-medium text-slate-400">Part</th>
                      <th className="px-3 py-2 text-xs font-medium text-slate-400">Offset</th>
                      <th className="px-3 py-2 text-xs font-medium text-slate-400">Timestamp</th>
                      <th className="px-3 py-2 text-xs font-medium text-slate-400">Key</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/5">
                    {filtered.map((m) => {
                      const isActive = selected?.offset === m.offset && selected?.partition === m.partition;
                      return (
                        <tr
                          key={`${m.partition}-${m.offset}`}
                          className={`cursor-pointer transition-colors hover:bg-white/5 ${isActive ? 'bg-white/5 ring-1 ring-indigo-500/40' : ''}`}
                          onClick={() => setSelected(m)}
                        >
                          <td className="px-3 py-2">{m.partition}</td>
                          <td className="px-3 py-2 font-mono">{m.offset}</td>
                          <td className="px-3 py-2">
                            {m.timestamp ? new Date(m.timestamp).toLocaleString() : ''}
                          </td>
                          <td className="px-3 py-2">
                            <code className="rounded bg-black/30 px-1 py-0.5 font-mono text-xs text-slate-200">
                              {m.keyUtf8 ?? ''}
                            </code>
                          </td>
                        </tr>
                      );
                    })}
                    {filtered.length === 0 && (
                      <tr>
                        <td colSpan={4} className="px-3 py-6 text-center text-sm text-slate-400">
                          No messages.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Right: preview */}
          <div className="md:col-span-3 p-3">
            <div className="mb-2 flex items-center justify-between">
              <div className="text-sm text-slate-400">
                Preview (UTF-8){' '}
                <span className="text-slate-500">—</span>{' '}
                {selected ? `partition ${selected.partition}, offset ${selected.offset}` : '—'}
              </div>
              <button
                className={primaryBtn}
                disabled={!selected?.valueUtf8}
                onClick={() => onClose(selected?.valueUtf8 ?? undefined)}
              >
                Use this as sample
              </button>
            </div>

            <div className="h-[460px] overflow-auto rounded-xl border border-white/10 bg-black/20 p-3">
              {selected?.valueUtf8 ? (
                <pre className="whitespace-pre-wrap text-xs leading-relaxed text-slate-200">
                  {pretty(selected.valueUtf8)}
                </pre>
              ) : (
                <div className="text-sm text-slate-400">Empty or non-UTF8 message.</div>
              )}

              {selected?.valueBase64 && !selected.valueUtf8 && (
                <div className="mt-3 rounded-md border border-amber-900/40 bg-amber-900/20 p-2 text-xs text-amber-200">
                  Payload appears non-UTF8. You can still select it; Studio will show raw text.
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="flex justify-end gap-3 border-t border-white/10 p-3">
          <button className={ghostBtn} onClick={() => onClose(undefined)}>
            Cancel
          </button>
          <button
            className={primaryBtn}
            disabled={!selected?.valueUtf8}
            onClick={() => onClose(selected?.valueUtf8 ?? undefined)}
          >
            Use this as sample
          </button>
        </div>
      </div>
    </div>
  );
}
