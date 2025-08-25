import Keycloak, { KeycloakConfig, KeycloakInitOptions } from 'keycloak-js';

let _kc: Keycloak | null = null;

export function getKeycloak(cfg: KeycloakConfig): Keycloak {
  if (_kc) return _kc;
  _kc = new Keycloak(cfg);
  return _kc;
}

/**
 * Ensure init is called only once across the app.
 * Returns the `authenticated` boolean.
 */
export async function ensureInit(
  kc: Keycloak,
  options: KeycloakInitOptions
): Promise<boolean> {
  // keycloak-js returns boolean | void depending on options; force boolean
  const res = await kc.init(options as KeycloakInitOptions);
  return Boolean(res);
}
