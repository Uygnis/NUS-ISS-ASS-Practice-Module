// Plain values and functions shared by the pages. Kept out of ui.jsx so that
// file exports only components, which React Fast Refresh needs.

export const BOOKING_STATUS_CLASS = { PENDING_PAYMENT: 'st-warn', CONFIRMED: 'st-ok', MODIFIED: 'st-warn', CANCELLED: 'st-bad', COMPLETED: 'st-neu' };

export const PAYMENT_STATUS_CLASS = { INITIATED: 'st-warn', SUCCESS: 'st-ok', FAILED: 'st-bad', REFUNDED: 'st-neu' };

export const CONFIRM_STATE_CLASS = { CONFIRMED: 'st-ok', PENDING: 'st-warn', COMPENSATED: 'st-neu', AWAITING_COMPENSATION: 'st-warn', NOT_APPLICABLE: 'st-neu' };

export const CAR_STATUS_CLASS = { AVAILABLE: 'st-ok', RENTED: 'st-warn', MAINTENANCE: 'st-bad', RETIRED: 'st-neu' };

export const MAINT_STATUS_CLASS = { SCHEDULED: 'st-warn', IN_PROGRESS: 'st-warn', COMPLETED: 'st-ok' };

export const USER_STATUS_CLASS = { ENABLED: 'st-ok', DISABLED: 'st-bad' };

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

export function fieldClass(errors, name) {
  return errors && errors[name] ? 'field invalid' : 'field';
}
