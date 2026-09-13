import { apiRequest } from "./client";

export function createNotificationsApi(config, auth) {
  const base = config.GATEWAY;
  return {
    mine: () => apiRequest(base, "/api/notifications/me", { auth }),
    unreadCount: () =>
      apiRequest(base, "/api/notifications/me/unread-count", { auth }),
    markRead: (id) =>
      apiRequest(base, `/api/notifications/${id}/read`, {
        method: "PUT",
        auth,
      }),
  };
}
