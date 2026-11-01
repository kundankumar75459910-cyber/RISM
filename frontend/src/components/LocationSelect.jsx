/** <select> of locations. The first open location is the server's default, so it is preselected by callers. */
export default function LocationSelect({ locations, value, onChange, allLabel, ...rest }) {
  return (
    <select value={value} onChange={(e) => onChange(e.target.value)} {...rest}>
      {allLabel !== undefined && <option value="">{allLabel}</option>}
      {locations.map((l) => <option key={l.id} value={l.id}>{l.name}{l.active === false ? ' (closed)' : ''}</option>)}
    </select>
  );
}
