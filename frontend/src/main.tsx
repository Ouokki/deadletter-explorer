// index.tsx
import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Route, Routes, Outlet, Link } from 'react-router-dom';
import { AuthProvider } from './auth/AuthProvider';
import { ApiAuthBridge } from './auth/ApiAuthBridge';
import App from './App';
import './styles.css';
import { LoginGate } from './auth/LoginGate';
import RedactionStudioPage from './pages/RedactionStudioPage';
import { Navbar } from './components/Navbar';

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
          <Route path="/home" element={<App />} />
          <Route path="/topics/:topic/redaction" element={<RedactionStudioPage />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  </React.StrictMode>
);
