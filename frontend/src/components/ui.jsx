import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

export function StatusPill({ status, map }) {
  const cls = (map && map[status]) || 'st-neu';
  return <span className={`status-pill ${cls}`}>{status}</span>;
}

/**
 * Success messages close themselves after 4s; errors stay until closed.
 * `sticky` keeps a success message too (e.g. one carrying an action).
 * Keyed by text, so a new message reappears even after the last was closed.
 */
export function Message({ text, kind = 'ok', sticky = false, children }) {
  if (!text) return null;
  return <MessageBox key={text} text={text} kind={kind} sticky={sticky}>{children}</MessageBox>;
}

function MessageBox({ text, kind, sticky, children }) {
  const [open, setOpen] = useState(true);
  useEffect(() => {
    if (kind !== 'ok' || sticky) return undefined;
    const t = setTimeout(() => setOpen(false), 4000);
    return () => clearTimeout(t);
  }, [kind, sticky]);
  if (!open) return null;
  return (
    <div className={`msg ${kind}`} role={kind === 'err' ? 'alert' : 'status'}>
      <span className="msg-text">{text}</span>
      {children}
      <button type="button" className="msg-close" aria-label="Dismiss" onClick={() => setOpen(false)}>×</button>
    </div>
  );
}

/** Empty state with an optional next step: action = { label, to }. */
export function Empty({ children, action }) {
  return (
    <div className="empty">
      <svg className="empty-art" viewBox="0 0 64 40" aria-hidden="true">
        <path d="M6 30 L10 21 L22 19 L29 11 L45 11 L52 19 L58 21 L58 30 Z" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
        <circle cx="18" cy="31" r="5" fill="var(--panel)" stroke="currentColor" strokeWidth="2" />
        <circle cx="47" cy="31" r="5" fill="var(--panel)" stroke="currentColor" strokeWidth="2" />
      </svg>
      <div>{children}</div>
      {action && <Link className="button accent small empty-cta" to={action.to}>{action.label}</Link>}
    </div>
  );
}

/** Placeholder bars while a list loads, instead of "Loading…". */
export function Skeleton({ rows = 3, card = false }) {
  if (card) {
    return (
      <div className="cardgrid" aria-busy="true" aria-label="Loading">
        {Array.from({ length: rows }, (_, i) => <div key={i} className="skel skel-card" />)}
      </div>
    );
  }
  return (
    <div aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => <div key={i} className="skel skel-row" />)}
    </div>
  );
}

/** Inline message under one input, from the backend's fieldErrors map. */
export function FieldError({ errors, name }) {
  const text = errors && errors[name];
  if (!text) return null;
  return <div className="field-err" role="alert">{text}</div>;
}

/** Password field with a show/hide toggle. Extra props go to the <input>. */
export function PasswordInput(props) {
  const [shown, setShown] = useState(false);
  return (
    <div className="pw-wrap">
      <input {...props} type={shown ? 'text' : 'password'} />
      <button type="button" className="pw-toggle" onClick={() => setShown((v) => !v)}
        aria-label={shown ? 'Hide password' : 'Show password'} aria-pressed={shown}>
        {shown ? 'Hide' : 'Show'}
      </button>
    </div>
  );
}
