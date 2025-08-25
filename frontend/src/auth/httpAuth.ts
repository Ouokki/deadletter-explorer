// Small helper so non-React modules (API service) can get the token.

export type TokenGetter = () => Promise<string | undefined>;

let _getToken: TokenGetter | null = null;

export function configureAuth(getToken: TokenGetter) {
  _getToken = getToken;
}

async function buildAuthHeaders(extra?: HeadersInit): Promise<HeadersInit> {
  const base: Record<string, string> = {};
  if (_getToken) {
    const t = await _getToken();
    if (t) base.Authorization = `Bearer ${t}`;
  }
  // Merge with any incoming headers (Request/Headers/string maps are all possible)
  const incoming: Record<string, string> = {};
  if (extra instanceof Headers) {
    extra.forEach((v, k) => (incoming[k] = v));
  } else if (Array.isArray(extra)) {
    for (const [k, v] of extra) incoming[k] = v as string;
  } else if (extra && typeof extra === 'object') {
    Object.assign(incoming, extra as Record<string, string>);
  }
  return { ...incoming, ...base };
}

/**
 * Wrapper around fetch that automatically adds the Authorization header.
 * Retries once on 401 after asking for a fresh token.
 */
export async function authFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const headers = await buildAuthHeaders(init.headers);
  let res = await fetch(input, { ...init, headers });

  if (res.status === 401 && _getToken) {
    // Try to refresh token via getToken() and retry once
    const headers2 = await buildAuthHeaders(init.headers);
    res = await fetch(input, { ...init, headers: headers2 });
  }
  return res;
}
