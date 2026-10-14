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

/** Today as YYYY-MM-DD in the user's local time zone, for <input type="date" min>. */
export function todayISO() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Same rule as the backend: an empty or reversed range, or a start in the past, is rejected. */
export function dateRangeError(startDate, endDate, { allowPastStart = false } = {}) {
  if (!startDate || !endDate) return 'Please choose both a start and an end date.';
  if (!allowPastStart && startDate < todayISO()) return "The start date can't be in the past. Please pick today or a later date.";
  if (endDate < startDate) return 'The end date must be on or after the start date.';
  return '';
}

/** Inclusive day count, matching reservation-service's pricing. */
export function rentalDays(startDate, endDate) {
  if (!startDate || !endDate || endDate < startDate) return 0;
  return Math.round((new Date(endDate) - new Date(startDate)) / 86400000) + 1;
}

/** Inline message under one input, from the backend's fieldErrors map. */
export function FieldError({ errors, name }) {
  const text = errors && errors[name];
  if (!text) return null;
  return <div className="field-err" role="alert">{text}</div>;
}

export function fieldClass(errors, name) {
  return errors && errors[name] ? 'field invalid' : 'field';
}
