import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useClampPage, useDebounce, useQuery } from '../hooks';
import ConfirmDialog from '../components/ConfirmDialog';
import Modal from '../components/Modal';
import { Badge, DataState, EmptyState, Field, PageHeader, Pagination } from '../components/ui';
import { money, num } from '../utils';

const EMPTY = { name: '', sku: '', barcode: '', imageUrl: '', categoryId: '', supplierId: '', price: '', quantity: '0', reorderLevel: '10' };

export default function Products() {
  const { isAdmin } = useAuth();
  const toast = useToast();
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [lowOnly, setLowOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState(null);     // null | {} (new) | product
  const [deleting, setDeleting] = useState(null);
  const [busy, setBusy] = useState(false);
  const debounced = useDebounce(search);

  const categories = useQuery(() => api.get('/categories'), []);
  const suppliers = useQuery(() => api.get('/suppliers', { size: 1000 }), []);
  const products = useQuery(
    () => api.get('/products', { search: debounced.trim(), categoryId, lowStock: lowOnly, page, size: 10 }),
    [debounced, categoryId, lowOnly, page]);
  useClampPage(products, page, setPage);

  async function confirmDelete() {
    setBusy(true);
    try {
      await api.del(`/products/${deleting.id}`);
      toast.success(`${deleting.name} deleted`);
      setDeleting(null);
      products.reload();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Products" subtitle="Manage the product catalogue">
        <button className="btn btn-primary" onClick={() => setEditing({})}>+ Add product</button>
      </PageHeader>

      <div className="toolbar">
        <input className="search" type="search" placeholder="Search by name, SKU or barcode…" value={search}
               onChange={(e) => { setSearch(e.target.value); setPage(0); }} aria-label="Search products" />
        <select value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setPage(0); }} aria-label="Filter by category">
          <option value="">All categories</option>
          {(categories.data || []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <label className="check">
          <input type="checkbox" checked={lowOnly} onChange={(e) => { setLowOnly(e.target.checked); setPage(0); }} />
          Low stock only
        </label>
      </div>

      <section className="card flush">
        <DataState query={products} empty={<EmptyState title="No products found" hint="Try a different search or filter, or add a new product." />}>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Product</th><th>Category</th><th>Supplier</th><th className="num">Price</th>
                  <th className="num">In stock</th><th className="num">Reorder at</th><th className="actions">Actions</th></tr>
              </thead>
              <tbody>
                {products.data && products.data.content.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <div className="product-cell">
                        {p.imageUrl
                          ? <img className="thumb" src={p.imageUrl} alt="" loading="lazy"
                                 onError={(e) => { e.currentTarget.style.visibility = 'hidden'; }} />
                          : <span className="thumb thumb-empty" aria-hidden="true" />}
                        <div><strong>{p.name}</strong><small className="sub">{p.sku}{p.barcode ? ` · ${p.barcode}` : ''}</small></div>
                      </div>
                    </td>
                    <td>{p.categoryName}</td>
                    <td>{p.supplierName || <span className="muted">-</span>}</td>
                    <td className="num">{money(p.price)}</td>
                    <td className="num">
                      {num(p.quantity)}{' '}
                      {p.quantity === 0 ? <Badge tone="red">Out</Badge> : p.lowStock ? <Badge tone="amber">Low</Badge> : null}
                    </td>
                    <td className="num">{num(p.reorderLevel)}</td>
                    <td className="actions">
                      <button className="btn btn-sm" onClick={() => setEditing(p)}>Edit</button>
                      {isAdmin && <button className="btn btn-sm btn-danger-outline" onClick={() => setDeleting(p)}>Delete</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </DataState>
        <Pagination data={products.data} onChange={setPage} />
      </section>

      {editing && (
        <ProductForm product={editing} categories={categories.data || []} suppliers={(suppliers.data && suppliers.data.content) || []}
                     onClose={() => setEditing(null)}
                     onSaved={(msg) => { setEditing(null); toast.success(msg); products.reload(); }} />
      )}
      {deleting && (
        <ConfirmDialog title="Delete product" danger busy={busy} confirmLabel="Delete product"
                       message={`Delete "${deleting.name}" (${deleting.sku})? It will disappear from the catalogue, but its purchase and sales history is kept.`}
                       onConfirm={confirmDelete} onCancel={() => setDeleting(null)} />
      )}
    </>
  );
}

function ProductForm({ product, categories, suppliers, onClose, onSaved }) {
  const isNew = !product.id;
  const [form, setForm] = useState(isNew ? EMPTY : {
    name: product.name, sku: product.sku, barcode: product.barcode || '', imageUrl: product.imageUrl || '',
    categoryId: String(product.categoryId),
    supplierId: product.supplierId ? String(product.supplierId) : '', price: String(product.price),
    quantity: '0', reorderLevel: String(product.reorderLevel),
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [errors, setErrors] = useState({});
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setError('');
    const errs = {};
    if (!form.name.trim()) errs.name = 'Name is required';
    if (!form.sku.trim()) errs.sku = 'SKU is required';
    if (form.imageUrl.trim() && !/^https?:\/\/\S+$/.test(form.imageUrl.trim())) errs.imageUrl = 'Enter a link starting with http:// or https://';
    if (!form.categoryId) errs.categoryId = 'Choose a category';
    const price = Number(form.price);
    if (form.price === '' || Number.isNaN(price) || price < 0) errs.price = 'Enter a valid price (0 or more)';
    else if (!/^\d+(\.\d{1,2})?$/.test(form.price.trim())) errs.price = 'Use at most 2 decimal places';
    if (!Number.isInteger(Number(form.reorderLevel)) || Number(form.reorderLevel) < 0 || form.reorderLevel === '') errs.reorderLevel = 'Enter a whole number (0 or more)';
    if (isNew && (!Number.isInteger(Number(form.quantity)) || Number(form.quantity) < 0 || form.quantity === '')) errs.quantity = 'Enter a whole number (0 or more)';
    setErrors(errs);
    if (Object.keys(errs).length) return;

    setSaving(true);
    const payload = {
      name: form.name.trim(), sku: form.sku.trim(), barcode: form.barcode.trim() || null,
      imageUrl: form.imageUrl.trim() || null, categoryId: Number(form.categoryId),
      supplierId: form.supplierId ? Number(form.supplierId) : null, price,
      quantity: isNew ? Number(form.quantity) : undefined, reorderLevel: Number(form.reorderLevel),
    };
    try {
      if (isNew) await api.post('/products', payload);
      else await api.put(`/products/${product.id}`, payload);
      onSaved(isNew ? 'Product added' : 'Product updated');
    } catch (err) {
      setError(err.message);
      setErrors(err.fieldErrors || {});
      setSaving(false);
    }
  }

  return (
    <Modal title={isNew ? 'Add product' : 'Edit product'} onClose={onClose}
           footer={(<>
             <button className="btn" onClick={onClose} disabled={saving}>Cancel</button>
             <button className="btn btn-primary" form="product-form" disabled={saving}>{saving ? 'Saving…' : 'Save product'}</button>
           </>)}>
      <form id="product-form" onSubmit={submit} noValidate>
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        <div className="form-grid">
          <Field label="Name *" error={errors.name}><input value={form.name} onChange={set('name')} autoFocus /></Field>
          <Field label="SKU *" error={errors.sku} hint="Must be unique"><input value={form.sku} onChange={set('sku')} /></Field>
          <Field label="Barcode" error={errors.barcode} hint="Scan it here. Must be unique when set"><input value={form.barcode} maxLength={64} onChange={set('barcode')} /></Field>
          <Field label="Category *" error={errors.categoryId}>
            <select value={form.categoryId} onChange={set('categoryId')}>
              <option value="">Select…</option>
              {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
          <Field label="Supplier" error={errors.supplierId}>
            <select value={form.supplierId} onChange={set('supplierId')}>
              <option value="">None</option>
              {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <Field label="Price *" error={errors.price}><input inputMode="decimal" value={form.price} onChange={set('price')} placeholder="0.00" /></Field>
          <Field label="Reorder level *" error={errors.reorderLevel} hint="Low-stock alert at or below this quantity">
            <input inputMode="numeric" value={form.reorderLevel} onChange={set('reorderLevel')} />
          </Field>
          {isNew ? (
            <Field label="Opening stock" error={errors.quantity} hint="Placed at the default location and recorded as an inventory transaction">
              <input inputMode="numeric" value={form.quantity} onChange={set('quantity')} />
            </Field>
          ) : (
            <Field label="Current stock" hint="Change stock via purchases, sales or an adjustment on the Inventory page">
              <input value={product.quantity} disabled />
            </Field>
          )}
        </div>
        <Field label="Image link" error={errors.imageUrl} hint="Optional. A web address of a picture; RIMS does not store the image itself">
          <input type="url" inputMode="url" value={form.imageUrl} maxLength={500} placeholder="https://…" onChange={set('imageUrl')} />
        </Field>
        {form.imageUrl.trim() && /^https?:\/\/\S+$/.test(form.imageUrl.trim()) && (
          <img className="thumb thumb-large" src={form.imageUrl.trim()} alt="Product preview"
               onError={(e) => { e.currentTarget.style.visibility = 'hidden'; }} />
        )}
      </form>
    </Modal>
  );
}
