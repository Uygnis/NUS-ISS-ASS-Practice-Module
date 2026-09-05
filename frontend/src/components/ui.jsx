export const BOOKING_STATUS_CLASS = { PENDING_PAYMENT: 'st-warn', CONFIRMED: 'st-ok', MODIFIED: 'st-warn', CANCELLED: 'st-bad', COMPLETED: 'st-neu' };
export const PAYMENT_STATUS_CLASS = { INITIATED: 'st-warn', SUCCESS: 'st-ok', FAILED: 'st-bad', REFUNDED: 'st-neu' };
export const CONFIRM_STATE_CLASS = { CONFIRMED: 'st-ok', PENDING: 'st-warn', COMPENSATED: 'st-neu', AWAITING_COMPENSATION: 'st-warn', NOT_APPLICABLE: 'st-neu' };
export const CAR_STATUS_CLASS = { AVAILABLE: 'st-ok', RENTED: 'st-warn', MAINTENANCE: 'st-bad', RETIRED: 'st-neu' };
export const MAINT_STATUS_CLASS = { SCHEDULED: 'st-warn', IN_PROGRESS: 'st-warn', COMPLETED: 'st-ok' };
export const USER_STATUS_CLASS = { ENABLED: 'st-ok', DISABLED: 'st-bad' };

export function StatusPill({ status, map }) {
  const cls = (map && map[status]) || 'st-neu';
  return <span className={`status-pill ${cls}`}>{status}</span>;
}

export function Message({ text, kind = 'ok' }) {
  if (!text) return null;
  return <div className={`msg ${kind}`}>{text}</div>;
}

export function Empty({ children }) {
  return <div className="empty">{children}</div>;
}

export function fmtMoney(n) {
  return n == null ? '—' : `$${Number(n).toFixed(2)}`;
}

export function fmtDate(d) {
  return d || '—';
}
