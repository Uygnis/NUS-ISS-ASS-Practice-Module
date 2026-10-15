import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/useAuth';

export default function ProtectedRoute({ roles, children }) {
  const { auth } = useAuth();
  if (!auth) return <Navigate to="/login" replace />;
  if (roles && !roles.includes(auth.role)) return <Navigate to="/browse" replace />;
  return children;
}
