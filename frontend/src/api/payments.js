import { apiRequest, uuid } from "./client";

export function createPaymentsApi(config, auth) {
  const base = config.GATEWAY;
  return {
    pay: (payload) =>
      apiRequest(base, "/api/payments", {
        method: "POST",
        body: payload,
        headers: { "Idempotency-Key": uuid() },
        auth,
      }),
    forBooking: (bookingId) =>
      apiRequest(base, "/api/payments", { query: { bookingId }, auth }),
    mine: () => apiRequest(base, "/api/payments/me", { auth }),
    refund: (id) =>
      apiRequest(base, `/api/payments/${id}/refund`, { method: "POST", auth }),
    stats: () => apiRequest(base, "/api/payments/internal/stats", { auth }),
    auditLog: (limit) =>
      apiRequest(base, "/api/payments/admin/audit-log", {
        query: { limit },
        auth,
      }),
  };
}
