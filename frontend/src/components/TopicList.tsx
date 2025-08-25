import React from 'react';

type Props = {
  topics: string[];
  selected: string | null;
  onSelect: (t: string) => void;
};

export default function TopicList({ topics, selected, onSelect }: Props) {
  if (!topics.length) return <div className="muted">No DLQ topics found yet</div>;
  return (
    <ul className="space-y-1">
      {topics.map((t) => (
        <li key={t}>
          <button
            onClick={() => onSelect(t)}
            className={`w-full text-left px-3 py-2 rounded-md transition-colors ${
              selected === t
                ? "bg-blue-600 text-white font-medium"
                : "hover:bg-gray-100 text-gray-700"
            }`}
          >
            {t}
          </button>
        </li>
      ))}
    </ul>

  );
}
