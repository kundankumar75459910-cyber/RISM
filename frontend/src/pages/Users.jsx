import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useQuery } from '../hooks';
import Modal from '../components/Modal';
import { Badge, DataState, Field, PageHeader } from '../components/ui';
import { dateTime } from '../utils';

export default function Users() {
  const { user: me } = useAuth();
  const toast = useToast();
  const users = useQuery(() => api.get('/users'), []);
  const [editing, setEditing] = useState(null);

  return (
    <>
      <PageHeader title="Users" subtitle="Administrators have full access; staff handle day-to-day operations">
        <button className="btn btn-primary" onClick={() => setEditing({})}>+ Add user</button>
      </PageHeader>
      <section className="card flush">
        <DataState query={users}>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>Username</th><th>Role</th><th>Status</th><th>Created</th><th className="actions">Actions</th></tr></thead>
              <tbody>
                {users.data && users.data.map((u) => (
                  <tr key={u.id}>
                    <td><strong>{u.fullName}</strong>{u.username === me.username && <small className="sub">(you)</small>}</td>
                    <td>{u.username}</td>
                    <td><Badge tone={u.role === 'ADMIN' ? 'blue' : 'gray'}>{u.role === 'ADMIN' ? 'Admin' : 'Staff'}</Badge></td>
                    <td><Badge tone={u.active ? 'green' : 'red'}>{u.active ? 'Active' : 'Disabled'}</Badge></td>
                    <td className="nowrap">{dateTime(u.createdAt)}</td>
                    <td className="actions"><button className="btn btn-sm" onClick={() => setEditing(u)}>Edit</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </DataState>
      </section>
      {editing && <UserForm target={editing} onClose={() => setEditing(null)}
                            onSaved={(m) => { setEditing(null); toast.success(m); users.reload(); }} />}
    </>
  );
}

function UserForm({ target, onClose, onSaved }) {
  const isNew = !target.id;
  const [form, setForm] = useState({
    username: target.username || '', fullName: target.fullName || '', role: target.role || 'STAFF',
    active: target.active !== false, password: '',
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [errors, setErrors] = useState({});
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    const errs = {};
    if (!/^[A-Za-z0-9._-]{3,50}$/.test(form.username.trim())) errs.username = '3-50 characters: letters, digits, . _ -';
    if (!form.fullName.trim()) errs.fullName = 'Full name is required';
    if (isNew && form.password.length < 6) errs.password = 'Password must be at least 6 characters';
    if (!isNew && form.password && form.password.length < 6) errs.password = 'Password must be at least 6 characters';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setSaving(true);
    setError('');
    const body = { username: form.username.trim(), fullName: form.fullName.trim(), role: form.role, active: form.active,
      password: form.password || undefined };
    try {
      if (isNew) await api.post('/users', body);
      else await api.put(`/users/${target.id}`, body);
      onSaved(isNew ? 'User created' : 'User updated');
    } catch (err) {
      setError(err.message);
      setErrors(err.fieldErrors || {});
      setSaving(false);
    }
  }

  return (
    <Modal title={isNew ? 'Add user' : `Edit ${target.username}`} onClose={onClose}
           footer={(<>
             <button className="btn" onClick={onClose} disabled={saving}>Cancel</button>
             <button className="btn btn-primary" form="user-form" disabled={saving}>{saving ? 'Saving…' : 'Save user'}</button>
           </>)}>
      <form id="user-form" onSubmit={submit} noValidate>
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        <div className="form-grid">
          <Field label="Username *" error={errors.username}>
            <input value={form.username} onChange={set('username')} disabled={!isNew} autoFocus={isNew} />
          </Field>
          <Field label="Full name *" error={errors.fullName}><input value={form.fullName} onChange={set('fullName')} /></Field>
          <Field label="Role *" error={errors.role}>
            <select value={form.role} onChange={set('role')}>
              <option value="STAFF">Staff</option>
              <option value="ADMIN">Admin</option>
            </select>
          </Field>
          <Field label={isNew ? 'Password *' : 'New password'} error={errors.password} hint={isNew ? 'At least 6 characters' : 'Leave blank to keep the current password'}>
            <input type="password" value={form.password} onChange={set('password')} autoComplete="new-password" />
          </Field>
        </div>
        <label className="check">
          <input type="checkbox" checked={form.active} onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))} />
          Account is active (disabled users cannot log in)
        </label>
      </form>
    </Modal>
  );
}
