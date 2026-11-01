const CURRENCY = (import.meta.env && import.meta.env.VITE_CURRENCY) || 'USD';
const LOCALE = (import.meta.env && import.meta.env.VITE_LOCALE) || 'en-US';

const moneyFormat = new Intl.NumberFormat(LOCALE, { style: 'currency', currency: CURRENCY });
const numberFormat = new Intl.NumberFormat(LOCALE);

export const money = (v) => (v === null || v === undefined ? '-' : moneyFormat.format(Number(v)));
export const num = (v) => (v === null || v === undefined ? '-' : numberFormat.format(Number(v)));

export function dateTime(s) {
  if (!s) return '-';
  const d = new Date(s);
  return Number.isNaN(d.getTime()) ? '-' : d.toLocaleString(LOCALE, { dateStyle: 'medium', timeStyle: 'short' });
}

export function dateOnly(s) {
  if (!s) return '-';
  const d = new Date(s.length === 10 ? `${s}T00:00:00` : s);
  return Number.isNaN(d.getTime()) ? '-' : d.toLocaleDateString(LOCALE, { month: 'short', day: 'numeric' });
}

/** exact money arithmetic on the client (integer cents) - the server stays authoritative */
export const cents = (v) => Math.round(Number(v) * 100);
export const fromCents = (c) => c / 100;

export const TX_LABELS = {
  INITIAL_STOCK: 'Opening stock',
  PURCHASE_RECEIVED: 'Purchase received',
  SALE: 'Sale',
  ADJUSTMENT_IN: 'Adjustment (in)',
  ADJUSTMENT_OUT: 'Adjustment (out)',
  TRANSFER_IN: 'Transfer in',
  TRANSFER_OUT: 'Transfer out',
};

/** Download rows as CSV. Cells that look like spreadsheet formulas are neutralised. */
export function downloadCsv(filename, headers, rows) {
  const esc = (v) => {
    let s = v === null || v === undefined ? '' : String(v);
    if (/^[=+\-@]/.test(s) && Number.isNaN(Number(s))) s = `'${s}`;
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const text = [headers, ...rows].map((r) => r.map(esc).join(',')).join('\r\n');
  const blob = new Blob(['﻿', text], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
