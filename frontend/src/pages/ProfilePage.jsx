import { useEffect, useState } from 'react';
import { useApi } from '../api/useApi';
import { useAuth } from '../context/AuthContext';
import { Message, FieldError, fieldClass } from '../components/ui';

export default function ProfilePage() {
  const api = useApi();
  const { updateName } = useAuth();
  const [me, setMe] = useState(null);
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const [fieldErrs, setFieldErrs] = useState({});
  const [saving, setSaving] = useState(false);

  async function load() {
    setErr('');
    try {
      const data = await api.accounts.me();
      setMe(data);
      setFullName(data.fullName || '');
      setPhone(data.phone || '');
    } catch (e) {
      setErr(e.message);
    }
  }

  useEffect(() => { load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, []);

  async function save(e) {
    e.preventDefault();
    setMsg(''); setErr(''); setFieldErrs({});
    setSaving(true);
    try {
      const updated = await api.accounts.updateMe({ fullName, phone });
      updateName(updated.fullName);
      setMe(updated);
      setMsg('Profile updated.');
    } catch (e2) {
      setFieldErrs(e2.fieldErrors || {});
      setErr(Object.keys(e2.fieldErrors || {}).length ? '' : e2.message);
    } finally {
      setSaving(false);
    }
  }

  const dirty = !!me && (fullName !== (me.fullName || '') || phone !== (me.phone || ''));

  return (
    <div className="panel">
      <h2>My profile</h2>
      {err && <Message text={err} kind="err" />}
      {!me ? (
        <div className="hint">Loading…</div>
      ) : (
        <form onSubmit={save}>
          <div className="row">
            <div className={fieldClass(fieldErrs, 'fullName')}>
              <label>Full name</label>
              <input value={fullName} onChange={(e) => setFullName(e.target.value)} autoComplete="name" maxLength={150} required />
              <FieldError errors={fieldErrs} name="fullName" />
            </div>
            <div className={fieldClass(fieldErrs, 'phone')}>
              <label>Phone</label>
              <input type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel" inputMode="tel" placeholder="+65 9123 4567" maxLength={20} />
              <FieldError errors={fieldErrs} name="phone" />
            </div>
          </div>
          <div className="row">
            <div className="field"><label>Email</label><input value={me.email || ''} disabled /></div>
            <div className="field"><label>Role</label><input value={me.role || ''} disabled /></div>
            <div className="field"><label>Account status</label><input value={me.enabled ? 'Enabled' : 'Disabled'} disabled /></div>
          </div>
          <Message text={msg} kind="ok" />
          <button type="submit" className="primary" disabled={saving || !dirty}>{saving ? 'Saving…' : 'Save changes'}</button>
        </form>
      )}
    </div>
  );
}
