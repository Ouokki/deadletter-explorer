import { useEffect, useState } from 'react';
import { getMessages } from '../services/api';
import { MessageDto } from '../types/types';

export function useMessages(topic: string | null, limit = 200) {
  const [messages, setMessages] = useState<MessageDto[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!topic) { setMessages([]); return; }

    const controller = new AbortController();
    setLoading(true);

    getMessages(topic, limit, { signal: controller.signal })
      .then(setMessages)
      .catch(err => {
        // Ignore aborts; you can surface others with a global notifier if you prefer
        if ((err as any)?.name !== 'AbortError') console.error(err);
      })
      .finally(() => setLoading(false));

    return () => controller.abort();
  }, [topic, limit]);

  return { messages, loading };
}
