import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useApi } from '../api/useApi';

export default function RegisterPage() {
  const { login } = useAuth();
  const api = useApi();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '' });
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  function set(field) {
    return (e) => setForm((f) => ({ ...f, [field]: e.target.value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setErr('');
    setBusy(true);
    try {
      const auth = await api.accounts.register(form);
      login(auth);
      navigate('/browse');
    } catch (e2) {
      setErr(e2.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div id="authScreen">
      <div className="plate">
        <h1>RentEz</h1>
        <div className="tagline">Create an account to start booking.</div>
        <form onSubmit={handleSubmit}>
          <div className="field">
            <label>Full name</label>
            <input value={form.fullName} onChange={set('fullName')} required />
          </div>
          <div className="field">
            <label>Email</label>
            <input type="email" value={form.email} onChange={set('email')} required />
          </div>
          <div className="field">
            <label>Phone</label>
            <input value={form.phone} onChange={set('phone')} />
          </div>
          <div className="field">
            <label>Password (min 8 chars)</label>
            <input type="password" minLength={8} value={form.password} onChange={set('password')} required />
          </div>
          <div className="authErr">{err}</div>
          <button type="submit" className="primary" style={{ width: '100%' }} disabled={busy}>
            {busy ? 'Creating account…' : 'Create account'}
          </button>
        </form>
        <div className="authSwitch">
          Already have an account? <Link to="/login">Sign in</Link>
        </div>
      </div>
    </div>
  );
}
