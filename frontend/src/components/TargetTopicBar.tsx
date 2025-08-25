import React from 'react';

type Props = {
  value: string;
  onChange: (v: string) => void;
  status?: string;
};

export default function TargetTopicBar({ value, onChange, status }: Props) {
  return (
    <div className="bar">
      <label>Target topic:</label>
      <input
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder="e.g. orders"
      />
      <span className="muted">{status}</span>
    </div>
  );
}
