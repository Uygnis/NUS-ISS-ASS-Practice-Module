// Thin fetch wrapper shared by every service module. Takes the auth token
// as an explicit argument rather than reading global state, so it stays
// easy to test and doesn't depend on React context.

export function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export async function apiRequest(base, path, { method = 'GET', body = null, query = null, headers = {}, auth = null } = {}) {
  let url = base + path;
  if (query) {
    const qs = Object.entries(query)
      .filter(([, v]) => v !== undefined && v !== null && v !== '')
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
      .join('&');
    if (qs) url += '?' + qs;
  }

  const opts = { method, headers: { ...headers } };
  if (body !== null) {
    opts.headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(body);
  }
  if (auth?.token) {
    opts.headers['Authorization'] = `${auth.tokenType || 'Bearer'} ${auth.token}`;
  }

  let res;
  try {
    res = await fetch(url, opts);
  } catch (e) {
    throw new Error(`Could not reach ${base} — is the service running and reachable? (${e.message})`);
  }

  const text = await res.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }

  if (!res.ok) {
    const message =
      (data && (data.message || data.error || data.detail)) ||
      (typeof data === 'string' ? data : null) ||
      `Request failed (${res.status})`;
    throw new Error(message);
  }
  return data;
}
