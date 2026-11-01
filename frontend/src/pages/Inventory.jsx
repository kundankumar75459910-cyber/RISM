import { useState } from 'react';
import { api } from '../api';
import { useToast } from '../context/ToastContext';
import { openLocations, useClampPage, useDebounce, useLocations, useQuery } from '../hooks';
import LocationSelect from '../components/LocationSelect';
import Modal from '../components/Modal';
import { Badge, DataState, EmptyState, Field, PageHeader, Pagination, Spinner, Tabs } from '../components/ui';
import { dateTime, money, num, TX_LABELS } from '../utils';

export default function Inventory() {
  const [tab, setTab] = useState('stock');
  const [lowOnly, setLowOnly] = useState(false);
  const lowStock = useQuery(() => api.get('/inventory/low-stock'), []);
  const [refreshKey, setRefreshKey] = useState(0);
  const lowCount = lowStock.data ? lowStock.data.length : 0;

  return (
    <>
      <PageHeader title="Inventory" subtitle="Current stock levels by product and location, and the full history of stock movements" />

      {lowCount > 0 && (
        <div className="alert alert-warning" role="status">
          <span><strong>{lowCount} product{lowCount === 1 ? '' : 's'}</strong> at or below {lowCount === 1 ? 'its' : 'their'} reorder level.</span>
          <button className="btn btn-sm" onClick={() => { setTab('stock'); setLowOnly(true); }}>Show them</button>
        </div>
      )}

      <Tabs tabs={[{ key: 'stock', label: 'Stock levels' }, { key: 'history', label: 'Transaction history' }]}
            active={tab} onChange={setTab} />
      {tab === 'stock'
        ? <StockLevels lowOnly={lowOnly} setLowOnly={setLowOnly} refreshKey={refreshKey}
                       onChanged={() => { lowStock.reload(); setRefreshKey((k) => k + 1); }} />
        : <History refreshKey={refreshKey} />}
    </>
  );
}

