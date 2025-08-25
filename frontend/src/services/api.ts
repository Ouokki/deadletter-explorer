import { authFetch } from '../auth/httpAuth';
import { MessageDto, ReplayItemDto } from '../types/types';

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
