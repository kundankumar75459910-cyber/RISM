import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, getToken, onUnauthorized, setToken } from '../api';
import { useToast } from './ToastContext';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const toast = useToast();
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(() => !!getToken());

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
    window.location.hash = '/';
  }, []);

  // any 401 from the API (expired / revoked token) logs the user out
  useEffect(() => {
    onUnauthorized(() => {
      logout();
      toast.error('Your session has expired. Please log in again.');
    });
  }, [logout, toast]);

  // restore the session from a stored token
  useEffect(() => {
    if (!getToken()) return;
    api.get('/auth/me')
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (username, password) => {
    const res = await api.post('/auth/login', { username, password });
    setToken(res.token);
    setUser(res.user);
    window.location.hash = '/';
  }, []);

  const value = useMemo(() => ({
    user, loading, login, logout, isAdmin: user?.role === 'ADMIN',
  }), [user, loading, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
