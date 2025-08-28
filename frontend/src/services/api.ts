import { authFetch } from '../auth/httpAuth';
import { MessageDto, PreviewResponse, ReplayItemDto, Rule } from '../types/types';

type FetchOpts = { signal?: AbortSignal };


export async function getTopics(): Promise<string[]> {
  const r = await authFetch('/api/dlq/topics');
  if (!r.ok) throw new Error('Failed to load topics');
  return r.json();
}

export async function getMessages(
  topic: string,
  limit = 200,
  opts: FetchOpts = {}
): Promise<MessageDto[]> {
  const r = await authFetch(
    `/api/dlq/messages?topic=${encodeURIComponent(topic)}&limit=${limit}`,
    { signal: opts.signal }
  );
  if (!r.ok) throw new Error('Failed to load messages');
  return r.json();
}

export async function replay(
  targetTopic: string,
  items: ReplayItemDto[],
  throttlePerSec?: number
) {
  const body = { sourceTopic: items[0]?.topic, targetTopic, items, throttlePerSec };
  const r = await authFetch('/api/dlq/replay', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!r.ok) throw new Error('Replay failed');
  return r.json();
}

export async function getLastMessageUtf8(
  topic: string,
  opts: FetchOpts = {}
): Promise<string | undefined> {
  const msgs = await getMessages(topic, 1, opts);
  return msgs[0]?.valueUtf8 ?? undefined; 
}



export async function getRedactionRules(
  scope: 'global' | 'topic' | 'pattern',
  key?: string,
  opts: FetchOpts = {}
): Promise<Rule[]> {
  const qs = new URLSearchParams({ scope, ...(key ? { key } : {}) }).toString();
  const r = await authFetch(`/api/redaction/rules?${qs}`, { signal: opts.signal });
  if (!r.ok) throw new Error(`Failed to load rules (${r.status})`);
  return r.json();
}

export async function saveRedactionRules(
  scope: 'global' | 'topic' | 'pattern',
  key: string,
  rules: Rule[],
  opts: FetchOpts = {}
): Promise<void> {
  const r = await authFetch('/api/redaction/rules', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ scope, key, rules }),
    signal: opts.signal,
  });
  if (!r.ok) throw new Error(`Save failed (${r.status})`);
}

export async function previewRedaction(
  sampleJson: string,
  rules: Rule[],
  opts: FetchOpts = {}
): Promise<PreviewResponse> {
  const r = await authFetch('/api/redaction/preview', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sampleJson, rules }),
    signal: opts.signal,
  });
  if (!r.ok) throw new Error(`Preview failed (${r.status})`);
  return r.json();
}

export async function validateJsonPath(
  path: string,
  sample: any,
  opts: FetchOpts = {}
): Promise<{ ok: boolean; message?: string }> {
  const r = await authFetch('/api/redaction/validate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path, sample }),
    signal: opts.signal,
  });
  if (!r.ok) throw new Error(`Validate failed (${r.status})`);
  return r.json();
}
export type { Rule };

