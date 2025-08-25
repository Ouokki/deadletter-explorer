import React from 'react';

type Props = {
  topics: string[];
  selected: string | null;
  onSelect: (t: string) => void;
};

export default function TopicList({ topics, selected, onSelect }: Props) {
  if (!topics.length) return <div className="muted">No DLQ topics found yet</div>;
  return (
    <ul className="list">
      {topics.map(t => (
        <li key={t}>
          <button
            onClick={() => onSelect(t)}
            className={`listItem ${selected === t ? 'active' : ''}`}
          >
            {t}
          </button>
        </li>
      ))}
    </ul>
  );
}
