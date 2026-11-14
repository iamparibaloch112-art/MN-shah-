async function request(path, options = {}) {
  const resp = await fetch(path, {
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    ...options
  });
  const data = await resp.json().catch(() => ({}));
  if (!resp.ok) {
    const err = new Error(data.error || `Request failed (${resp.status})`);
    err.status = resp.status;
    throw err;
  }
  return data;
}

export const api = {
  getAuthConfig: () => request('/api/auth/config'),
  getMe: () => request('/api/auth/me'),
  loginWithGoogle: (idToken) =>
    request('/api/auth/google', { method: 'POST', body: JSON.stringify({ idToken }) }),
  loginWithPassword: (email, password) =>
    request('/api/auth/password', { method: 'POST', body: JSON.stringify({ email, password }) }),
  logout: () => request('/api/auth/logout', { method: 'POST' }),
  getMarkets: () => request('/api/markets'),
  getMarket: (assetId) => request(`/api/market/${encodeURIComponent(assetId)}`),
  analyzeNow: (assetId, userCommand) =>
    request('/api/analyze', { method: 'POST', body: JSON.stringify({ assetId, userCommand }) }),
  askAssistant: (query, assetId) =>
    request('/api/assistant', { method: 'POST', body: JSON.stringify({ query, assetId }) }),
  getAiStatus: () => request('/api/ai/status'),
  recheckAi: () => request('/api/ai/recheck', { method: 'POST' }),
  getSignals: () => request('/api/signals'),
  deleteSignal: (id) => request(`/api/signals/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  getTrades: () => request('/api/trades'),
  createTrade: (payload) =>
    request('/api/trades', { method: 'POST', body: JSON.stringify(payload) }),
  cancelTrade: (id) =>
    request(`/api/trades/${encodeURIComponent(id)}/cancel`, { method: 'POST' })
};
