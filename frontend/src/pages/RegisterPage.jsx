import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/useAuth';
import { useApi } from '../api/useApi';
import { FieldError, PasswordInput } from '../components/ui';
import { fieldClass } from '../components/format';

export default function RegisterPage() {
  const { login } = useAuth();
  const api = useApi();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '' });
  const [confirm, setConfirm] = useState('');
  const [err, setErr] = useState('');
  const [fieldErrs, setFieldErrs] = useState({});
  const [busy, setBusy] = useState(false);

  function set(field) {
    return (e) => setForm((f) => ({ ...f, [field]: e.target.value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setErr('');
    setFieldErrs({});
    if (form.password !== confirm) {
      setFieldErrs({ confirm: "The passwords don't match." });
      return;
    }
    setBusy(true);
    try {
      const auth = await api.accounts.register(form);
      login(auth);
      navigate('/browse');
    } catch (e2) {
      setFieldErrs(e2.fieldErrors || {});
      setErr(Object.keys(e2.fieldErrors || {}).length ? '' : e2.message);
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
          <div className={fieldClass(fieldErrs, 'fullName')}>
            <label>Full name</label>
            <input value={form.fullName} onChange={set('fullName')} autoComplete="name" autoFocus maxLength={150} required />
            <FieldError errors={fieldErrs} name="fullName" />
          </div>
          <div className={fieldClass(fieldErrs, 'email')}>
            <label>Email</label>
            <input type="email" value={form.email} onChange={set('email')} autoComplete="email" required />
            <FieldError errors={fieldErrs} name="email" />
          </div>
          <div className={fieldClass(fieldErrs, 'phone')}>
            <label>Phone (optional)</label>
            <input type="tel" value={form.phone} onChange={set('phone')} autoComplete="tel" inputMode="tel"
              placeholder="+65 9123 4567" maxLength={20} />
            <FieldError errors={fieldErrs} name="phone" />
          </div>
          <div className={fieldClass(fieldErrs, 'password')}>
            <label>Password (at least 8 characters)</label>
            <PasswordInput minLength={8} value={form.password} onChange={set('password')} autoComplete="new-password" required />
            <FieldError errors={fieldErrs} name="password" />
          </div>
          <div className={fieldClass(fieldErrs, 'confirm')}>
            <label>Confirm password</label>
            <PasswordInput minLength={8} value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" required />
            <FieldError errors={fieldErrs} name="confirm" />
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
