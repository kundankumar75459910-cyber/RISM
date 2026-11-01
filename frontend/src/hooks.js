import { useEffect, useState } from 'react';
import { api } from './api';

/** Runs an async loader whenever deps change. Returns { data, loading, error, reload }. */
export function useQuery(loader, deps) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setState((s) => ({ ...s, loading: true, error: null }));
    loader()
      .then((data) => { if (!cancelled) setState({ data, loading: false, error: null }); })
      .catch((err) => { if (!cancelled) setState((s) => ({ data: s.data, loading: false, error: err.message })); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  return { ...state, reload: () => setTick((t) => t + 1) };
}

export function useDebounce(value, delay = 300) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

/** If the current page became empty (e.g. last row deleted), step back one page. */
export function useClampPage(query, page, setPage) {
  useEffect(() => {
    const d = query.data;
    if (d && d.content.length === 0 && page > 0) setPage(Math.max(Math.min(page - 1, d.totalPages - 1), 0));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query.data]);
}

/** Minimal hash router: "#/products" -> "/products". */
export function useHashPath() {
  const read = () => window.location.hash.replace(/^#/, '') || '/';
  const [path, setPath] = useState(read);
  useEffect(() => {
    const onChange = () => setPath(read());
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return path;
}

export const navigate = (to) => { window.location.hash = to; };

/** All locations (open and closed). Most forms only offer the open ones: see openLocations(). */
export function useLocations() {
  return useQuery(() => api.get('/locations'), []);
}

export const openLocations = (query) => (query.data || []).filter((l) => l.active);
