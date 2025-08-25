import React from 'react';
import { useAuth } from '../auth/AuthProvider';

export const Navbar: React.FC = () => {
  const { authenticated, user, login, logout } = useAuth();

  return (
    <nav style={{
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      padding: '10px 16px',
      background: '#111827',
      color: '#fff',
    }}>
      <strong>Dead Letter Explorer</strong>
      {authenticated ? (
        <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
          <span>👋 {user?.username ?? `${user?.firstName ?? ''} ${user?.lastName ?? ''}`}</span>
          <button onClick={() => logout({ redirectUri: window.location.origin })}>
            Logout
          </button>
        </div>
      ) : (
        <button onClick={() => login()}>
          Login
        </button>
      )}
    </nav>
  );
};
