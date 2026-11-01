// Thin fetch wrapper: base URL, JWT header, JSON handling and uniform errors.
const BASE = ((import.meta.env && import.meta.env.VITE_API_URL) || 'http://localhost:9090/api').replace(/\/$/, '');
const TOKEN_KEY = 'rims_token';

export const getToken = () => {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
};
export const setToken = (token) => {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch { /* storage unavailable */ }
};

let unauthorizedHandler = null;
export const onUnauthorized = (fn) => { unauthorizedHandler = fn; };

export class ApiError extends Error {
  constructor(message, status, fieldErrors) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors || {};
  }
}

function queryString(params) {
  if (!params) return '';
  const sp = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '' && v !== false) sp.append(k, String(v));
  });
  const s = sp.toString();
  return s ? `?${s}` : '';
}

// The Python forecasting service (rims/ml) is a separate process with its own base URL.
const ML_BASE = ((import.meta.env && import.meta.env.VITE_ML_URL) || 'http://localhost:8000/api').replace(/\/$/, '');

async function request(method, path, { params, body, base = BASE } = {}) {
  const headers = { Accept: 'application/json' };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let res;
  try {
    res = await fetch(`${base}${path}${queryString(params)}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(base === BASE
      ? 'Cannot reach the server. Is the backend running?'
      : 'Cannot reach the forecasting service. Is it running? (see ml/README.md)', 0);
  }
  if (res.status === 204) return null;

  let data = null;
  try { data = await res.json(); } catch { /* empty or non-JSON body */ }

  if (!res.ok) {
    if (res.status === 401 && token && unauthorizedHandler && !path.startsWith('/auth/login')) {
      unauthorizedHandler();
    }
    throw new ApiError((data && data.message) || `Request failed (${res.status})`, res.status, data && data.fieldErrors);
  }
  return data;
}

export const api = {
  get: (path, params) => request('GET', path, { params }),
  post: (path, body) => request('POST', path, { body: body === undefined ? {} : body }),
  put: (path, body) => request('PUT', path, { body }),
  del: (path) => request('DELETE', path),
};

/** Downloads a file from the API (e.g. a CSV) using the logged-in user's token. */
export async function downloadFile(path, params, filename) {
  const headers = { Accept: '*/*' };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  let res;
  try {
    res = await fetch(`${BASE}${path}${queryString(params)}`, { headers });
  } catch {
    throw new ApiError('Cannot reach the server. Is the backend running?', 0);
  }
  if (!res.ok) {
    let message = `Download failed (${res.status})`;
    try { message = (await res.json()).message || message; } catch { /* not JSON */ }
    throw new ApiError(message, res.status);
  }
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

/** Forecasting service (Python). Same JWT, so users only see what their RIMS login allows. */
export const mlApi = {
  get: (path, params) => request('GET', path, { params, base: ML_BASE }),
};
