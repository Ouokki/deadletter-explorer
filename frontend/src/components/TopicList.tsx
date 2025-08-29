import React from "react";

type Props = {
  topics: string[];
  selected: string | null;
  onSelect: (t: string) => void;
};

export default function TopicList({ topics, selected, onSelect }: Props) {
  if (!topics.length) {
    return (
      <div className="rounded-md border border-white/10 bg-white/5 p-3 text-sm text-slate-400">
        No DLQ topics found yet
      </div>
    );
  }

  return (
    <ul className="space-y-1" role="listbox" aria-label="DLQ topics">
      {topics.map((t) => {
        const active = selected === t;
        return (
          <li key={t}>
            <button
              type="button"
              onClick={() => onSelect(t)}
              title={t}
              role="option"
              aria-selected={active}
              className={[
                "group w-full text-left px-3 py-2 rounded-md transition-colors focus:outline-none",
                "focus:ring-2 focus:ring-indigo-500",
                "flex items-center gap-2",
                active
                  ? "bg-white/5 text-indigo-100 ring-1 ring-indigo-500/40"
                  : "text-slate-200 hover:bg-white/5"
              ].join(" ")}
            >
              <span
                className={[
                  "h-1.5 w-1.5 rounded-full",
                  active ? "bg-indigo-400" : "bg-white/20 group-hover:bg-white/30"
                ].join(" ")}
                aria-hidden="true"
              />
              <span className="truncate">{t}</span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
