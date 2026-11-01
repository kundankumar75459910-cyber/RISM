import { useState } from 'react';
import { mlApi } from '../api';
import { useQuery } from '../hooks';
import { Badge, EmptyState, ErrorBox, PageHeader, Spinner, StatCard } from '../components/ui';
import { downloadCsv, num } from '../utils';

const STATUS = {
  STOCKOUT: { label: 'Out of stock', tone: 'red' },
  REORDER_NOW: { label: 'Reorder now', tone: 'amber' },
  ON_ORDER: { label: 'On order', tone: 'blue' },
  OVERSTOCK: { label: 'Overstock', tone: 'gray' },
  OK: { label: 'OK', tone: 'green' },
  SLOW_MOVER: { label: 'No recent sales', tone: 'gray' },
};
const CONFIDENCE_HINT = {
  LOW: 'Little sales history. Treat as a rough guide.',
  MEDIUM: 'Reasonable history.',
  HIGH: 'Plenty of history.',
};

const days = (v) => (v === null || v === undefined ? '-' : v >= 1000 ? '999+' : num(Math.round(v)));
const rate = (v) => (v === null || v === undefined ? '-' : (Math.round(v * 10) / 10).toLocaleString());

export default function Forecast() {
  const [status, setStatus] = useState('');
  const [serviceLevel, setServiceLevel] = useState('0.95');

  // The full list is fetched once per service level; the status filter is applied locally so the
  // summary cards always describe every product.
  const query = useQuery(() => mlApi.get('/recommendations', { serviceLevel }), [serviceLevel]);
  const data = query.data;
  const items = data ? (status ? data.items.filter((i) => i.status === status) : data.items) : [];

  function exportCsv() {
    downloadCsv('reorder-recommendations.csv',
      ['SKU', 'Product', 'Category', 'In stock', 'On order', 'Avg daily demand', 'Days of cover', 'Lead time (days)',
        'Current reorder level', 'Recommended reorder level', 'Suggested order qty', 'Status', 'Confidence', 'Model'],
      items.map((i) => [i.sku, i.name, i.category, i.stock, i.pendingQty, i.avgDailyDemand, i.daysOfCover ?? '',
        i.leadTimeDays, i.currentReorderLevel, i.recommendedReorderLevel, i.suggestedOrderQty,
        STATUS[i.status].label, i.confidence, i.model]));
  }

  return (
    <>
      <PageHeader title="Demand forecast"
                  subtitle="Predicts daily demand from your sales history and recommends when and how much to reorder.">
        <button className="btn" onClick={exportCsv} disabled={!items.length}>Export CSV</button>
      </PageHeader>

      {query.error && !data && (
        <ErrorBox message={query.error} onRetry={query.reload} />
      )}
      {!data && !query.error && <Spinner label="Analysing sales history…" />}

      {data && (
        <>
          <div className="stat-grid">
            <StatCard label="Out of stock" value={num(data.summary.counts.STOCKOUT)} tone={data.summary.counts.STOCKOUT ? 'danger' : 'default'}
                      sub="Selling, but none on hand" />
            <StatCard label="Reorder now" value={num(data.summary.counts.REORDER_NOW)} tone={data.summary.counts.REORDER_NOW ? 'warning' : 'default'}
                      sub="At or below reorder point" />
            <StatCard label="Units to order" value={num(data.summary.unitsToOrder)} sub="Across all suggestions" />
            <StatCard label="Overstocked" value={num(data.summary.counts.OVERSTOCK)} sub={`More than ${data.params.overstockDays} days of cover`} />
          </div>

          <div className="toolbar">
            <select aria-label="Filter by status" value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All products</option>
              {Object.entries(STATUS).map(([k, s]) => <option key={k} value={k}>{s.label} ({data.summary.counts[k]})</option>)}
            </select>
            <label className="inline-field">Service level
              <select value={serviceLevel} onChange={(e) => setServiceLevel(e.target.value)} aria-label="Target service level">
                <option value="0.90">90%</option>
                <option value="0.95">95%</option>
                <option value="0.99">99%</option>
              </select>
            </label>
            {query.loading && <span className="muted">Updating…</span>}
          </div>

          <section className="card flush">
            {items.length === 0 ? (
              <EmptyState title="No products in this group" hint="Choose another status to see more products." />
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Product</th><th className="num">In stock</th><th className="num">Demand / day</th>
                      <th className="num">Days of cover</th><th className="num">Reorder level</th>
                      <th className="num">Suggested order</th><th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((i) => (
                      <tr key={i.productId}>
                        <td>
                          <strong>{i.name}</strong>
                          <small className="sub">{i.sku} · {i.reason}</small>
                        </td>
                        <td className="num">{num(i.stock)}{i.pendingQty > 0 && <small className="sub">+{num(i.pendingQty)} on order</small>}</td>
                        <td className="num">{rate(i.avgDailyDemand)}</td>
                        <td className="num">{days(i.daysOfCover)}</td>
                        <td className="num">
                          {num(i.currentReorderLevel)}
                          {i.recommendedReorderLevel !== i.currentReorderLevel && (
                            <small className="sub">suggest {num(i.recommendedReorderLevel)}</small>
                          )}
                        </td>
                        <td className="num"><strong>{i.suggestedOrderQty > 0 ? num(i.suggestedOrderQty) : '-'}</strong></td>
                        <td>
                          <Badge tone={STATUS[i.status].tone}>{STATUS[i.status].label}</Badge>
                          <small className="sub" title={CONFIDENCE_HINT[i.confidence]}>{i.confidence.toLowerCase()} confidence</small>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <p className="muted forecast-note">
            Forecasts use the last {data.params.windowDays} days of completed days (today is excluded), the supplier's
            average delivery time from received purchase orders (default {data.params.defaultLeadTime} days), and a
            {' '}{Math.round(data.params.serviceLevel * 100)}% service level. Days when an item was out of stock understate
            true demand. Suggestions are advice only: nothing is ordered or changed automatically.
          </p>
        </>
      )}
    </>
  );
}
