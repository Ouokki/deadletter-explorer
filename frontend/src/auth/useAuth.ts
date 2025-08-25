/* Hook-only file keeps HMR stable */
import { useContext } from 'react';
import { AuthContext, type AuthContextShape } from './AuthProvider';

export const useAuth = (): AuthContextShape => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within <AuthProvider>');
  return ctx;
};
