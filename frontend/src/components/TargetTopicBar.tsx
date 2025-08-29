import React from "react";

type Props = {
  value: string;
  onChange: (v: string) => void;
  status?: string;
};

export default function TargetTopicBar({ value, onChange, status }: Props) {
  const inputId = React.useId();

  const toneClass = React.useMemo(() => {
    const s = status?.toLowerCase() || "";
    if (!s) return "text-slate-400";
    if (s.includes("fail") || s.includes("error")) return "text-rose-300";
    if (s.includes("ok") || s.includes("replayed") || s.includes("success")) return "text-emerald-300";
    if (s.includes("replay") || s.includes("loading") || s.includes("…")) return "text-indigo-300";
    return "text-slate-300";
  }, [status]);

  return (
    <div className="flex items-center gap-3 rounded-xl border border-white/10 bg-[#0F162B] px-4 py-2">
      <label htmlFor={inputId} className="text-sm font-medium text-slate-300">
        Target topic
      </label>

      <input
        id={inputId}
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="e.g. orders"
        className="flex-1 rounded-md border border-white/10 bg-white/5 px-3 py-1.5 text-sm text-slate-100 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500"
      />

      <span
        className={`text-xs ${toneClass}`}
        aria-live="polite"
        aria-atomic="true"
      >
        {status}
      </span>
    </div>
  );
}
