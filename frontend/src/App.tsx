import React, { useEffect, useMemo, useState } from 'react'
import { getTopics, getMessages, replay, type MessageDto } from './api/client'

export default function App() {
  const [topics, setTopics] = useState<string[]>([])
  const [selectedTopic, setSelectedTopic] = useState<string | null>(null)
  const [messages, setMessages] = useState<MessageDto[]>([])
  const [loading, setLoading] = useState(false)
  const [targetTopic, setTargetTopic] = useState('')
  const [status, setStatus] = useState<string>('')

  useEffect(() => { getTopics().then(setTopics).catch(e => setStatus(String(e))) }, [])

  useEffect(() => {
    if (!selectedTopic) return
    setLoading(true)
    getMessages(selectedTopic, 200)
      .then(setMessages)
      .catch(e => setStatus(String(e)))
      .finally(() => setLoading(false))
  }, [selectedTopic])

  const replayOne = async (m: MessageDto) => {
    if (!targetTopic) {
      alert('Specify a target topic to replay to')
      return
    }
    setStatus('Replaying 1 message...')
    try {
      const res = await replay(targetTopic, [{
        partition: m.partition,
        offset: m.offset,
        valueBase64: m.valueBase64,
        headersBase64: m.headers
      }])
      setStatus(`OK → replayed to ${targetTopic}`)
    } catch (e) {
      setStatus('Replay failed: ' + e)
    }
  }

  return (
    <div style={{fontFamily:'Inter, system-ui, sans-serif', padding: 16}}>
      <h1>DeadLetter Explorer</h1>
      <p style={{opacity:.7}}>Inspect and replay messages from Kafka DLQs (local MVP)</p>

      <div style={{display:'flex', gap:16, alignItems:'center'}}>
        <label>Target topic:</label>
        <input value={targetTopic} onChange={e=>setTargetTopic(e.target.value)} placeholder="e.g. orders" />
        <span style={{opacity:.6}}>{status}</span>
      </div>

      <div style={{display:'grid', gridTemplateColumns:'240px 1fr', gap:16, marginTop:16}}>
        <aside style={{border:'1px solid #ddd', borderRadius:8, padding:8}}>
          <b>DLQ Topics</b>
          <ul style={{listStyle:'none', paddingLeft:0}}>
            {topics.map(t => (
              <li key={t}>
                <button onClick={()=>setSelectedTopic(t)}
                        style={{background:selectedTopic===t?'#eef':'transparent', border:'none', cursor:'pointer', padding:'6px 8px', width:'100%', textAlign:'left'}}>
                  {t}
                </button>
              </li>
            ))}
            {!topics.length && <li style={{opacity:.6}}>No DLQ topics found yet</li>}
          </ul>
        </aside>

        <main style={{border:'1px solid #ddd', borderRadius:8, padding:8}}>
          <div style={{display:'flex', justifyContent:'space-between', alignItems:'center'}}>
            <b>Messages {selectedTopic ? `in ${selectedTopic}` : ''}</b>
            {loading && <span>Loading…</span>}
          </div>
          <table style={{width:'100%', borderCollapse:'collapse', marginTop:8}}>
            <thead>
              <tr>
                <th style={{textAlign:'left', borderBottom:'1px solid #eee'}}>Partition</th>
                <th style={{textAlign:'left', borderBottom:'1px solid #eee'}}>Offset</th>
                <th style={{textAlign:'left', borderBottom:'1px solid #eee'}}>Timestamp</th>
                <th style={{textAlign:'left', borderBottom:'1px solid #eee'}}>Key</th>
                <th style={{textAlign:'left', borderBottom:'1px solid #eee'}}>Value (UTF-8 or base64)</th>
                <th style={{textAlign:'left', borderBottom:'1px solid #eee'}}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {messages.map(m => (
                <tr key={m.partition + '-' + m.offset}>
                  <td>{m.partition}</td>
                  <td>{m.offset}</td>
                  <td>{new Date(m.timestamp).toLocaleString()}</td>
                  <td><code style={{opacity:.8}}>{m.keyUtf8 ?? ''}</code></td>
                  <td>
                    <pre style={{maxWidth:600, overflow:'auto', margin:0}}>
                      {m.valueUtf8 ?? `(base64) ${m.valueBase64?.slice(0, 80)}…`}
                    </pre>
                  </td>
                  <td>
                    <button onClick={()=>replayOne(m)}>Replay →</button>
                  </td>
                </tr>
              ))}
              {!messages.length && <tr><td colSpan={6} style={{opacity:.6}}>No messages yet</td></tr>}
            </tbody>
          </table>
        </main>
      </div>
    </div>
  )
}
