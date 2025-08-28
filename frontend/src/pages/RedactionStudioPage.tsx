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

export default function RedactionStudioPage() {
  const { topic = '' } = useParams<{ topic: string }>();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [initialRules, setInitialRules] = React.useState<Rule[]>([]);

  React.useEffect(() => {
    const ac = new AbortController();
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const rules = await getRedactionRules('topic', topic, { signal: ac.signal });
        setInitialRules(rules ?? []);
      } catch (e: any) {
        setError(e.message || String(e));
      } finally {
        setLoading(false);
      }
    })();
    return () => ac.abort();
  }, [topic]);

  const onSave = async (rules: Rule[]) => {
    await saveRedactionRules('topic', topic, rules);
  };

  const onPickMessage = async () => {
    const payload = await getLastMessageUtf8(topic);
    if (!payload) throw new Error('No messages available in DLQ for this topic.');
    return payload;
  };

  const onValidatePath = async (path: string, sample: any) =>
    validateJsonPath(path, sample);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">Redaction Studio</h1>
          <p className="text-sm text-gray-600">
            Scope: <code className="rounded bg-gray-100 px-1">topic</code> — Key:{' '}
            <code className="rounded bg-gray-100 px-1">{topic}</code>
          </p>
        </div>
        <Link to="/home" className="text-sm text-gray-600 hover:text-gray-900">
          ← Back to Home
        </Link>
      </div>

      {loading && (
        <div className="rounded-xl border border-gray-200 bg-white p-6 text-sm text-gray-600">
          Loading rules…
        </div>
      )}
      {error && (
        <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          {error}
        </div>
      )}

      {!loading && !error && (
        <RedactionStudio
          initialRules={initialRules}
          onSave={onSave}
          onPickMessage={onPickMessage}
          onValidatePath={onValidatePath}
        />
      )}
    </div>
  );
}
