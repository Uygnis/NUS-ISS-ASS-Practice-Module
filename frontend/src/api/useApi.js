import { useMemo } from "react";
import { useConfig } from "../context/ConfigContext";
import { useAuth } from "../context/AuthContext";
import { createAccountsApi } from "./accounts";
import { createCatalogApi } from "./catalog";
import { createReservationsApi } from "./reservations";
import { createPaymentsApi } from "./payments";
import { createNotificationsApi } from "./notifications";

export function useApi() {
  const { config } = useConfig();
  const { auth } = useAuth();

  return useMemo(
    () => ({
      accounts: createAccountsApi(config, auth),
      catalog: createCatalogApi(config, auth),
      reservations: createReservationsApi(config, auth),
      payments: createPaymentsApi(config, auth),
      notifications: createNotificationsApi(config, auth),
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [config, auth?.token],
  );
}
