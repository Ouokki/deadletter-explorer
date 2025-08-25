import { useEffect, useState } from 'react';
import { getTopics } from '../services/api';

export function useTopics() {
  const [topics, setTopics] = useState<string[]>([]);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    let cancelled = false;
    getTopics()
      .then(t => { if (!cancelled) setTopics(t); })
      .catch(e => { if (!cancelled) setError(e); });
    return () => { cancelled = true; };
  }, []);

  return { topics, error };
}
