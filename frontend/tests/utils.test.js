import test from 'node:test';
import assert from 'node:assert/strict';
import { cents, fromCents, money, num, TX_LABELS, downloadCsv } from '../src/utils.js';

test('money in integer cents does not drift', () => {
  // 0.1 + 0.2 style errors: summing cents stays exact
  assert.equal(fromCents(cents('0.10') + cents('0.20')), 0.3);
  assert.equal(cents('19.99') * 3, 5997);
  assert.equal(cents(4.5), 450);
});

test('money and num show a dash for missing values', () => {
  assert.equal(money(null), '-');
  assert.equal(num(undefined), '-');
  assert.equal(num(1234), '1,234');
  assert.match(money(12.5), /12\.50/);
});

test('every transaction type the server can write has a label', () => {
  for (const t of ['INITIAL_STOCK', 'PURCHASE_RECEIVED', 'SALE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'TRANSFER_IN', 'TRANSFER_OUT']) {
    assert.ok(TX_LABELS[t], `missing label for ${t}`);
  }
});

test('CSV export defuses formulas, keeps negative numbers, and quotes special characters', async () => {
  let captured = null;
  globalThis.URL.createObjectURL = (blob) => { captured = blob; return 'blob:x'; };
  globalThis.URL.revokeObjectURL = () => {};
  globalThis.document = {
    body: { appendChild() {} },
    createElement: () => ({ click() {}, remove() {} }),
  };
  downloadCsv('t.csv', ['a', 'b'], [['=1+1', -5], ['say "hi", ok', '@x'], [null, 'line1\nline2']]);
  const text = (await captured.text()).replace(/^﻿/, '');
  const rows = text.split('\r\n');
  assert.equal(rows[0], 'a,b');
  assert.equal(rows[1], "'=1+1,-5");               // formula neutralised, real number untouched
  assert.equal(rows[2], '"say ""hi"", ok",\'@x');
  assert.equal(rows[3], ',"line1\nline2"'.replace('\n', '\n'));
});
