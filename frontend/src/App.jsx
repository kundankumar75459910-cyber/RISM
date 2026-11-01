import { useAuth } from './context/AuthContext';
import { useHashPath } from './hooks';
import Layout from './components/Layout';
import { EmptyState, Spinner } from './components/ui';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Products from './pages/Products';
import Inventory from './pages/Inventory';
import Suppliers from './pages/Suppliers';
import Purchases from './pages/Purchases';
import Sales from './pages/Sales';
import Reports from './pages/Reports';
import Forecast from './pages/Forecast';
import Locations from './pages/Locations';
import Categories from './pages/Categories';
import Users from './pages/Users';

// Route table: path -> page. `admin` routes are hidden from staff in the sidebar
// and shown an "access denied" message here. The backend (SecurityConfig) is the
// real enforcement; this is only the user-facing side.
const ROUTES = {
  '/': { title: 'Dashboard', Page: Dashboard },
  '/products': { title: 'Products', Page: Products },
  '/inventory': { title: 'Inventory', Page: Inventory },
  '/suppliers': { title: 'Suppliers', Page: Suppliers },
  '/purchases': { title: 'Purchases', Page: Purchases },
  '/sales': { title: 'Sales', Page: Sales },
  '/reports': { title: 'Reports', Page: Reports },
  '/forecast': { title: 'Demand forecast', Page: Forecast },
  '/locations': { title: 'Locations', Page: Locations, admin: true },
  '/categories': { title: 'Categories', Page: Categories, admin: true },
  '/users': { title: 'Users', Page: Users, admin: true },
};

export default function App() {
  const { user, loading, isAdmin } = useAuth();
  const path = useHashPath();

  if (loading) return <Spinner label="Restoring your session…" />;
  if (!user) return <Login />;

  const route = ROUTES[path];
  let content;
  if (!route) {
    content = <EmptyState title="Page not found" hint="The page you are looking for does not exist.">
      <a className="btn" href="#/">Go to dashboard</a>
    </EmptyState>;
  } else if (route.admin && !isAdmin) {
    content = <EmptyState title="Access denied" hint="Only administrators can open this page." />;
  } else {
    const { Page } = route;
    content = <Page />;
  }

  return (
    <Layout path={path} title={route ? route.title : 'Not found'}>
      {content}
    </Layout>
  );
}
