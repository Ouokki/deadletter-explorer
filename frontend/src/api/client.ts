export async function getTopics(): Promise<string[]> {
  const r = await fetch('/api/dlq/topics');
  if (!r.ok) throw new Error('Failed to load topics');
  return r.json();
}

export type MessageDto = {
  topic?: string;
  partition: number;
  offset: number;
  timestamp: number;
  keyUtf8?: string | null;
  valueUtf8?: string | null;
  valueBase64?: string | null;
  headers: Record<string, string>;
};

export async function getMessages(topic: string, limit = 200): Promise<MessageDto[]> {
  const r = await fetch(`/api/dlq/messages?topic=${encodeURIComponent(topic)}&limit=${limit}`);
  if (!r.ok) throw new Error('Failed to load messages');
  return r.json();
}

type ReplayItemDto = {
  partition: number;
  offset: number;
  valueBase64?: string | null;
  headersBase64?: Record<string, string>;
  topic?: string; // ← allow topic when caller provides it
};

export async function replay(targetTopic: string, items: ReplayItemDto[], throttlePerSec?: number
) {const body = {
    sourceTopic: items[0]?.topic, // optional
    targetTopic,
    items,
    throttlePerSec
  };
  const r = await fetch('/api/dlq/replay', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  if (!r.ok) throw new Error('Replay failed');
  return r.json();
}
