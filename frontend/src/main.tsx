import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthProvider';
import { ApiAuthBridge } from './auth/ApiAuthBridge';
import { Navbar } from './components/Navbar';
import App from './App';
import './styles.css';
import { LoginGate } from './auth/LoginGate';
import { ProtectedRoute } from './auth/ProtectedRoute';

const container = document.getElementById('root');
if (!container) throw new Error('Root container #root not found');
const root = createRoot(container);

root.render(
  <React.StrictMode>
    <AuthProvider
      url="http://localhost:8081"
      realm="dle"
      clientId="dle-frontend"
      initOptions={{
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
      }}
    >
      <ApiAuthBridge />
      <BrowserRouter>
        <Navbar />
        <Routes>
          <Route path="/" element={<LoginGate />} />
          <Route
            path="/home"
            element={
              <ProtectedRoute>
                <App />
              </ProtectedRoute>
            }
          />
          {/* add more protected routes the same way */}
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  </React.StrictMode>
);
