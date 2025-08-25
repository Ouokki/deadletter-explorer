import React from 'react';

type Props = {
  value: string;
  onChange: (v: string) => void;
  status?: string;
};

export default function TargetTopicBar({ value, onChange, status }: Props) {
  return (
    <div className="flex items-center gap-3 bg-white border rounded-lg px-4 py-2 shadow-sm">
      <label className="text-sm font-medium text-gray-700">Target topic:</label>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="e.g. orders"
        className="flex-1 rounded-md border-gray-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 text-sm px-3 py-1.5"
      />
      <span className="text-sm text-gray-500">{status}</span>
    </div>

  );
}
