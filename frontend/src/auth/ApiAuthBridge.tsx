import { useEffect } from 'react';
import { configureAuth } from './httpAuth';
import { useAuth } from './AuthProvider';

export function ApiAuthBridge() {
  const { getToken } = useAuth();
  useEffect(() => { configureAuth(getToken); }, [getToken]);
  return null;
}