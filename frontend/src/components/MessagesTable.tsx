import React from 'react';
import { MessageDto } from '../types/types';
import { formatTimestamp } from '../utils/format';

type Props = {
  messages: MessageDto[];
  onReplay: (m: MessageDto) => void;
  emptyText?: string;
};

export default function MessagesTable({ messages, onReplay, emptyText = 'No data' }: Props) {
  if (!messages.length) {
    return <div className="muted">{emptyText}</div>;
  }

  return (
    <table className="min-w-full border border-gray-200 text-sm">
      <thead className="bg-gray-50 text-left">
        <tr>
          <th className="px-4 py-2 font-medium text-gray-600">Partition</th>
          <th className="px-4 py-2 font-medium text-gray-600">Offset</th>
          <th className="px-4 py-2 font-medium text-gray-600">Timestamp</th>
          <th className="px-4 py-2 font-medium text-gray-600">Key</th>
          <th className="px-4 py-2 font-medium text-gray-600">
            Value (UTF-8 or base64)
          </th>
          <th className="px-4 py-2 font-medium text-gray-600">Actions</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-gray-200">
        {messages.map((m) => (
          <tr
            key={`${m.partition}-${m.offset}`}
            className="hover:bg-gray-50 transition-colors"
          >
            <td className="px-4 py-2">{m.partition}</td>
            <td className="px-4 py-2">{m.offset}</td>
            <td className="px-4 py-2">{formatTimestamp(m.timestamp)}</td>
            <td className="px-4 py-2">
              <code className="rounded bg-gray-100 px-1 py-0.5 text-xs font-mono text-gray-800">
                {m.keyUtf8 ?? ""}
              </code>
            </td>
            <td className="px-4 py-2">
              <pre className="max-w-xs overflow-x-auto rounded bg-gray-100 p-2 text-xs font-mono text-gray-800">
                {m.valueUtf8 ??
                  `(base64) ${m.valueBase64?.slice(0, 80)}…`}
              </pre>
            </td>
            <td className="px-4 py-2">
              <button
                onClick={() => onReplay(m)}
                className="rounded-md bg-blue-600 px-3 py-1 text-xs font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                Replay
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>

  );
}
