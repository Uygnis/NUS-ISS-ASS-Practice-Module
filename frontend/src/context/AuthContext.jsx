import { createContext, useContext, useState, useCallback } from 'react';

const AuthContext = createContext(null);
const STORAGE_KEY = 'rentez_auth';

function loadAuth() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null');
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(loadAuth);

  const login = useCallback((authResponse) => {
    const session = {
      token: authResponse.token,
      tokenType: authResponse.tokenType || 'Bearer',
      userId: authResponse.userId,
      fullName: authResponse.fullName,
      role: authResponse.role,
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    setAuth(session);
  }, []);

  const updateName = useCallback((fullName) => {
    setAuth((prev) => {
      if (!prev) return prev;
      const next = { ...prev, fullName };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      return next;
    });
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    setAuth(null);
  }, []);

  return (
    <AuthContext.Provider value={{ auth, login, logout, updateName }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
