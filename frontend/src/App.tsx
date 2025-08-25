import React, { useState } from 'react';
import { useTopics } from './hooks/useTopics';
import { useMessages } from './hooks/useMessages';
import { replay } from './services/api';
import { MessageDto } from './types/types';
import TargetTopicBar from './components/TargetTopicBar';
import TopicList from './components/TopicList';
import MessagesTable from './components/MessagesTable';
import { Navbar } from './components/Navbar';

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
    <>
    <Navbar />
    <div className="min-h-screen bg-gray-50 text-gray-900">
      {/* Header */}
      <header className="border-b bg-white shadow-sm px-6 py-4">
        <h1 className="text-2xl font-semibold">DeadLetter Explorer</h1>
        <p className="text-gray-500 text-sm">
          Inspect and replay messages from Kafka DLQs (local MVP)
        </p>
      </header>

      {/* Target Topic Bar */}
      <div className="px-6 py-4">
        <TargetTopicBar
          value={targetTopic}
          onChange={setTargetTopic}
          status={status}
        />
      </div>

      {/* Layout */}
      <div className="grid grid-cols-12 gap-6 px-6 pb-6">
        {/* Sidebar */}
        <aside className="col-span-3 bg-white border rounded-lg shadow-sm p-4">
          <h2 className="font-medium mb-2">DLQ Topics</h2>
          <TopicList
            topics={topics}
            selected={selectedTopic}
            onSelect={setSelectedTopic}
          />
        </aside>

        {/* Main Content */}
        <main className="col-span-9 bg-white border rounded-lg shadow-sm p-4 flex flex-col">
          <div className="flex items-center justify-between border-b pb-2 mb-4">
            <h2 className="font-medium">
              Messages {selectedTopic ? `in ${selectedTopic}` : ""}
            </h2>
            {loading && <span className="text-sm text-gray-500">Loading…</span>}
          </div>

          <div className="flex-1 overflow-auto">
            <MessagesTable
              messages={messages}
              onReplay={replayOne}
              emptyText="No messages yet"
            />
          </div>
        </main>
      </div>
    </div>

    </>
    
  );
}
