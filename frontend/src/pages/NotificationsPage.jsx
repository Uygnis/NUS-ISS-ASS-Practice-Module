import { useEffect, useState } from 'react';
import { useApi } from '../api/useApi';
import { Message, Empty } from '../components/ui';

export default function NotificationsPage() {
  const api = useApi();
  const [list, setList] = useState(null);
  const [err, setErr] = useState('');

  async function load() {
    setErr('');
    try {
      const notifications = await api.notifications.mine();
      setList(notifications);
    } catch (e) {
      setErr(e.message);
    }
  }

  useEffect(() => { load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, []);

  async function markRead(id) {
    try {
      await api.notifications.markRead(id);
      load();
    } catch (e) {
      setErr(e.message);
    }
  }

  return (
    <div className="panel">
      <h2>Notifications</h2>
      <div className="row" style={{ marginBottom: 14 }}>
        <button className="small" onClick={load}>Refresh</button>
      </div>
      <Message text={err} kind="err" />
      {list === null ? (
        <div className="hint">Loading…</div>
      ) : list.length === 0 ? (
        <Empty>No notifications yet.</Empty>
      ) : (
        list.map((n) => (
          <div className="panel" key={n.id} style={{ marginBottom: 10, opacity: n.read ? 0.6 : 1 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10 }}>
              <div>
                <strong>{n.type || 'Notification'}</strong>
                <div>{n.message}</div>
                <div className="hint">
                  {n.relatedEntityType ? `${n.relatedEntityType} #${n.relatedEntityId} · ` : ''}{n.sentAt}
                </div>
              </div>
              <div>
                {n.read
                  ? <span className="status-pill st-neu">READ</span>
                  : <button className="small" onClick={() => markRead(n.id)}>Mark read</button>}
              </div>
            </div>
          </div>
        ))
      )}
    </div>
  );
}
