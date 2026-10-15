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
    console.error(`Could not reach ${base || 'the gateway'}`, e);
    throw new Error("We can't reach RentEz right now. Please check your connection and try again.", { cause: e });
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
    // A 401 on a request that carried a token means the session ran out. The
    // login call itself sends no token, so "Invalid email or password" still
    // reaches the login form unchanged.
    if (res.status === 401 && auth?.token) {
      window.dispatchEvent(new Event('rentez:session-expired'));
      throw new Error('Your session has expired. Please sign in again.');
    }
    const message =
      (data && data.message) ||
      (res.status === 403 ? "You don't have access to this." : null) ||
      (res.status >= 500 ? 'Something went wrong on our side. Please try again in a moment.' : null) ||
      (data && (data.error || data.detail)) ||
      (typeof data === 'string' && data.length < 200 ? data : null) ||
      `Request failed (${res.status})`;
    const err = new Error(message);
    err.status = res.status;
    err.fieldErrors = (data && data.fieldErrors) || {};
    throw err;
  }
  return data;
}
