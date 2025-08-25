import React, { useEffect, useRef, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthProvider';

export const LoginGate: React.FC = () => {
  const { initialized, authenticated, login } = useAuth();
  const loc = useLocation();
  const from = (loc.state as any)?.from?.pathname || '/home';

  const [redirecting, setRedirecting] = useState(false);
  const loginCalledRef = useRef(false); // prevents double-call in React 18 StrictMode

  useEffect(() => {
    if (!initialized || authenticated) return;
    if (loginCalledRef.current) return;

    loginCalledRef.current = true;
    setRedirecting(true);
    login({ redirectUri: window.location.origin });
  }, [initialized, authenticated, login]);

  if (!initialized || redirecting) {
    return (
      <div style={{
        minHeight: '100vh', display: 'flex', alignItems: 'center',
        justifyContent: 'center', fontFamily: 'system-ui, sans-serif'
      }}>
        <div>
          <div style={{
            width: 40, height: 40, borderRadius: '50%',
            border: '4px solid #e5e7eb', borderTopColor: '#111827',
            animation: 'spin 1s linear infinite', margin: '0 auto 12px'
          }} />
          <div>Redirecting to sign in…</div>
          <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
        </div>
      </div>
    );
  }

  if (authenticated) return <Navigate to={from} replace />;

  return null;
};
