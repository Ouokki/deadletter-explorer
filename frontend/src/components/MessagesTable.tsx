import React from 'react';
import { MessageDto } from '../types/types';
import { formatTimestamp } from '../utils/format';

type Props = {
  messages: MessageDto[];
  onReplay: (m: MessageDto) => void;
  emptyText?: string;
};

export default function MessagesTable({
  messages,
  onReplay,
  emptyText = 'No data',
}: Props) {
  if (!messages.length) {
    return (
      <div className="rounded-xl border border-white/10 bg-[#0F162B] p-6 text-center text-sm text-slate-400">
        {emptyText}
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-xl border border-white/10 bg-[#0F162B]">
      <table className="min-w-full text-sm">
        {/* Sticky, opaque header prevents hover showing behind it */}
        <thead className="sticky top-0 z-10 bg-[#0F162B]">
          <tr className="border-b border-white/10 text-left">
            <th className="px-3 py-2 text-xs font-medium text-slate-400">Partition</th>
            <th className="px-3 py-2 text-xs font-medium text-slate-400">Offset</th>
            <th className="px-3 py-2 text-xs font-medium text-slate-400">Timestamp</th>
            <th className="px-3 py-2 text-xs font-medium text-slate-400">Key</th>
            <th className="px-3 py-2 text-xs font-medium text-slate-400">Value (UTF-8 or base64)</th>
            <th className="px-3 py-2 text-right text-xs font-medium text-slate-400">Actions</th>
          </tr>
        </thead>

        <tbody className="divide-y divide-white/5">
          {messages.map((m) => (
            <tr
              key={`${m.partition}-${m.offset}`}
              className="hover:bg-white/5 transition-colors"
            >
              <td className="px-3 py-2">{m.partition}</td>
              <td className="px-3 py-2">{m.offset}</td>
              <td className="px-3 py-2">{formatTimestamp(m.timestamp)}</td>

              <td className="px-3 py-2">
                <code className="rounded bg-black/30 px-1 py-0.5 text-xs font-mono text-slate-200">
                  {m.keyUtf8 ?? ''}
                </code>
              </td>

              <td className="px-3 py-2">
                <pre className="max-w-[42rem] overflow-x-auto rounded bg-black/30 p-2 text-xs font-mono text-slate-200">
                  {m.valueUtf8 ??
                    `(base64) ${
                      m.valueBase64
                        ? m.valueBase64.slice(0, 80) + (m.valueBase64.length > 80 ? '…' : '')
                        : ''
                    }`}
                </pre>
              </td>

              <td className="px-3 py-2 text-right">
                <button
                  onClick={() => onReplay(m)}
                  className="rounded-md bg-indigo-600 px-3 py-1 text-xs font-medium text-white hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                >
                  Replay
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
