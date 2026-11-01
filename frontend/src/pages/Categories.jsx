import { useState } from 'react';
import { api } from '../api';
import { useToast } from '../context/ToastContext';
import { useQuery } from '../hooks';
import ConfirmDialog from '../components/ConfirmDialog';
import Modal from '../components/Modal';
import { DataState, EmptyState, Field, PageHeader } from '../components/ui';

export default function Categories() {
  const toast = useToast();
  const categories = useQuery(() => api.get('/categories'), []);
  const [editing, setEditing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [busy, setBusy] = useState(false);

  async function confirmDelete() {
    setBusy(true);
    try {
      await api.del(`/categories/${deleting.id}`);
      toast.success(`${deleting.name} deleted`);
      categories.reload();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
      setDeleting(null);
    }
  }

  return (
    <>
      <PageHeader title="Categories" subtitle="Group products for filtering and reporting">
        <button className="btn btn-primary" onClick={() => setEditing({})}>+ Add category</button>
      </PageHeader>
      <section className="card flush">
        <DataState query={categories} empty={<EmptyState title="No categories yet" hint="Add a category before creating products." />}>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>Description</th><th className="num">Products</th><th className="actions">Actions</th></tr></thead>
              <tbody>
                {categories.data && categories.data.map((c) => (
                  <tr key={c.id}>
                    <td><strong>{c.name}</strong></td>
                    <td>{c.description || '-'}</td>
                    <td className="num">{c.productCount}</td>
                    <td className="actions">
                      <button className="btn btn-sm" onClick={() => setEditing(c)}>Edit</button>
                      <button className="btn btn-sm btn-danger-outline" onClick={() => setDeleting(c)}>Delete</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </DataState>
      </section>
      {editing && <CategoryForm category={editing} onClose={() => setEditing(null)}
                                onSaved={(m) => { setEditing(null); toast.success(m); categories.reload(); }} />}
      {deleting && (
        <ConfirmDialog title="Delete category" danger busy={busy} confirmLabel="Delete category"
                       message={`Delete "${deleting.name}"? Categories that still contain products cannot be deleted.`}
                       onConfirm={confirmDelete} onCancel={() => setDeleting(null)} />
      )}
    </>
  );
}

function CategoryForm({ category, onClose, onSaved }) {
  const isNew = !category.id;
  const [name, setName] = useState(category.name || '');
  const [description, setDescription] = useState(category.description || '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [errors, setErrors] = useState({});

  async function submit(e) {
    e.preventDefault();
    if (!name.trim()) { setErrors({ name: 'Category name is required' }); return; }
    setSaving(true);
    setError('');
    try {
      const body = { name: name.trim(), description: description.trim() };
      if (isNew) await api.post('/categories', body);
      else await api.put(`/categories/${category.id}`, body);
      onSaved(isNew ? 'Category added' : 'Category updated');
    } catch (err) {
      setError(err.message);
      setErrors(err.fieldErrors || {});
      setSaving(false);
    }
  }

  return (
    <Modal title={isNew ? 'Add category' : 'Edit category'} onClose={onClose}
           footer={(<>
             <button className="btn" onClick={onClose} disabled={saving}>Cancel</button>
             <button className="btn btn-primary" form="category-form" disabled={saving}>{saving ? 'Saving…' : 'Save category'}</button>
           </>)}>
      <form id="category-form" onSubmit={submit} noValidate>
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        <Field label="Name *" error={errors.name}><input value={name} onChange={(e) => setName(e.target.value)} autoFocus /></Field>
        <Field label="Description" error={errors.description}><input value={description} onChange={(e) => setDescription(e.target.value)} maxLength={255} /></Field>
      </form>
    </Modal>
  );
}
