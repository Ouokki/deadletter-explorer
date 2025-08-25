import React, { useState } from 'react';
import { useTopics } from './hooks/useTopics';
import { useMessages } from './hooks/useMessages';
import { replay } from './services/api';
import { MessageDto } from './types/types';
import TargetTopicBar from './components/TargetTopicBar';
import TopicList from './components/TopicList';
import MessagesTable from './components/MessagesTable';

export default function App() {
  const [selectedTopic, setSelectedTopic] = useState<string | null>(null);
  const [targetTopic, setTargetTopic] = useState('');
  const [status, setStatus] = useState<string>('');

  const { topics, error: topicsError } = useTopics();
  const { messages, loading } = useMessages(selectedTopic, 200);

  if (topicsError && !status) setStatus(String(topicsError));

  const replayOne = async (m: MessageDto) => {
    if (!targetTopic) {
      alert('Specify a target topic to replay to');
      return;
    }
    setStatus('Replaying 1 message...');
    try {
      await replay(targetTopic, [{
        partition: m.partition,
        offset: m.offset,
        valueBase64: m.valueBase64 ?? undefined,
        headersBase64: m.headers,
        topic: m.topic ?? selectedTopic ?? undefined
      }]);
      setStatus(`OK → replayed to ${targetTopic}`);
    } catch (e) {
      setStatus('Replay failed: ' + e);
    }
  };

  return (
    <div className="page">
      <header className="header">
        <h1>DeadLetter Explorer</h1>
        <p className="muted">Inspect and replay messages from Kafka DLQs (local MVP)</p>
      </header>

      <TargetTopicBar
        value={targetTopic}
        onChange={setTargetTopic}
        status={status}
      />

      <div className="layout">
        <aside className="panel">
          <b>DLQ Topics</b>
          <TopicList
            topics={topics}
            selected={selectedTopic}
            onSelect={setSelectedTopic}
          />
        </aside>

        <main className="panel">
          <div className="panelHeader">
            <b>Messages {selectedTopic ? `in ${selectedTopic}` : ''}</b>
            {loading && <span>Loading…</span>}
          </div>

          <MessagesTable
            messages={messages}
            onReplay={replayOne}
            emptyText="No messages yet"
          />
        </main>
      </div>
    </div>
  );
}
