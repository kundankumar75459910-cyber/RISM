import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { Field } from '../components/ui';

export default function Login() {
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    if (!username.trim() || !password) {
      setError('Enter your username and password');
      return;
    }
    setBusy(true);
    setError('');
    try {
      await login(username.trim(), password);
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={submit} noValidate>
        <div className="brand brand-dark">
          <span className="brand-mark">R</span>
          <div>
            <strong>RIMS</strong>
            <small>Retail Inventory Management System</small>
          </div>
        </div>
        <h1>Sign in</h1>
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        <Field label="Username">
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" autoFocus />
        </Field>
        <Field label="Password">
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
        </Field>
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <p className="login-hint muted">
          Demo accounts: <code>admin</code> / <code>admin123</code> &nbsp;·&nbsp; <code>staff</code> / <code>staff123</code>
        </p>
      </form>
    </div>
  );
}
