import { money, num } from '../utils';

/** Horizontal bars, pure HTML/CSS. data: [{ label, value }] */
export function BarChart({ data, formatValue = num }) {
  const max = Math.max(1, ...data.map((d) => d.value));
  return (
    <div className="bars" role="img" aria-label="Bar chart">
      {data.map((d) => (
        <div className="bar-row" key={d.label}>
          <span className="bar-label" title={d.label}>{d.label}</span>
          <div className="bar-track">
            <div className="bar-fill" style={{ width: `${(d.value / max) * 100}%` }} />
          </div>
          <span className="bar-value">{formatValue(d.value)}</span>
        </div>
      ))}
    </div>
  );
}

/** Area/line chart in SVG. data: [{ label, value }] */
export function LineChart({ data, formatValue = money, height = 220 }) {
  const W = 640;
  const H = height;
  const pad = { l: 56, r: 14, t: 14, b: 28 };
  const max = Math.max(1, ...data.map((d) => d.value));
  const niceMax = niceCeil(max);
  const innerW = W - pad.l - pad.r;
  const innerH = H - pad.t - pad.b;
  const x = (i) => pad.l + (data.length <= 1 ? innerW / 2 : (i / (data.length - 1)) * innerW);
  const y = (v) => pad.t + innerH - (v / niceMax) * innerH;
  const points = data.map((d, i) => `${x(i)},${y(d.value)}`).join(' ');
  const area = data.length
    ? `${x(0)},${y(0)} ${points} ${x(data.length - 1)},${y(0)}`
    : '';
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => f * niceMax);
  const labelEvery = Math.ceil(data.length / 7);

  return (
    <svg className="line-chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Line chart">
      {ticks.map((t) => (
        <g key={t}>
          <line x1={pad.l} x2={W - pad.r} y1={y(t)} y2={y(t)} className="grid" />
          <text x={pad.l - 8} y={y(t) + 4} textAnchor="end" className="axis">{compact(t)}</text>
        </g>
      ))}
      {area && <polygon points={area} className="area" />}
      {points && <polyline points={points} className="line" fill="none" />}
      {data.map((d, i) => (
        <g key={d.label + i}>
          <circle cx={x(i)} cy={y(d.value)} r="3.5" className="dot">
            <title>{`${d.label}: ${formatValue(d.value)}`}</title>
          </circle>
          {i % labelEvery === 0 && (
            <text x={x(i)} y={H - 8} textAnchor="middle" className="axis">{d.label}</text>
          )}
        </g>
      ))}
    </svg>
  );
}

function niceCeil(v) {
  const pow = 10 ** Math.floor(Math.log10(v));
  const n = v / pow;
  const step = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
  return step * pow;
}

function compact(v) {
  return new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(v);
}
