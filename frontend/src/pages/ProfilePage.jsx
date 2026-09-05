import { useEffect, useState } from 'react';
import { useApi } from '../api/useApi';
import { useAuth } from '../context/AuthContext';
import { Message } from '../components/ui';

export default function ProfilePage() {
  const api = useApi();
  const { updateName } = useAuth();
  const [me, setMe] = useState(null);
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');

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
    setMsg(''); setErr('');
    try {
      const updated = await api.accounts.updateMe({ fullName, phone });
      updateName(updated.fullName);
      setMsg('Profile updated.');
    } catch (e2) {
      setErr(e2.message);
    }
  }

  return (
    <div className="panel">
      <h2>My profile</h2>
      {err && <Message text={err} kind="err" />}
      {!me ? (
        <div className="hint">Loading…</div>
      ) : (
        <form onSubmit={save}>
          <div className="row">
            <div className="field">
              <label>Full name</label>
              <input value={fullName} onChange={(e) => setFullName(e.target.value)} required />
            </div>
            <div className="field">
              <label>Phone</label>
              <input value={phone} onChange={(e) => setPhone(e.target.value)} />
            </div>
          </div>
          <div className="row">
            <div className="field"><label>Email</label><input value={me.email || ''} disabled /></div>
            <div className="field"><label>Role</label><input value={me.role || ''} disabled /></div>
            <div className="field"><label>Account status</label><input value={me.enabled ? 'Enabled' : 'Disabled'} disabled /></div>
          </div>
          <Message text={msg} kind="ok" />
          <button type="submit" className="primary">Save changes</button>
        </form>
      )}
    </div>
  );
}
