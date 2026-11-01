import { num } from '../utils';

export function Spinner({ label = 'Loading…' }) {
  return (
    <div className="spinner-wrap" role="status">
      <div className="spinner" />
      <span>{label}</span>
    </div>
  );
}

export function Badge({ tone = 'gray', children }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

export function EmptyState({ title, hint, children }) {
  return (
    <div className="empty">
      <strong>{title}</strong>
      {hint && <p>{hint}</p>}
      {children}
    </div>
  );
}

export function ErrorBox({ message, onRetry }) {
  return (
    <div className="alert alert-error" role="alert">
      <span>{message}</span>
      {onRetry && <button className="btn btn-sm" onClick={onRetry}>Retry</button>}
    </div>
  );
}

export function PageHeader({ title, subtitle, children }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      <div className="page-actions">{children}</div>
    </div>
  );
}

export function StatCard({ label, value, sub, tone = 'default' }) {
  return (
    <div className={`stat stat-${tone}`}>
      <span className="stat-label">{label}</span>
      <strong className="stat-value">{value}</strong>
      {sub && <span className="stat-sub">{sub}</span>}
    </div>
  );
}

export function Field({ label, error, hint, children }) {
  return (
    <label className={`field${error ? ' has-error' : ''}`}>
      <span className="field-label">{label}</span>
      {children}
      {hint && !error && <small className="field-hint">{hint}</small>}
      {error && <small className="field-error">{error}</small>}
    </label>
  );
}

export function Tabs({ tabs, active, onChange }) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((t) => (
        <button key={t.key} role="tab" aria-selected={active === t.key}
                className={`tab${active === t.key ? ' active' : ''}`} onClick={() => onChange(t.key)}>
          {t.label}
        </button>
      ))}
    </div>
  );
}

export function Pagination({ data, onChange }) {
  if (!data || data.totalElements === 0) return null;
  const { page, size, totalElements, totalPages } = data;
  const from = page * size + 1;
  const to = Math.min((page + 1) * size, totalElements);
  return (
    <div className="pagination">
      <span className="muted">Showing {num(from)}–{num(to)} of {num(totalElements)}</span>
      <div className="pagination-buttons">
        <button className="btn btn-sm" disabled={page <= 0} onClick={() => onChange(page - 1)}>Previous</button>
        <span className="muted">Page {page + 1} of {Math.max(totalPages, 1)}</span>
        <button className="btn btn-sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</button>
      </div>
    </div>
  );
}

/** Wraps a data table with loading / error / empty handling. */
export function DataState({ query, empty, children }) {
  const { data, loading, error, reload } = query;
  if (error && !data) return <ErrorBox message={error} onRetry={reload} />;
  if (!data) return <Spinner />;
  const rows = Array.isArray(data) ? data : data.content;
  return (
    <div className={loading ? 'is-refreshing' : ''}>
      {error && <ErrorBox message={error} onRetry={reload} />}
      {rows.length === 0 ? (empty || <EmptyState title="Nothing to show" />) : children}
    </div>
  );
}
