import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthProvider';

export const ProtectedRoute: React.FC<React.PropsWithChildren> = ({ children }) => {
  const { initialized, authenticated } = useAuth();
  const loc = useLocation();

  if (!initialized) return null; // or a spinner
  if (!authenticated) return <Navigate to="/" replace state={{ from: loc }} />;
  return <>{children}</>;
};
