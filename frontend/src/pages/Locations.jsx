import { useState } from 'react';
import { api } from '../api';
import { useToast } from '../context/ToastContext';
import { useLocations } from '../hooks';
import Modal from '../components/Modal';
import { Badge, DataState, EmptyState, Field, PageHeader } from '../components/ui';
import { num } from '../utils';

export default function Locations() {
  const toast = useToast();
  const locations = useLocations();
  const [editing, setEditing] = useState(null);   // null | {} (new) | location

  return (
    <>
      <PageHeader title="Locations" subtitle="Warehouses and stores that hold stock. The first open location is the default for new stock.">
        <button className="btn btn-primary" onClick={() => setEditing({})}>+ Add location</button>
      </PageHeader>
      <section className="card flush">
        <DataState query={locations} empty={<EmptyState title="No locations yet" hint="Add a warehouse or store to start holding stock." />}>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>Address</th><th className="num">Units held</th><th>Status</th><th className="actions">Actions</th></tr></thead>
              <tbody>
                {(locations.data || []).map((l) => (
                  <tr key={l.id}>
                    <td><strong>{l.name}</strong></td>
                    <td>{l.address || <span className="muted">-</span>}</td>
                    <td className="num">{num(l.totalUnits)}</td>
                    <td><Badge tone={l.active ? 'green' : 'gray'}>{l.active ? 'Open' : 'Closed'}</Badge></td>
                    <td className="actions"><button className="btn btn-sm" onClick={() => setEditing(l)}>Edit</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </DataState>
      </section>
      {editing && (
        <LocationForm location={editing} onClose={() => setEditing(null)}
                      onSaved={(msg) => { setEditing(null); toast.success(msg); locations.reload(); }} />
      )}
    </>
  );
}

function LocationForm({ location, onClose, onSaved }) {
  const isNew = !location.id;
  const [form, setForm] = useState({ name: location.name || '', address: location.address || '', active: location.active ?? true });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [errors, setErrors] = useState({});

  async function submit(e) {
    e.preventDefault();
    setError('');
    if (!form.name.trim()) { setErrors({ name: 'Name is required' }); return; }
    setErrors({});
    setSaving(true);
    const payload = { name: form.name.trim(), address: form.address.trim() || null, active: form.active };
    try {
      if (isNew) await api.post('/locations', payload);
      else await api.put(`/locations/${location.id}`, payload);
      onSaved(isNew ? 'Location added' : 'Location updated');
    } catch (err) {
      setError(err.message);
      setErrors(err.fieldErrors || {});
      setSaving(false);
    }
  }

  return (
    <Modal title={isNew ? 'Add location' : 'Edit location'} onClose={onClose}
           footer={(<>
             <button className="btn" onClick={onClose} disabled={saving}>Cancel</button>
             <button className="btn btn-primary" form="location-form" disabled={saving}>{saving ? 'Saving…' : 'Save location'}</button>
           </>)}>
      <form id="location-form" onSubmit={submit} noValidate>
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        <Field label="Name *" error={errors.name}>
          <input value={form.name} maxLength={100} autoFocus onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </Field>
        <Field label="Address" error={errors.address}>
          <input value={form.address} maxLength={255} onChange={(e) => setForm({ ...form, address: e.target.value })} />
        </Field>
        <label className="check">
          <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
          Open (can receive, sell and transfer stock)
        </label>
        {!isNew && <p className="muted field-hint">A location can only be closed when it holds no stock. Transfer its stock elsewhere first.</p>}
      </form>
    </Modal>
  );
}
