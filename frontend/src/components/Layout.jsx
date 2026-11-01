import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import Icon from './Icon';

export const NAV = [
  { to: '/', label: 'Dashboard', icon: 'dashboard' },
  { to: '/products', label: 'Products', icon: 'products' },
  { to: '/inventory', label: 'Inventory', icon: 'inventory' },
  { to: '/suppliers', label: 'Suppliers', icon: 'suppliers' },
  { to: '/purchases', label: 'Purchases', icon: 'purchases' },
  { to: '/sales', label: 'Sales', icon: 'sales' },
  { to: '/reports', label: 'Reports', icon: 'reports' },
  { to: '/forecast', label: 'Forecast', icon: 'forecast' },
  { to: '/locations', label: 'Locations', icon: 'locations', admin: true },
  { to: '/categories', label: 'Categories', icon: 'categories', admin: true },
  { to: '/users', label: 'Users', icon: 'users', admin: true },
];

export default function Layout({ path, title, children }) {
  const { user, isAdmin, logout } = useAuth();
  const [open, setOpen] = useState(false);

  useEffect(() => { setOpen(false); }, [path]);   // close the mobile menu after navigating

  const isActive = (to) => (to === '/' ? path === '/' : path === to || path.startsWith(`${to}/`));

  return (
    <div className="app-shell">
      <aside className={`sidebar${open ? ' open' : ''}`}>
        <div className="brand">
          <span className="brand-mark">R</span>
          <div>
            <strong>RIMS</strong>
            <small>Inventory Manager</small>
          </div>
        </div>
        <nav aria-label="Main navigation">
          {NAV.filter((n) => !n.admin || isAdmin).map((n) => (
            <a key={n.to} href={`#${n.to}`} className={`nav-link${isActive(n.to) ? ' active' : ''}`}
               aria-current={isActive(n.to) ? 'page' : undefined}>
              <Icon name={n.icon} />
              <span>{n.label}</span>
            </a>
          ))}
        </nav>
      </aside>
      {open && <div className="sidebar-scrim" onClick={() => setOpen(false)} />}

      <div className="main">
        <header className="topbar">
          <button className="icon-btn menu-btn" onClick={() => setOpen((o) => !o)} aria-label="Toggle navigation">
            <Icon name="menu" size={22} />
          </button>
          <h2 className="topbar-title">{title}</h2>
          <div className="topbar-user">
            <div className="user-chip">
              <span className="avatar">{(user.fullName || user.username).charAt(0).toUpperCase()}</span>
              <div className="user-meta">
                <strong>{user.fullName}</strong>
                <small>{user.role === 'ADMIN' ? 'Administrator' : 'Staff'}</small>
              </div>
            </div>
            <button className="btn btn-sm" onClick={logout}>
              <Icon name="logout" size={15} /> Log out
            </button>
          </div>
        </header>
        <main className="content">{children}</main>
      </div>
    </div>
  );
}
