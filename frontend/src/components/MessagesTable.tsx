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
    <table className="table">
      <thead>
        <tr>
          <th>Partition</th>
          <th>Offset</th>
          <th>Timestamp</th>
          <th>Key</th>
          <th>Value (UTF-8 or base64)</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        {messages.map(m => (
          <tr key={`${m.partition}-${m.offset}`}>
            <td>{m.partition}</td>
            <td>{m.offset}</td>
            <td>{formatTimestamp(m.timestamp)}</td>
            <td><code className="code">{m.keyUtf8 ?? ''}</code></td>
            <td>
              <pre className="pre">
                {m.valueUtf8 ?? `(base64) ${m.valueBase64?.slice(0, 80)}…`}
              </pre>
            </td>
            <td>
              <button onClick={() => onReplay(m)}>Replay →</button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
