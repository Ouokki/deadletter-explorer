/* @refresh reset */
import React, {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  PropsWithChildren,
  useContext,
} from 'react';
import type {
  KeycloakInitOptions,
  KeycloakLoginOptions,
  KeycloakLogoutOptions,
} from 'keycloak-js';
import { getKeycloak, ensureInit } from './kc-singleton';
import { AuthUser } from '../types/types';


export type AuthContextShape = {
  initialized: boolean;
  authenticated: boolean;
  user?: AuthUser | null;
  token?: string;
  getToken: () => Promise<string | undefined>;
  login: (opts?: KeycloakLoginOptions) => void;
  logout: (opts?: KeycloakLogoutOptions) => void;
  refresh: (minValiditySeconds?: number) => Promise<string | undefined>;
  hasRealmRole: (role: string) => boolean;
  hasClientRole: (clientId: string, role: string) => boolean;
};

export const AuthContext = createContext<AuthContextShape | null>(null);

export type AuthProviderProps = PropsWithChildren<{
  url: string;
  realm: string;
  clientId: string;
  initOptions?: KeycloakInitOptions;
  refreshIntervalMs?: number;
  minValiditySeconds?: number;
}>;

let _kcInitPromise: Promise<boolean> | null = null;

export const AuthProvider: React.FC<AuthProviderProps> = ({
  url,
  realm,
  clientId,
  initOptions,
  refreshIntervalMs = 10_000,
  minValiditySeconds = 30,
  children,
}) => {
  const kc = useRef(
    getKeycloak({ url, realm, clientId })
  ).current;

  const [initialized, setInitialized] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [token, setToken] = useState<string | undefined>();
  const [user, setUser] = useState<AuthUser | null>(null);

  const buildUser = useCallback((tp: any | undefined): AuthUser | null => {
    if (!tp) return null;
    const realmRoles: string[] = tp?.realm_access?.roles ?? [];
    const ra = tp?.resource_access ?? {};
    const clientRoles = Object.fromEntries(
      Object.keys(ra).map((k) => [k, ra[k]?.roles ?? []])
    );
    return {
      username: tp?.preferred_username,
      email: tp?.email,
      firstName: tp?.given_name || tp?.firstName,
      lastName: tp?.family_name || tp?.lastName,
      realmRoles,
      clientRoles,
      raw: tp,
    };
  }, []);

  // Init + keycloak event handlers
  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        kc.onAuthLogout = () => {
          setAuthenticated(false);
          setUser(null);
          setToken(undefined);
        };
        kc.onAuthError = (err) => console.warn('Keycloak auth error', err);

        kc.onTokenExpired = async () => {
          try {
            const ok = await kc.updateToken(minValiditySeconds);
            if (ok) {
              setToken(kc.token);
              setUser(buildUser(kc.tokenParsed));
            } else {
              setAuthenticated(false);
            }
          } catch {
            setAuthenticated(false);
          }
        };

        const origin = window.location.origin;
        const defaults: KeycloakInitOptions = {
          onLoad: 'check-sso',
          pkceMethod: 'S256',
          silentCheckSsoRedirectUri: `${origin}/silent-check-sso.html`,
          checkLoginIframe: false,
        };

        if (!_kcInitPromise) {
          _kcInitPromise = ensureInit(kc, { ...defaults, ...initOptions });
        }
        const auth = await _kcInitPromise;
        if (cancelled) return;

        setAuthenticated(Boolean(auth));
        setToken(kc.token);
        setUser(buildUser(kc.tokenParsed));
      } catch (e) {
        console.error('Keycloak init failed', e);
      } finally {
        if (!cancelled) setInitialized(true);
      }
    })();

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url, realm, clientId]);

  // Periodic refresh
  useEffect(() => {
    const id = window.setInterval(async () => {
      if (!kc.authenticated) return;
      try {
        const refreshed = await kc.updateToken(minValiditySeconds);
        if (refreshed) {
          setToken(kc.token);
          setUser(buildUser(kc.tokenParsed));
        }
      } catch {
        // network hiccup, ignore; next tick will retry
      }
    }, refreshIntervalMs);

    return () => window.clearInterval(id);
  }, [kc, refreshIntervalMs, minValiditySeconds, buildUser]);

  // Refresh when tab becomes visible again
  useEffect(() => {
    const handler = async () => {
      if (document.visibilityState === 'visible' && kc.authenticated) {
        try {
          const refreshed = await kc.updateToken(minValiditySeconds);
          if (refreshed) {
            setToken(kc.token);
            setUser(buildUser(kc.tokenParsed));
          }
        } catch {
          // ignore; will retry on interval
        }
      }
    };
    document.addEventListener('visibilitychange', handler);
    return () => document.removeEventListener('visibilitychange', handler);
  }, [kc, minValiditySeconds, buildUser]);

  // Cross-tab logout sync (optional but nice)
  useEffect(() => {
    const storageHandler = (e: StorageEvent) => {
      if (e.key === 'kc:logout' && e.newValue === '1') {
        setAuthenticated(false);
        setUser(null);
        setToken(undefined);
        localStorage.removeItem('kc:logout');
      }
    };
    window.addEventListener('storage', storageHandler);
    return () => window.removeEventListener('storage', storageHandler);
  }, []);

  const login = useCallback(
    (opts?: KeycloakLoginOptions) =>
      kc.login({ redirectUri: window.location.origin, ...opts }),
    [kc]
  );

  const logout = useCallback(
    (opts?: KeycloakLogoutOptions) => {
      // notify other tabs
      try {
        localStorage.setItem('kc:logout', '1');
      } catch {}
      return kc.logout({ redirectUri: window.location.origin, ...opts });
    },
    [kc]
  );

  const refresh = useCallback(
    async (minValidity = minValiditySeconds) => {
      if (!kc.authenticated) return undefined;
      const refreshed = await kc.updateToken(minValidity).catch(() => false);
      if (refreshed) {
        setToken(kc.token);
        setUser(buildUser(kc.tokenParsed));
      }
      return kc.token;
    },
    [kc, buildUser, minValiditySeconds]
  );

  const getToken = useCallback(async () => {
    if (!kc.authenticated) return undefined;
    await kc.updateToken(minValiditySeconds).catch(() => {});
    return kc.token;
  }, [kc, minValiditySeconds]);

  const hasRealmRole = useCallback(
    (role: string) => !!user?.realmRoles?.includes(role),
    [user]
  );

  const hasClientRole = useCallback(
    (clientIdParam: string, role: string) =>
      !!user?.clientRoles?.[clientIdParam]?.includes(role),
    [user]
  );

  const value = useMemo<AuthContextShape>(
    () => ({
      initialized,
      authenticated,
      user,
      token,
      getToken,
      login,
      logout,
      refresh,
      hasRealmRole,
      hasClientRole,
    }),
    [
      initialized,
      authenticated,
      user,
      token,
      getToken,
      login,
      logout,
      refresh,
      hasRealmRole,
      hasClientRole,
    ]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

/** Stable hook export to consume the context throughout the app */
export const useAuth = (): AuthContextShape => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within <AuthProvider>');
  return ctx;
};
