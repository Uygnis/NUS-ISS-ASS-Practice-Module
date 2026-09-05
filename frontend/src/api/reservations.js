import { apiRequest } from "./client";

export function createReservationsApi(config, auth) {
  const base = config.GATEWAY;
  return {
    availability: (params) =>
      apiRequest(base, "/api/reservations/availability", {
        query: params,
        auth,
      }),
    create: (payload) =>
      apiRequest(base, "/api/reservations/bookings", {
        method: "POST",
        body: payload,
        auth,
      }),
    mine: () => apiRequest(base, "/api/reservations/bookings/me", { auth }),
    find: (id) =>
      apiRequest(base, `/api/reservations/bookings/${id}`, { auth }),
    update: (id, payload) =>
      apiRequest(base, `/api/reservations/bookings/${id}`, {
        method: "PUT",
        body: payload,
        auth,
      }),
    cancel: (id) =>
      apiRequest(base, `/api/reservations/bookings/${id}`, {
        method: "DELETE",
        auth,
      }),
    stats: () => apiRequest(base, "/api/reservations/internal/stats", { auth }),
    auditLog: (limit) =>
      apiRequest(base, "/api/reservations/admin/audit-log", {
        query: { limit },
        auth,
      }),
  };
}