function StockLevels({ lowOnly, setLowOnly, refreshKey, onChanged }) {
  const toast = useToast();
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [page, setPage] = useState(0);
  const [adjusting, setAdjusting] = useState(null);
  const [transferring, setTransferring] = useState(null);
  const [viewing, setViewing] = useState(null);
  const debounced = useDebounce(search);
  const categories = useQuery(() => api.get('/categories'), []);
  const stock = useQuery(
    () => api.get('/inventory', { search: debounced.trim(), categoryId, lowStock: lowOnly, page, size: 10 }),
    [debounced, categoryId, lowOnly, page, refreshKey]);
  useClampPage(stock, page, setPage);

  const status = (p) => (p.quantity === 0 ? <Badge tone="red">Out of stock</Badge>
    : p.lowStock ? <Badge tone="amber">Low stock</Badge> : <Badge tone="green">In stock</Badge>);

  return (
    <>
      <div className="toolbar">
        <input className="search" type="search" placeholder="Search by name, SKU or barcode…" value={search}
               onChange={(e) => { setSearch(e.target.value); setPage(0); }} aria-label="Search stock" />
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
        <DataState query={stock} empty={<EmptyState title="No products match" />}>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Product</th><th>Category</th><th className="num">On hand (all locations)</th><th className="num">Reorder at</th>
                <th>Status</th><th className="num">Stock value</th><th className="actions">Actions</th></tr></thead>
              <tbody>
                {stock.data && stock.data.content.map((p) => (
                  <tr key={p.id}>
                    <td><strong>{p.name}</strong><small className="sub">{p.sku}{p.barcode ? ` · ${p.barcode}` : ''}</small></td>
                    <td>{p.categoryName}</td>
                    <td className="num"><strong>{num(p.quantity)}</strong></td>
                    <td className="num">{num(p.reorderLevel)}</td>
                    <td>{status(p)}</td>
                    <td className="num">{money(p.stockValue)}</td>
                    <td className="actions">
                      <button className="btn btn-sm" onClick={() => setViewing(p)}>Locations</button>
                      <button className="btn btn-sm" onClick={() => setTransferring(p)} disabled={p.quantity === 0}>Transfer</button>
                      <button className="btn btn-sm" onClick={() => setAdjusting(p)}>Adjust</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </DataState>
        <Pagination data={stock.data} onChange={setPage} />
      </section>
      {viewing && <StockBreakdown product={viewing} onClose={() => setViewing(null)} />}
      {adjusting && (
        <AdjustModal product={adjusting} onClose={() => setAdjusting(null)}
                     onDone={() => { setAdjusting(null); toast.success('Stock adjusted'); onChanged(); }} />
      )}
      {transferring && (
        <TransferModal product={transferring} onClose={() => setTransferring(null)}
                       onDone={() => { setTransferring(null); toast.success('Stock transferred'); onChanged(); }} />
      )}
    </>
  );
}

/** Where one product's stock is held. */
function StockBreakdown({ product, onClose }) {
  const stock = useQuery(() => api.get(`/inventory/stock/${product.id}`), [product.id]);
  return (
    <Modal title={`Stock by location: ${product.name}`} onClose={onClose} footer={<button className="btn" onClick={onClose}>Close</button>}>
      {!stock.data ? (stock.error ? <div className="alert alert-error">{stock.error}</div> : <Spinner />) : (
        stock.data.length === 0 ? <p className="muted">This product has no stock at any location.</p> : (
          <table>
            <thead><tr><th>Location</th><th className="num">Units</th></tr></thead>
            <tbody>
              {stock.data.map((s) => (
                <tr key={s.locationId}>
                  <td>{s.locationName}{!s.locationActive && <Badge tone="gray">Closed</Badge>}</td>
                  <td className="num">{num(s.quantity)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot><tr><th>Total</th><th className="num">{num(stock.data.reduce((n, s) => n + s.quantity, 0))}</th></tr></tfoot>
          </table>
        )
      )}
    </Modal>
  );
}

/** Units of the product at a location, from the breakdown query (0 when none / not loaded). */
const heldAt = (stock, locationId) => (stock.data || []).find((s) => String(s.locationId) === String(locationId))?.quantity ?? 0;

function AdjustModal({ product, onClose, onDone }) {
  const locations = useLocations();
  const stock = useQuery(() => api.get(`/inventory/stock/${product.id}`), [product.id]);
  const open = openLocations(locations);
  const [locationChoice, setLocationChoice] = useState('');
  const [direction, setDirection] = useState('IN');
  const [quantity, setQuantity] = useState('');
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [errors, setErrors] = useState({});

  const locationId = locationChoice || (open[0] ? String(open[0].id) : '');
  const here = heldAt(stock, locationId);
  const locationName = open.find((l) => String(l.id) === locationId)?.name || 'this location';

  async function submit(e) {
    e.preventDefault();
    const errs = {};
    const q = Number(quantity);
    if (!locationId) errs.location = 'Choose a location';
    if (!Number.isInteger(q) || q < 1 || quantity === '') errs.quantity = 'Enter a whole number of at least 1';
    else if (direction === 'OUT' && q > here) errs.quantity = `Only ${here} in stock at ${locationName}`;
    if (!reason.trim()) errs.reason = 'Give a reason (e.g. damaged, stock count correction)';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setSaving(true);
    setError('');
    try {
      await api.post('/inventory/adjust', {
        productId: product.id, direction, quantity: q, reason: reason.trim(), locationId: Number(locationId),
      });
      onDone();
    } catch (err) {
      setError(err.message);
      setErrors(err.fieldErrors || {});
      setSaving(false);
    }
  }

  const after = Number.isInteger(Number(quantity)) && quantity !== ''
    ? here + (direction === 'IN' ? 1 : -1) * Number(quantity) : null;

  return (
    <Modal title={`Adjust stock: ${product.name}`} onClose={onClose}
           footer={(<>
             <button className="btn" onClick={onClose} disabled={saving}>Cancel</button>
             <button className="btn btn-primary" form="adjust-form" disabled={saving || !stock.data}>{saving ? 'Saving…' : 'Apply adjustment'}</button>
           </>)}>
      <form id="adjust-form" onSubmit={submit} noValidate>
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        <p className="muted">{num(product.quantity)} in stock in total ({product.sku}). Use this for stock-count corrections, damage or returns; purchases and sales update stock automatically.</p>
        <Field label="Location *" error={errors.location} hint={stock.data ? `${num(here)} held here now` : undefined}>
          <LocationSelect locations={open} value={locationId} onChange={setLocationChoice} />
        </Field>
        <div className="radio-row" role="radiogroup" aria-label="Direction">
          <label><input type="radio" checked={direction === 'IN'} onChange={() => setDirection('IN')} /> Add stock</label>
          <label><input type="radio" checked={direction === 'OUT'} onChange={() => setDirection('OUT')} /> Remove stock</label>
        </div>
        <Field label="Quantity *" error={errors.quantity} hint={after !== null && after >= 0 ? `New level at ${locationName}: ${num(after)}` : undefined}>
          <input inputMode="numeric" value={quantity} onChange={(e) => setQuantity(e.target.value)} autoFocus />
        </Field>
        <Field label="Reason *" error={errors.reason}>
          <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} />
        </Field>
      </form>
    </Modal>
  );
}

function TransferModal({ product, onClose, onDone }) {
  const locations = useLocations();
  const stock = useQuery(() => api.get(`/inventory/stock/${product.id}`), [product.id]);
  const open = openLocations(locations);
  const [fromChoice, setFromChoice] = useState('');
  const [toChoice, setToChoice] = useState('');
  const [quantity, setQuantity] = useState('');
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [errors, setErrors] = useState({});

  const sources = open.filter((l) => heldAt(stock, l.id) > 0);
  const fromId = fromChoice || (sources[0] ? String(sources[0].id) : '');
  const destinations = open.filter((l) => String(l.id) !== fromId);
  const toId = toChoice && toChoice !== fromId ? toChoice : (destinations[0] ? String(destinations[0].id) : '');
  const available = heldAt(stock, fromId);

  async function submit(e) {
    e.preventDefault();
    const errs = {};
    const q = Number(quantity);
    if (!fromId) errs.from = 'No open location holds this product';
    if (!toId) errs.to = 'Choose a destination';
    if (!Number.isInteger(q) || q < 1 || quantity === '') errs.quantity = 'Enter a whole number of at least 1';
    else if (q > available) errs.quantity = `Only ${available} available at the source`;
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setSaving(true);
    setError('');
    try {
      await api.post('/inventory/transfer', {
        productId: product.id, fromLocationId: Number(fromId), toLocationId: Number(toId), quantity: q, note: note.trim() || null,
      });
      onDone();
    } catch (err) {
      setError(err.message);
      setErrors(err.fieldErrors || {});
      setSaving(false);
    }
  }

  return (
    <Modal title={`Transfer stock: ${product.name}`} onClose={onClose}
           footer={(<>
             <button className="btn" onClick={onClose} disabled={saving}>Cancel</button>
             <button className="btn btn-primary" form="transfer-form" disabled={saving || !stock.data || open.length < 2}>{saving ? 'Moving…' : 'Transfer'}</button>
           </>)}>
      <form id="transfer-form" onSubmit={submit} noValidate>
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        {open.length < 2 && locations.data && <div className="alert alert-warning">Transfers need at least two open locations. Add one on the Locations page.</div>}
        <p className="muted">Moves units between locations. The product's total stock does not change.</p>
        <div className="form-grid">
          <Field label="From *" error={errors.from} hint={stock.data ? `${num(available)} available` : undefined}>
            <LocationSelect locations={sources} value={fromId} onChange={setFromChoice} />
          </Field>
          <Field label="To *" error={errors.to}>
            <LocationSelect locations={destinations} value={toId} onChange={setToChoice} />
          </Field>
        </div>
        <Field label="Quantity *" error={errors.quantity}>
          <input inputMode="numeric" value={quantity} onChange={(e) => setQuantity(e.target.value)} autoFocus />
        </Field>
        <Field label="Note"><input value={note} onChange={(e) => setNote(e.target.value)} maxLength={200} /></Field>
      </form>
    </Modal>
  );
}

function History({ refreshKey }) {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [locationId, setLocationId] = useState('');
  const [type, setType] = useState('');
  const [page, setPage] = useState(0);
  const categories = useQuery(() => api.get('/categories'), []);
  const locations = useLocations();
  const tx = useQuery(() => api.get('/inventory/transactions', { from, to, categoryId, locationId, type, page, size: 10 }),
    [from, to, categoryId, locationId, type, page, refreshKey]);
  useClampPage(tx, page, setPage);

  return (
    <>
      <div className="toolbar">
        <label className="inline-field">From <input type="date" value={from} max={to || undefined} onChange={(e) => { setFrom(e.target.value); setPage(0); }} /></label>
        <label className="inline-field">To <input type="date" value={to} min={from || undefined} onChange={(e) => { setTo(e.target.value); setPage(0); }} /></label>
        <select value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setPage(0); }} aria-label="Filter by category">
          <option value="">All categories</option>
          {(categories.data || []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <LocationSelect locations={locations.data || []} value={locationId} allLabel="All locations" aria-label="Filter by location"
                        onChange={(v) => { setLocationId(v); setPage(0); }} />
        <select value={type} onChange={(e) => { setType(e.target.value); setPage(0); }} aria-label="Filter by type">
          <option value="">All types</option>
          {Object.entries(TX_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
      </div>
      <section className="card flush">
        <DataState query={tx} empty={<EmptyState title="No transactions in this range" />}>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Date</th><th>Product</th><th>Location</th><th>Type</th><th className="num">Change</th><th className="num">Balance here</th><th>Reference / note</th><th>By</th></tr></thead>
              <tbody>
                {tx.data && tx.data.content.map((t) => (
                  <tr key={t.id}>
                    <td className="nowrap">{dateTime(t.createdAt)}</td>
                    <td><strong>{t.productName}</strong><small className="sub">{t.sku} · {t.categoryName}</small></td>
                    <td>{t.locationName}</td>
                    <td>{TX_LABELS[t.type] || t.type}</td>
                    <td className={`num ${t.quantityChange > 0 ? 'pos' : 'neg'}`}>{t.quantityChange > 0 ? '+' : ''}{t.quantityChange}</td>
                    <td className="num">{t.quantityAfter}</td>
                    <td>{t.note || '-'}</td>
                    <td>{t.createdBy || <span className="muted">system</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </DataState>
        <Pagination data={tx.data} onChange={setPage} />
      </section>
    </>
  );
}
