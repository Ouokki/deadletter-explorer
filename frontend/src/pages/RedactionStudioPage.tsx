// redaction/RedactionStudioPage.tsx
import React from 'react';
import { useParams, Link } from 'react-router-dom';
import RedactionStudio from '../components/RedactionStudio';
import {
  getRedactionRules,
  saveRedactionRules,
  validateJsonPath,
  getLastMessageUtf8,
  type Rule,
} from '../services/api';

function isAbortError(err: unknown): boolean {
  return !!err && (err as any).name === 'AbortError';
}

export default function RedactionStudioPage() {
  const { topic } = useParams<{ topic: string }>();
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [initialRules, setInitialRules] = React.useState<Rule[]>([]);

  React.useEffect(() => {
    if (!topic) return;
    const ac = new AbortController();

    setLoading(true);
    setError(null);

    getRedactionRules('topic', topic, { signal: ac.signal })
      .then((rules) => {
        if (ac.signal.aborted) return;
        setInitialRules(rules ?? []);
      })
      .catch((e) => {
        if (isAbortError(e)) return;
        setError((e as Error).message || String(e));
      })
      .finally(() => {
        if (!ac.signal.aborted) setLoading(false);
      });

    return () => ac.abort();
  }, [topic]);

  const onSave = async (rules: Rule[]) => {
    if (!topic) return;
    await saveRedactionRules('topic', topic, rules);
  };

  const onPickMessage = async () => {
    if (!topic) throw new Error('Missing topic');
    const payload = await getLastMessageUtf8(topic);
    if (!payload) throw new Error('No messages available in DLQ for this topic.');
    return payload;
  };

  const onValidatePath = (path: string, sample: any) =>
    validateJsonPath(path, sample);

  return (
    <div className="mx-auto max-w-7xl px-4 py-4 md:px-6 text-slate-100">
      {/* Header */}
      <div className="mb-4 flex items-center justify-between">
        <div>
          <h1 className="text-lg font-semibold tracking-tight">Redaction Studio</h1>
          <p className="mt-1 text-xs text-slate-400">
            Scope:{' '}
            <span className="inline-flex items-center gap-1 rounded-md border border-white/10 bg-white/5 px-2 py-0.5 align-middle">
              <span className="h-1.5 w-1.5 rounded-full bg-indigo-400" aria-hidden="true" />
              <code className="text-[0.8rem]">topic</code>
            </span>
            <span className="mx-2 text-slate-500">•</span>
            Key:{' '}
            <code className="rounded-md border border-white/10 bg-white/5 px-2 py-0.5 text-[0.8rem]">
              {topic ?? ''}
            </code>
          </p>
        </div>

        <Link
          to="/home"
          className="inline-flex items-center gap-1 rounded-md border border-white/10 bg-white/5 px-3 py-1.5 text-sm text-slate-200 hover:bg-white/10 focus:outline-none focus:ring-2 focus:ring-indigo-500"
        >
          ← Back
        </Link>
      </div>

      {/* States */}
      {loading && (
        <div
          role="status"
          aria-live="polite"
          className="rounded-xl border border-white/10 bg-[#0F162B] p-6 text-sm text-slate-300"
        >
          Loading rules…
        </div>
      )}

      {error && (
        <div
          role="alert"
          className="rounded-xl border border-rose-800/40 bg-rose-900/30 p-4 text-sm text-rose-200"
        >
          {error}
        </div>
      )}

      {/* Main panel */}
      {!loading && !error && topic && (
        <div className="rounded-xl border border-white/10 bg-[#0F162B] p-4">
          <RedactionStudio
            initialRules={initialRules}
            onSave={onSave}
            onPickMessage={onPickMessage}
            onValidatePath={onValidatePath}
          />
        </div>
      )}
    </div>
  );
}
