import { api } from '../api';
import { useQuery } from '../hooks';
import { Badge, ErrorBox, PageHeader, Spinner, StatCard } from '../components/ui';
import { BarChart, LineChart } from '../components/Charts';
import { dateOnly, dateTime, money, num, TX_LABELS } from '../utils';

export default function Dashboard() {
  const q = useQuery(() => api.get('/dashboard'), []);
  if (q.error && !q.data) return <ErrorBox message={q.error} onRetry={q.reload} />;
  if (!q.data) return <Spinner />;
  const d = q.data;

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Live overview of stock, suppliers and sales">
        <button className="btn" onClick={q.reload} disabled={q.loading}>{q.loading ? 'Refreshing…' : 'Refresh'}</button>
      </PageHeader>

      <div className="stat-grid">
        <StatCard label="Total products" value={num(d.totalProducts)} />
        <StatCard label="Units in stock" value={num(d.totalQuantity)} sub={`Stock value ${money(d.inventoryValue)}`} />
        <StatCard label="Low-stock products" value={num(d.lowStockCount)} tone={d.lowStockCount > 0 ? 'warning' : 'default'}
                  sub={d.lowStockCount > 0 ? 'At or below reorder level' : 'Everything above reorder level'} />
        <StatCard label="Suppliers" value={num(d.totalSuppliers)} />
        <StatCard label="Total sales" value={money(d.totalSalesAmount)} sub={`${num(d.totalSalesCount)} sales recorded`} tone="success" />
      </div>

      <div className="grid-2">
        <section className="card">
          <h3>Stock by category</h3>
          {d.stockByCategory.length === 0
            ? <p className="muted">No products yet.</p>
            : <BarChart data={d.stockByCategory.map((c) => ({ label: c.category, value: c.quantity }))} />}
        </section>
        <section className="card">
          <h3>Sales - last 14 days</h3>
          <LineChart data={d.salesTrend.map((s) => ({ label: dateOnly(s.date), value: Number(s.amount) }))} />
        </section>
      </div>

      {d.stockByLocation?.length > 1 && (
        <section className="card">
          <h3>Stock by location</h3>
          <BarChart data={d.stockByLocation.map((l) => ({ label: l.location, value: l.quantity }))} />
        </section>
      )}

      <div className="grid-2 grid-2-wide-left">
        <section className="card">
          <h3>Recent inventory transactions</h3>
          {d.recentTransactions.length === 0 ? <p className="muted">No transactions yet.</p> : (
            <div className="table-wrap">
              <table>
                <thead><tr><th>When</th><th>Product</th><th>Type</th><th className="num">Change</th><th className="num">Balance</th></tr></thead>
                <tbody>
                  {d.recentTransactions.map((t) => (
                    <tr key={t.id}>
                      <td className="nowrap">{dateTime(t.createdAt)}</td>
                      <td>{t.productName}<small className="sub">{t.sku}</small></td>
                      <td>{TX_LABELS[t.type] || t.type}</td>
                      <td className={`num ${t.quantityChange > 0 ? 'pos' : 'neg'}`}>{t.quantityChange > 0 ? '+' : ''}{t.quantityChange}</td>
                      <td className="num">{t.quantityAfter}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
        <section className="card">
          <h3>Needs restocking</h3>
          {d.lowStockProducts.length === 0 ? <p className="muted">No low-stock products.</p> : (
            <ul className="plain-list">
              {d.lowStockProducts.map((p) => (
                <li key={p.id}>
                  <div>
                    <strong>{p.name}</strong>
                    <small className="sub">{p.sku} · reorder at {p.reorderLevel}</small>
                  </div>
                  <Badge tone={p.quantity === 0 ? 'red' : 'amber'}>{p.quantity === 0 ? 'Out of stock' : `${p.quantity} left`}</Badge>
                </li>
              ))}
            </ul>
          )}
          {d.lowStockCount > d.lowStockProducts.length && (
            <a className="link" href="#/inventory">View all {d.lowStockCount} low-stock products →</a>
          )}
        </section>
      </div>
    </>
  );
}
