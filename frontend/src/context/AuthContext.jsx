import { createContext, useContext, useState, useCallback, useEffect } from 'react';

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

  // Fired by api/client.js when a signed-in request comes back 401. Clearing
  // the session sends ProtectedRoute to /login, which shows the notice.
  useEffect(() => {
    function expired() {
      try { sessionStorage.setItem('rentez_session_expired', '1'); } catch { /* storage blocked */ }
      logout();
    }
    window.addEventListener('rentez:session-expired', expired);
    return () => window.removeEventListener('rentez:session-expired', expired);
  }, [logout]);

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
