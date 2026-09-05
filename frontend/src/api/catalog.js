import { apiRequest } from "./client";

export function createCatalogApi(config, auth) {
  const base = config.GATEWAY;
  return {
    search: (location, type) =>
      apiRequest(base, "/api/catalog/cars", {
        query: { location, type },
        auth,
      }),
    find: (id) => apiRequest(base, `/api/catalog/cars/${id}`, { auth }),
    create: (payload) =>
      apiRequest(base, "/api/catalog/cars", {
        method: "POST",
        body: payload,
        auth,
      }),
    update: (id, payload) =>
      apiRequest(base, `/api/catalog/cars/${id}`, {
        method: "PUT",
        body: payload,
        auth,
      }),
    remove: (id) =>
      apiRequest(base, `/api/catalog/cars/${id}`, { method: "DELETE", auth }),
    setStatus: (id, status) =>
      apiRequest(base, `/api/catalog/cars/${id}/status`, {
        method: "PATCH",
        query: { status },
        auth,
      }),
    scheduleMaintenance: (payload) =>
      apiRequest(base, "/api/catalog/maintenance", {
        method: "POST",
        body: payload,
        auth,
      }),
    maintenanceStatus: (id, status) =>
      apiRequest(base, `/api/catalog/maintenance/${id}/status`, {
        method: "PUT",
        query: { status },
        auth,
      }),
    maintenanceFor: (carId) =>
      apiRequest(base, `/api/catalog/maintenance/car/${carId}`, { auth }),
    stats: () => apiRequest(base, "/api/catalog/internal/stats", { auth }),
    auditLog: (limit) =>
      apiRequest(base, "/api/catalog/admin/audit-log", {
        query: { limit },
        auth,
      }),
  };
}

export const CAR_TYPES = [
  "SEDAN",
  "SUV",
  "HATCHBACK",
  "TRUCK",
  "ELECTRIC",
  "LUXURY",
];
