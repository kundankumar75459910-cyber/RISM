import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useClampPage, useDebounce, useQuery } from '../hooks';
import ConfirmDialog from '../components/ConfirmDialog';
import Modal from '../components/Modal';
import { DataState, EmptyState, Field, PageHeader, Pagination } from '../components/ui';

export default function Suppliers() {
  const { isAdmin } = useAuth();
  const toast = useToast();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [busy, setBusy] = useState(false);
  const debounced = useDebounce(search);
  const suppliers = useQuery(() => api.get('/suppliers', { search: debounced.trim(), page, size: 10 }), [debounced, page]);
  useClampPage(suppliers, page, setPage);

  async function confirmDelete() {
    setBusy(true);
    try {
      await api.del(`/suppliers/${deleting.id}`);
      toast.success(`${deleting.name} deleted`);
      setDeleting(null);
      suppliers.reload();
    } catch (err) {
      toast.error(err.message);
      setDeleting(null);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Suppliers" subtitle={isAdmin ? 'Manage the businesses you buy from' : 'View supplier details (editing is limited to administrators)'}>
        {isAdmin && <button className="btn btn-primary" onClick={() => setEditing({})}>+ Add supplier</button>}
      </PageHeader>
      <div className="toolbar">
        <input className="search" type="search" placeholder="Search by name or email…" value={search}
               onChange={(e) => { setSearch(e.target.value); setPage(0); }} aria-label="Search suppliers" />
      </div>
      <section className="card flush">
        <DataState query={suppliers} empty={<EmptyState title="No suppliers found" />}>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Supplier</th><th>Contact number</th><th>Email</th><th>Address</th>{isAdmin && <th className="actions">Actions</th>}</tr></thead>
              <tbody>
                {suppliers.data && suppliers.data.content.map((s) => (
                  <tr key={s.id}>
                    <td><strong>{s.name}</strong></td>
                    <td className="nowrap">{s.contactNumber || '-'}</td>
                    <td>{s.email ? <a className="link" href={`mailto:${s.email}`}>{s.email}</a> : '-'}</td>
                    <td>{s.address || '-'}</td>
                    {isAdmin && (
                      <td className="actions">
                        <button className="btn btn-sm" onClick={() => setEditing(s)}>Edit</button>
                        <button className="btn btn-sm btn-danger-outline" onClick={() => setDeleting(s)}>Delete</button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </DataState>
        <Pagination data={suppliers.data} onChange={setPage} />
      </section>
      {editing && <SupplierForm supplier={editing} onClose={() => setEditing(null)}
                                onSaved={(m) => { setEditing(null); toast.success(m); suppliers.reload(); }} />}
      {deleting && (
        <ConfirmDialog title="Delete supplier" danger busy={busy} confirmLabel="Delete supplier"
                       message={`Delete "${deleting.name}"? Suppliers that have purchase orders or linked products cannot be deleted.`}
                       onConfirm={confirmDelete} onCancel={() => setDeleting(null)} />
      )}
    </>
  );
}

function SupplierForm({ supplier, onClose, onSaved }) {
  const isNew = !supplier.id;
  const [form, setForm] = useState({
    name: supplier.name || '', contactNumber: supplier.contactNumber || '', email: supplier.email || '', address: supplier.address || '',
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [errors, setErrors] = useState({});
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    const errs = {};
    if (!form.name.trim()) errs.name = 'Supplier name is required';
    if (form.email.trim() && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.email.trim())) errs.email = 'Enter a valid email address';
    if (form.contactNumber.trim() && !/^[0-9+()\-\s]{5,30}$/.test(form.contactNumber.trim())) errs.contactNumber = 'Digits, spaces, + ( ) - only';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setSaving(true);
    setError('');
    try {
      if (isNew) await api.post('/suppliers', form);
      else await api.put(`/suppliers/${supplier.id}`, form);
      onSaved(isNew ? 'Supplier added' : 'Supplier updated');
    } catch (err) {
      setError(err.message);
      setErrors(err.fieldErrors || {});
      setSaving(false);
    }
  }

  return (
    <Modal title={isNew ? 'Add supplier' : 'Edit supplier'} onClose={onClose}
           footer={(<>
             <button className="btn" onClick={onClose} disabled={saving}>Cancel</button>
             <button className="btn btn-primary" form="supplier-form" disabled={saving}>{saving ? 'Saving…' : 'Save supplier'}</button>
           </>)}>
      <form id="supplier-form" onSubmit={submit} noValidate>
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        <Field label="Name *" error={errors.name}><input value={form.name} onChange={set('name')} autoFocus /></Field>
        <div className="form-grid">
          <Field label="Contact number" error={errors.contactNumber}><input value={form.contactNumber} onChange={set('contactNumber')} /></Field>
          <Field label="Email" error={errors.email}><input type="email" value={form.email} onChange={set('email')} /></Field>
        </div>
        <Field label="Address" error={errors.address}><textarea rows="2" value={form.address} onChange={set('address')} maxLength={255} /></Field>
      </form>
    </Modal>
  );
}
