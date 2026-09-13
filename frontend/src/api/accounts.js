import { apiRequest } from "./client";

export function createAccountsApi(config, auth) {
  const base = config.GATEWAY;
  return {
    register: (payload) =>
      apiRequest(base, "/api/accounts/auth/register", {
        method: "POST",
        body: payload,
      }),
    login: (payload) =>
      apiRequest(base, "/api/accounts/auth/login", {
        method: "POST",
        body: payload,
      }),
    me: () => apiRequest(base, "/api/accounts/users/me", { auth }),
    updateMe: (payload) =>
      apiRequest(base, "/api/accounts/users/me", {
        method: "PUT",
        body: payload,
        auth,
      }),
    listUsers: () => apiRequest(base, "/api/accounts/admin/users", { auth }),
    setStatus: (id, enabled) =>
      apiRequest(base, `/api/accounts/admin/users/${id}/status`, {
        method: "PUT",
        query: { enabled },
        auth,
      }),
    setRole: (id, role) =>
      apiRequest(base, `/api/accounts/admin/users/${id}/role`, {
        method: "PUT",
        query: { role },
        auth,
      }),
    reportSummary: () =>
      apiRequest(base, "/api/accounts/admin/reports/summary", { auth }),
    auditLog: (limit) =>
      apiRequest(base, "/api/accounts/admin/audit-log", {
        query: { limit },
        auth,
      }),
  };
}
