import { useRef, useState } from 'react';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { openLocations, useClampPage, useLocations, useQuery } from '../hooks';
import LocationSelect from '../components/LocationSelect';
import ConfirmDialog from '../components/ConfirmDialog';
import Modal from '../components/Modal';
import { Badge, DataState, EmptyState, Field, PageHeader, Pagination, Spinner } from '../components/ui';
import { cents, dateTime, fromCents, money, num } from '../utils';

const STATUS_TONE = { PENDING: 'amber', RECEIVED: 'green', CANCELLED: 'gray' };
const STATUS_LABEL = { PENDING: 'Pending', RECEIVED: 'Received', CANCELLED: 'Cancelled' };

export default function Purchases() {
  const { isAdmin } = useAuth();
  const [status, setStatus] = useState('');
  const [supplierId, setSupplierId] = useState('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [openId, setOpenId] = useState(null);

  const suppliers = useQuery(() => api.get('/suppliers', { size: 1000 }), []);
  const orders = useQuery(
    () => api.get('/purchases', { status, supplierId, page, size: 10 }),
    [status, supplierId, page]);
  useClampPage(orders, page, setPage);

  return (
    <>
      <PageHeader title="Purchase orders" subtitle="Order stock from suppliers. Stock only increases when an order is received.">
        <button className="btn btn-primary" onClick={() => setCreating(true)}>+ New purchase order</button>
      </PageHeader>

      <div className="toolbar">
        <select aria-label="Filter by status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
          <option value="">All statuses</option>
          <option value="PENDING">Pending</option>
          <option value="RECEIVED">Received</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
        <select aria-label="Filter by supplier" value={supplierId} onChange={(e) => { setSupplierId(e.target.value); setPage(0); }}>
          <option value="">All suppliers</option>
          {(suppliers.data?.content || []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      </div>

      <section className="card flush">
        <DataState query={orders} empty={<EmptyState title="No purchase orders" hint="Create a purchase order to order stock from a supplier." />}>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>PO number</th><th>Supplier</th><th>Deliver to</th><th>Ordered</th><th>Status</th><th className="num">Total</th><th className="actions">Actions</th></tr>
              </thead>
              <tbody>
                {orders.data?.content.map((po) => (
                  <tr key={po.id}>
                    <td><strong>{po.poNumber}</strong></td>
                    <td>{po.supplierName}</td>
                    <td>{po.locationName}</td>
                    <td>{dateTime(po.orderDate)}</td>
                    <td><Badge tone={STATUS_TONE[po.status]}>{STATUS_LABEL[po.status]}</Badge></td>
                    <td className="num">{money(po.totalAmount)}</td>
                    <td className="actions">
                      <button className="btn btn-sm" onClick={() => setOpenId(po.id)}>View</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </DataState>
        <Pagination data={orders.data} onChange={setPage} />
      </section>

      {creating && (
        <NewPurchaseForm
          suppliers={suppliers.data?.content || []}
          onClose={() => setCreating(false)}
          onSaved={() => { setCreating(false); orders.reload(); }}
        />
      )}
      {openId && (
        <PurchaseDetail id={openId} isAdmin={isAdmin} onClose={() => setOpenId(null)} onChanged={orders.reload} />
      )}
    </>
  );
}

function PurchaseDetail({ id, isAdmin, onClose, onChanged }) {
  const toast = useToast();
  const po = useQuery(() => api.get(`/purchases/${id}`), [id]);
  const [action, setAction] = useState(null);   // 'receive' | 'cancel' | null
  const [busy, setBusy] = useState(false);

  async function run() {
    setBusy(true);
    try {
      if (action === 'receive') {
        await api.post(`/purchases/${id}/receive`);
        toast.success('Order received. Stock has been updated');
      } else {
        await api.post(`/purchases/${id}/cancel`);
        toast.success('Order cancelled. Stock was not changed');
      }
      setAction(null);
      po.reload();
      onChanged();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  const footer = po.data && (
    <>
      {po.data.status === 'PENDING' && isAdmin && (
        <button className="btn btn-danger-outline" onClick={() => setAction('cancel')}>Cancel order</button>
      )}
      {po.data.status === 'PENDING' && (
        <button className="btn btn-primary" onClick={() => setAction('receive')}>Mark as received</button>
      )}
      <button className="btn" onClick={onClose}>Close</button>
    </>
  );

  return (
    <>
      <Modal title={po.data ? `Purchase order ${po.data.poNumber}` : 'Purchase order'} onClose={onClose} wide footer={footer}>
        {!po.data ? (po.error ? <div className="alert alert-error">{po.error}</div> : <Spinner />) : (
          <>
            <div className="detail-grid">
              <div><span className="muted">Supplier</span><strong>{po.data.supplierName}</strong></div>
              <div><span className="muted">Status</span><Badge tone={STATUS_TONE[po.data.status]}>{STATUS_LABEL[po.data.status]}</Badge></div>
              <div><span className="muted">Deliver to</span>{po.data.locationName}</div>
              <div><span className="muted">Ordered</span>{dateTime(po.data.orderDate)}</div>
              <div><span className="muted">Received</span>{dateTime(po.data.receivedDate)}</div>
              <div><span className="muted">Created by</span>{po.data.createdBy || '-'}</div>
              <div><span className="muted">Notes</span>{po.data.notes || '-'}</div>
            </div>
            <div className="table-wrap">
              <table>
                <thead><tr><th>Product</th><th className="num">Qty</th><th className="num">Unit cost</th><th className="num">Line total</th></tr></thead>
                <tbody>
                  {po.data.items.map((i) => (
                    <tr key={i.productId}>
                      <td><strong>{i.productName}</strong><small className="sub">{i.sku}</small></td>
                      <td className="num">{num(i.quantity)}</td>
                      <td className="num">{money(i.unitCost)}</td>
                      <td className="num">{money(i.lineTotal)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr><th colSpan={3} className="num">Total</th><th className="num">{money(po.data.totalAmount)}</th></tr>
                </tfoot>
              </table>
            </div>
          </>
        )}
      </Modal>

      {action && (
        <ConfirmDialog
          title={action === 'receive' ? 'Mark as received?' : 'Cancel this order?'}
          danger={action === 'cancel'}
          busy={busy}
          confirmLabel={action === 'receive' ? 'Receive stock' : 'Cancel order'}
          message={action === 'receive'
            ? 'Stock at the delivery location will increase by the ordered quantities. This can only be done once.'
            : 'The order will be cancelled. Stock will not change. A cancelled order cannot be received.'}
          onConfirm={run}
          onCancel={() => setAction(null)}
        />
      )}
    </>
  );
}

function NewPurchaseForm({ suppliers, onClose, onSaved }) {
  const toast = useToast();
  const products = useQuery(() => api.get('/products', { size: 1000 }), []);
  const [supplierId, setSupplierId] = useState('');
  const locations = useLocations();
  const open = openLocations(locations);
  const [locationChoice, setLocationChoice] = useState('');
  const locationId = locationChoice || (open[0] ? String(open[0].id) : '');
  const [notes, setNotes] = useState('');
  const [lines, setLines] = useState([{ key: 1, productId: '', quantity: '1', unitCost: '' }]);
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);
  const nextKey = useRef(2);

  const setLine = (key, field, value) =>
    setLines((ls) => ls.map((l) => (l.key === key ? { ...l, [field]: value } : l)));
  const addLine = () => setLines((ls) => [...ls, { key: nextKey.current++, productId: '', quantity: '1', unitCost: '' }]);
  const removeLine = (key) => setLines((ls) => (ls.length > 1 ? ls.filter((l) => l.key !== key) : ls));

  // money is summed in integer cents on the client; the server recalculates it authoritatively
  const total = lines.reduce((sum, l) => {
    const q = Number(l.quantity), c = Number(l.unitCost);
    return Number.isInteger(q) && q > 0 && !Number.isNaN(c) && c >= 0 ? sum + cents(c) * q : sum;
  }, 0);

  function validate() {
    const errs = {};
    if (!supplierId) errs.supplierId = 'Choose a supplier';
    const seen = new Set();
    const lineErrs = lines.map((l) => {
      const e = {};
      if (!l.productId) e.productId = 'Choose a product';
      else if (seen.has(l.productId)) e.productId = 'Product already on another line';
      else seen.add(l.productId);
      if (!Number.isInteger(Number(l.quantity)) || Number(l.quantity) < 1 || l.quantity === '') e.quantity = 'Whole number, 1 or more';
      if (l.unitCost === '' || Number.isNaN(Number(l.unitCost)) || Number(l.unitCost) < 0) e.unitCost = 'Enter a cost (0 or more)';
      else if (!/^\d+(\.\d{1,2})?$/.test(l.unitCost.trim())) e.unitCost = 'At most 2 decimal places';
      return e;
    });
    if (lineErrs.some((e) => Object.keys(e).length)) errs.lines = lineErrs;
    setErrors(errs);
    return Object.keys(errs).length === 0;
  }

  async function submit(e) {
    e.preventDefault();
    setFormError('');
    if (!validate()) return;
    setSaving(true);
    try {
      const po = await api.post('/purchases', {
        supplierId: Number(supplierId),
        locationId: Number(locationId),
        notes: notes.trim() || null,
        items: lines.map((l) => ({
          productId: Number(l.productId), quantity: Number(l.quantity), unitCost: Number(l.unitCost),
        })),
      });
      toast.success(`${po.poNumber} created. Stock updates when it is received`);
      onSaved();
    } catch (err) {
      setFormError(err.message);
      setErrors(err.fieldErrors || {});
      setSaving(false);
    }
  }

  const productList = products.data?.content || [];

  return (
    <Modal title="New purchase order" wide onClose={onClose}
           footer={(<>
             <button className="btn" onClick={onClose} disabled={saving}>Cancel</button>
             <button className="btn btn-primary" form="po-form" disabled={saving}>{saving ? 'Saving…' : 'Create order'}</button>
           </>)}>
      <form id="po-form" onSubmit={submit} noValidate>
        {formError && <div className="alert alert-error" role="alert">{formError}</div>}
        <div className="form-grid">
          <Field label="Supplier *" error={errors.supplierId}>
            <select value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
              <option value="">Select…</option>
              {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <Field label="Deliver to *" hint="Stock is added here when the order is received">
            <LocationSelect locations={open} value={locationId} onChange={setLocationChoice} />
          </Field>
          <Field label="Notes"><input value={notes} maxLength={255} onChange={(e) => setNotes(e.target.value)} /></Field>
        </div>

        <h3 className="section-title">Items</h3>
        {products.error && <div className="alert alert-error">{products.error}</div>}
        <div className="line-items">
          {lines.map((l, idx) => {
            const le = errors.lines?.[idx] || {};
            return (
              <div className="line-row" key={l.key}>
                <Field label={idx === 0 ? 'Product *' : undefined} error={le.productId}>
                  <select value={l.productId} onChange={(e) => setLine(l.key, 'productId', e.target.value)}>
                    <option value="">Select product…</option>
                    {productList.map((p) => <option key={p.id} value={p.id}>{p.name} ({p.sku})</option>)}
                  </select>
                </Field>
                <Field label={idx === 0 ? 'Qty *' : undefined} error={le.quantity}>
                  <input inputMode="numeric" value={l.quantity} onChange={(e) => setLine(l.key, 'quantity', e.target.value)} />
                </Field>
                <Field label={idx === 0 ? 'Unit cost *' : undefined} error={le.unitCost}>
                  <input inputMode="decimal" placeholder="0.00" value={l.unitCost} onChange={(e) => setLine(l.key, 'unitCost', e.target.value)} />
                </Field>
                <button type="button" className="btn btn-sm btn-danger-outline line-remove"
                        onClick={() => removeLine(l.key)} disabled={lines.length === 1} aria-label="Remove line">×</button>
              </div>
            );
          })}
        </div>
        <button type="button" className="btn btn-sm" onClick={addLine}>+ Add item</button>

        <div className="total-row">
          <span className="muted">Order total</span>
          <strong>{money(fromCents(total))}</strong>
        </div>
      </form>
    </Modal>
  );
}
