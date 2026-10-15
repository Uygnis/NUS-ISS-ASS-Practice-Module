import { useEffect, useState } from 'react';
import { useApi } from '../api/useApi';
import { Message, Empty, Skeleton } from '../components/ui';

// Tone and glyph per event type; unknown types fall back to neutral.
const KINDS = {
  BOOKING_CONFIRMED: { tone: 'n-ok', icon: '✓', title: 'Booking confirmed' },
  PAYMENT_RECEIPT: { tone: 'n-ok', icon: '$', title: 'Payment receipt' },
  PAYMENT_SUCCESS: { tone: 'n-ok', icon: '$', title: 'Payment successful' },
  PAYMENT_FAILED: { tone: 'n-bad', icon: '!', title: 'Payment failed' },
  BOOKING_CANCELLED: { tone: 'n-neu', icon: '×', title: 'Booking cancelled' },
};

function kindOf(type) {
  return KINDS[type] || {
    tone: 'n-neu',
    icon: 'i',
    title: type ? type.charAt(0) + type.slice(1).toLowerCase().replace(/_/g, ' ') : 'Notification',
  };
}

/** "5 min ago" with the exact time kept for the tooltip. */
function ago(iso) {
  if (!iso) return '';
  const secs = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (Number.isNaN(secs)) return iso;
  if (secs < 60) return 'just now';
  if (secs < 3600) return `${Math.floor(secs / 60)} min ago`;
  if (secs < 86400) return `${Math.floor(secs / 3600)} h ago`;
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

// Tells Layout to refresh the unread badge now rather than at its next poll.
function notifyBadge() {
  window.dispatchEvent(new Event('rentez:notifications-changed'));
}

export default function NotificationsPage() {
  const api = useApi();
  const [list, setList] = useState(null);
  const [err, setErr] = useState('');
  const [marking, setMarking] = useState(false);

  // load() asks for a refetch; the effect does it. Fetching inside the
  // effect (not via a local async function) lets a stale response be ignored.
  const [reloadKey, setReloadKey] = useState(0);
  const load = () => setReloadKey((k) => k + 1);

  useEffect(() => {
    let active = true;
    api.notifications.mine()
      .then((data) => {
        if (!active) return;
        setErr('');
        setList(data);
      })
      .catch((e) => {
        if (active) setErr(e.message);
      });
    return () => {
      active = false;
    };
  }, [api, reloadKey]);

  async function markRead(id) {
    try {
      await api.notifications.markRead(id);
      notifyBadge();
      load();
    } catch (e) {
      setErr(e.message);
    }
  }

  async function markAllRead() {
    setMarking(true);
    try {
      await Promise.all(list.filter((n) => !n.read).map((n) => api.notifications.markRead(n.id)));
    } catch (e) {
      setErr(e.message);
    } finally {
      setMarking(false);
      notifyBadge();
      load();
    }
  }

  const unread = (list || []).filter((n) => !n.read).length;

  return (
    <div className="panel">
      <h2>Notifications</h2>
      <div className="row" style={{ marginBottom: 14 }}>
        <button className="small" onClick={load}>Refresh</button>
        {unread > 0 && (
          <button className="small" onClick={markAllRead} disabled={marking}>
            {marking ? 'Marking…' : `Mark all read (${unread})`}
          </button>
        )}
      </div>
      <Message text={err} kind="err" />
      {list === null ? (
        <Skeleton rows={4} />
      ) : list.length === 0 ? (
        <Empty>No notifications yet. Booking and payment updates will appear here.</Empty>
      ) : (
        list.map((n) => {
          const k = kindOf(n.type);
          return (
            <div className={`notif ${k.tone}${n.read ? ' read' : ''}`} key={n.id}>
              <div className="n-icon" aria-hidden="true">{k.icon}</div>
              <div className="n-body">
                <strong>{k.title}</strong>
                <div>{n.message}</div>
                <div className="hint" title={n.sentAt}>
                  {n.relatedEntityType ? `${n.relatedEntityType} #${n.relatedEntityId} · ` : ''}{ago(n.sentAt)}
                </div>
              </div>
              <div>
                {n.read
                  ? <span className="status-pill st-neu">READ</span>
                  : <button className="small" onClick={() => markRead(n.id)}>Mark read</button>}
              </div>
            </div>
          );
        })
      )}
    </div>
  );
}
