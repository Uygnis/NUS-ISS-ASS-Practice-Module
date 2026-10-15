import { createContext, useContext } from 'react';

// The context object and its hook live apart from AuthProvider so the .jsx
// file exports only a component (React Fast Refresh).
export const AuthContext = createContext(null);

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
