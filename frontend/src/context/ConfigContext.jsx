import { useState } from "react";
import { ConfigContext } from "./useConfig";

// The gateway URL comes from public/config.js, written at deploy time. The
// in-page override footer and its localStorage copy were removed: users had
// no use for them, and a stale saved override would silently break requests.
function loadConfig() {
  try {
    localStorage.removeItem("rentez_config_overrides");
  } catch {
    /* storage blocked */
  }
  return {
    // ?? not ||: an empty gateway means same-origin and is a deliberate
    // value, but || treats it as unset and would fall back to localhost.
    GATEWAY: window.__RENTEZ_GATEWAY_ENDPOINT__ ?? "",
  };
}

export function ConfigProvider({ children }) {
  const [config] = useState(loadConfig);
  return <ConfigContext.Provider value={{ config }}>{children}</ConfigContext.Provider>;
}
