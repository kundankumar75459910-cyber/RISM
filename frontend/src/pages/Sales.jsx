import { useRef, useState } from 'react';
import { api } from '../api';
import { useToast } from '../context/ToastContext';
import { openLocations, useClampPage, useLocations, useQuery } from '../hooks';
import LocationSelect from '../components/LocationSelect';
import Modal from '../components/Modal';
import { DataState, EmptyState, Field, PageHeader, Pagination, Spinner } from '../components/ui';
import { cents, dateTime, fromCents, money, num } from '../utils';

export default function Sales() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [openId, setOpenId] = useState(null);

  const sales = useQuery(() => api.get('/sales', { from, to, page, size: 10 }), [from, to, page]);
  useClampPage(sales, page, setPage);

  return (
    <>
      <PageHeader title="Sales" subtitle="Record sales. Stock is reduced as soon as a sale is saved.">
        <button className="btn btn-primary" onClick={() => setCreating(true)}>+ New sale</button>
      </PageHeader>

      <div className="toolbar">
        <label className="inline-field">From
          <input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} aria-label="From date" />
        </label>
        <label className="inline-field">To
          <input type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} aria-label="To date" />
        </label>
        {(from || to) && <button className="btn btn-sm" onClick={() => { setFrom(''); setTo(''); setPage(0); }}>Clear dates</button>}
      </div>

      <section className="card flush">
        <DataState query={sales} empty={<EmptyState title="No sales yet" hint="Record a sale to see it here." />}>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Sale number</th><th>Date</th><th>Customer</th><th>Location</th><th className="num">Items</th><th className="num">Total</th><th className="actions">Actions</th></tr>
              </thead>
              <tbody>
                {sales.data?.content.map((s) => (
                  <tr key={s.id}>
                    <td><strong>{s.saleNumber}</strong></td>
                    <td>{dateTime(s.saleDate)}</td>
                    <td>{s.customerName || <span className="muted">Walk-in</span>}</td>
                    <td>{s.locationName}</td>
                    <td className="num">{num(s.items.reduce((n, i) => n + i.quantity, 0))}</td>
                    <td className="num">{money(s.totalAmount)}</td>
                    <td className="actions"><button className="btn btn-sm" onClick={() => setOpenId(s.id)}>View</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </DataState>
        <Pagination data={sales.data} onChange={setPage} />
      </section>

      {creating && <NewSaleForm onClose={() => setCreating(false)} onSaved={() => { setCreating(false); sales.reload(); }} />}
      {openId && <SaleDetail id={openId} onClose={() => setOpenId(null)} />}
    </>
  );
}

function SaleDetail({ id, onClose }) {
  const sale = useQuery(() => api.get(`/sales/${id}`), [id]);
  return (
    <Modal title={sale.data ? `Sale ${sale.data.saleNumber}` : 'Sale'} onClose={onClose} wide
           footer={<button className="btn" onClick={onClose}>Close</button>}>
      {!sale.data ? (sale.error ? <div className="alert alert-error">{sale.error}</div> : <Spinner />) : (
        <>
          <div className="detail-grid">
            <div><span className="muted">Date</span>{dateTime(sale.data.saleDate)}</div>
            <div><span className="muted">Customer</span>{sale.data.customerName || 'Walk-in'}</div>
            <div><span className="muted">Location</span>{sale.data.locationName}</div>
            <div><span className="muted">Recorded by</span>{sale.data.createdBy || '-'}</div>
          </div>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Product</th><th className="num">Qty</th><th className="num">Unit price</th><th className="num">Line total</th></tr></thead>
              <tbody>
                {sale.data.items.map((i) => (
                  <tr key={i.productId}>
                    <td><strong>{i.productName}</strong><small className="sub">{i.sku}</small></td>
                    <td className="num">{num(i.quantity)}</td>
                    <td className="num">{money(i.unitPrice)}</td>
                    <td className="num">{money(i.lineTotal)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot><tr><th colSpan={3} className="num">Total</th><th className="num">{money(sale.data.totalAmount)}</th></tr></tfoot>
            </table>
          </div>
        </>
      )}
    </Modal>
  );
}

function NewSaleForm({ onClose, onSaved }) {
  const toast = useToast();
  const products = useQuery(() => api.get('/products', { size: 1000 }), []);
  const locations = useLocations();
  const open = openLocations(locations);
  const [locationChoice, setLocationChoice] = useState('');
  const locationId = locationChoice || (open[0] ? String(open[0].id) : '');
  // units of each product at the chosen location (a sale can only take what that location holds)
  const atLocation = useQuery(
    () => (locationId ? api.get(`/inventory/locations/${locationId}/stock`) : Promise.resolve([])), [locationId]);
  const held = new Map((atLocation.data || []).map((r) => [String(r.productId), r.quantity]));
  const [scan, setScan] = useState('');
  const [scanMsg, setScanMsg] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [lines, setLines] = useState([{ key: 1, productId: '', quantity: '1' }]);
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);
  const nextKey = useRef(2);

  const productList = products.data?.content || [];
  const byId = new Map(productList.map((p) => [String(p.id), p]));

  /** Barcode scanners type the code and press Enter. Matches a barcode, or a SKU as a fallback. */
  async function onScan(e) {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    const code = scan.trim();
    if (!code) return;
    setScanMsg('');
    try {
      const p = await api.get(`/products/lookup/${encodeURIComponent(code)}`);
      const id = String(p.id);
      setLines((ls) => {
        const existing = ls.find((l) => l.productId === id);
        if (existing) return ls.map((l) => (l.key === existing.key ? { ...l, quantity: String(Number(l.quantity || 0) + 1) } : l));
        const blank = ls.find((l) => !l.productId);
        if (blank) return ls.map((l) => (l.key === blank.key ? { ...l, productId: id, quantity: '1' } : l));
        return [...ls, { key: nextKey.current++, productId: id, quantity: '1' }];
      });
      setScanMsg(`Added ${p.name}`);
    } catch (err) {
      setScanMsg(err.message);
    }
    setScan('');
  }

  const setLine = (key, field, value) =>
    setLines((ls) => ls.map((l) => (l.key === key ? { ...l, [field]: value } : l)));
  const addLine = () => setLines((ls) => [...ls, { key: nextKey.current++, productId: '', quantity: '1' }]);
  const removeLine = (key) => setLines((ls) => (ls.length > 1 ? ls.filter((l) => l.key !== key) : ls));

  // Client total in integer cents, using the current product price. The server recalculates it.
  const total = lines.reduce((sum, l) => {
    const p = byId.get(l.productId);
    const q = Number(l.quantity);
    return p && Number.isInteger(q) && q > 0 ? sum + cents(p.price) * q : sum;
  }, 0);

  function validate() {
    const errs = {};
    const seen = new Set();
    const lineErrs = lines.map((l) => {
      const e = {};
      const p = byId.get(l.productId);
      if (!l.productId) e.productId = 'Choose a product';
      else if (seen.has(l.productId)) e.productId = 'Product already on another line';
      else seen.add(l.productId);
      const q = Number(l.quantity);
      if (!Number.isInteger(q) || q < 1 || l.quantity === '') e.quantity = 'Whole number, 1 or more';
      else if (p && q > (held.get(l.productId) ?? 0)) e.quantity = `Only ${num(held.get(l.productId) ?? 0)} at this location`;
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
      const sale = await api.post('/sales', {
        customerName: customerName.trim() || null,
        locationId: Number(locationId),
        items: lines.map((l) => ({ productId: Number(l.productId), quantity: Number(l.quantity) })),
      });
      toast.success(`${sale.saleNumber} recorded (${money(sale.totalAmount)})`);
      onSaved();
    } catch (err) {
      // a failed sale changes nothing on the server (the whole transaction rolls back)
      setFormError(err.message);
      setErrors(err.fieldErrors || {});
      setSaving(false);
    }
  }

  return (
    <Modal title="New sale" wide onClose={onClose}
           footer={(<>
             <button className="btn" onClick={onClose} disabled={saving}>Cancel</button>
             <button className="btn btn-primary" form="sale-form" disabled={saving}>{saving ? 'Saving…' : 'Record sale'}</button>
           </>)}>
      <form id="sale-form" onSubmit={submit} noValidate>
        {formError && <div className="alert alert-error" role="alert">{formError}</div>}
        {products.error && <div className="alert alert-error">{products.error}</div>}
        <div className="form-grid">
          <Field label="Sell from *" hint="Stock is taken from this location">
            <LocationSelect locations={open} value={locationId} onChange={setLocationChoice} />
          </Field>
          <Field label="Customer name" hint="Optional">
            <input value={customerName} maxLength={100} onChange={(e) => setCustomerName(e.target.value)} />
          </Field>
        </div>
        <Field label="Scan barcode" hint={scanMsg || 'Click here and scan (or type a barcode / SKU and press Enter)'}>
          <input className="scan-input" value={scan} onChange={(e) => setScan(e.target.value)} onKeyDown={onScan}
                 placeholder="Scan or type a barcode…" autoComplete="off" autoFocus />
        </Field>

        <h3 className="section-title">Items</h3>
        <div className="line-items">
          {lines.map((l, idx) => {
            const le = errors.lines?.[idx] || {};
            return (
              <div className="line-row sale-row" key={l.key}>
                <Field label={idx === 0 ? 'Product *' : undefined} error={le.productId}>
                  <select value={l.productId} onChange={(e) => setLine(l.key, 'productId', e.target.value)}>
                    <option value="">Select product…</option>
                    {productList.map((p) => (
                      <option key={p.id} value={p.id} disabled={(held.get(String(p.id)) ?? 0) === 0}>
                        {p.name} · {money(p.price)} · {num(held.get(String(p.id)) ?? 0)} here{(held.get(String(p.id)) ?? 0) === 0 ? ' (none)' : ''}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label={idx === 0 ? 'Qty *' : undefined} error={le.quantity}>
                  <input inputMode="numeric" value={l.quantity} onChange={(e) => setLine(l.key, 'quantity', e.target.value)} />
                </Field>
                <button type="button" className="btn btn-sm btn-danger-outline line-remove"
                        onClick={() => removeLine(l.key)} disabled={lines.length === 1} aria-label="Remove line">×</button>
              </div>
            );
          })}
        </div>
        <button type="button" className="btn btn-sm" onClick={addLine}>+ Add item</button>

        <div className="total-row">
          <span className="muted">Sale total</span>
          <strong>{money(fromCents(total))}</strong>
        </div>
      </form>
    </Modal>
  );
}
