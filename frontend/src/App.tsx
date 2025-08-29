import React from "react";
import { useTopics } from "./hooks/useTopics";
import { useMessages } from "./hooks/useMessages";
import { replay } from "./services/api";
import { MessageDto } from "./types/types";
import TopicList from "./components/TopicList";
import MessagesTable from "./components/MessagesTable";
import { useNavigate } from "react-router-dom";

// ----- UI tokens (aligned with the navbar) -----
const cx = (...cls: Array<string | false | null | undefined>) =>
  cls.filter(Boolean).join(" ");

const buttonBase =
  "inline-flex items-center justify-center rounded-lg px-3.5 py-2 text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-slate-900";

const buttonGhost = cx(
  buttonBase,
  "border border-slate-700/70 text-slate-200 hover:bg-slate-800/60"
);

const buttonPrimary = cx(
  buttonBase,
  "bg-gradient-to-b from-blue-600 to-blue-700 hover:from-blue-500 hover:to-blue-600 text-white focus:ring-blue-500"
);

export default function App() {
  const [selectedTopic, setSelectedTopic] = React.useState<string | null>(null);
  const [targetTopic, setTargetTopic] = React.useState("");
  const [status, setStatus] = React.useState<string>("");
  const { topics, error: topicsError } = useTopics();
  const { messages, loading } = useMessages(selectedTopic, 200);

  // Quick-jump topic for Redaction Studio
  const [studioTopic, setStudioTopic] = React.useState<string>("orders-DLQ");
  const navigate = useNavigate();

  if (topicsError && !status) setStatus(String(topicsError));

  const replayOne = async (m: MessageDto) => {
    if (!targetTopic.trim()) {
      setStatus("Please specify a target topic to replay to.");
      return;
    }
    setStatus("Replaying 1 message…");
    try {
      await replay(targetTopic, [
        {
          partition: m.partition,
          offset: m.offset,
          valueBase64: m.valueBase64 ?? undefined,
          headersBase64: m.headers,
          topic: m.topic ?? selectedTopic ?? undefined,
        },
      ]);
      setStatus(`OK → replayed to ${targetTopic}`);
    } catch (e: unknown) {
      setStatus("Replay failed: " + (e instanceof Error ? e.message : String(e)));
    }
  };

  const goToStudio: React.FormEventHandler<HTMLFormElement> = (e) => {
    e.preventDefault();
    const t = studioTopic.trim();
    if (!t) return;
    navigate(`/topics/${encodeURIComponent(t)}/redaction`);
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-950 to-slate-900 text-slate-100">
      {/* Page header (coherent with navbar) */}
      <header className="sticky top-0 z-30 border-b border-white/5 bg-slate-900/70 backdrop-blur px-4 md:px-6">
        <div className="mx-auto max-w-7xl py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="inline-block h-2.5 w-2.5 rounded-full bg-emerald-400 shadow-[0_0_10px_1px_rgba(16,185,129,0.55)]" />
              <h1 className="text-lg font-semibold tracking-tight">
                Dead Letter Explorer
              </h1>
            </div>
            {/* Toolbar: Studio quick-jump */}
            <form onSubmit={goToStudio} className="hidden items-center gap-2 md:flex">
              <input
                value={studioTopic}
                onChange={(e) => setStudioTopic(e.target.value)}
                placeholder="orders-DLQ"
                className="w-56 rounded-md border border-slate-700/70 bg-slate-800/70 px-3 py-1.5 text-sm text-slate-100 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <button type="submit" className={buttonGhost} title="Open Redaction Studio">
                Redaction Studio
              </button>
            </form>
          </div>

          <p className="mt-1 text-xs text-slate-400">
            Inspect & replay messages from Kafka DLQs (local MVP)
          </p>

          {/* Status line */}
          {status && (
            <div
              className="mt-3 rounded-md border border-slate-700/60 bg-slate-900/60 px-3 py-2 text-xs text-slate-300"
              role="status"
              aria-live="polite"
            >
              {status}
            </div>
          )}

          {/* Mobile Studio quick-jump */}
          <form onSubmit={goToStudio} className="mt-3 flex items-center gap-2 md:hidden">
            <input
              value={studioTopic}
              onChange={(e) => setStudioTopic(e.target.value)}
              placeholder="orders-DLQ"
              className="flex-1 rounded-md border border-slate-700/70 bg-slate-800/70 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button type="submit" className={buttonGhost}>
              Studio
            </button>
          </form>
        </div>
      </header>

      {/* Content */}
      <div className="mx-auto max-w-7xl px-4 py-5 md:px-6">
        {/* Replay target toolbar */}
        <section className="mb-5 rounded-xl border border-white/5 bg-slate-900/60 p-3 backdrop-blur">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="flex items-center gap-3">
              <label className="text-sm text-slate-300" htmlFor="replay-target">
                Target topic
              </label>
              <input
                id="replay-target"
                value={targetTopic}
                onChange={(e) => setTargetTopic(e.target.value)}
                placeholder="e.g. orders"
                className="w-64 rounded-md border border-slate-700/70 bg-slate-800/70 px-3 py-1.5 text-sm text-slate-100 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <span className="text-xs text-slate-400">
              Select a message → click “Replay” in the table
            </span>
          </div>
        </section>

        {/* Layout */}
        <div className="grid grid-cols-1 gap-5 lg:grid-cols-12">
          {/* Sidebar */}
          <aside className="lg:col-span-3 rounded-2xl border border-white/5 bg-slate-900/60 p-4 shadow-sm backdrop-blur">
            <h2 className="mb-2 text-sm font-medium text-slate-200">DLQ Topics</h2>
            <div className="text-slate-200/90">
              <TopicList topics={topics} selected={selectedTopic} onSelect={setSelectedTopic} />
            </div>
          </aside>

          {/* Main */}
          <main className="lg:col-span-9 rounded-2xl border border-white/5 bg-slate-900/60 p-4 shadow-sm backdrop-blur">
            <div className="mb-3 flex items-center justify-between border-b border-white/5 pb-2">
              <h2 className="text-sm font-medium text-slate-200">
                Messages {selectedTopic ? `in ${selectedTopic}` : ""}
              </h2>
              {loading && <span className="text-xs text-slate-400">Loading…</span>}
            </div>

            <div className="max-h-[70vh] overflow-auto text-slate-100">
              <MessagesTable
                messages={messages}
                onReplay={replayOne}
                emptyText="No messages yet"
              />
            </div>
          </main>
        </div>

        {/* Bottom actions (optional): another Studio jump */}
        <div className="mt-6 flex items-center justify-end">
          <form onSubmit={goToStudio} className="flex items-center gap-2">
            <input
              value={studioTopic}
              onChange={(e) => setStudioTopic(e.target.value)}
              placeholder="topic (e.g. orders-DLQ)"
              className="w-56 rounded-md border border-slate-700/70 bg-slate-800/70 px-3 py-1.5 text-sm text-slate-100 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button type="submit" className={buttonPrimary} title="Open Redaction Studio">
              Redaction Studio
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
