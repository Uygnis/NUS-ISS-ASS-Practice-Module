import { createContext, useContext, useState, useCallback } from "react";

const ConfigContext = createContext(null);
const STORAGE_KEY = "rentez_config_overrides";

function loadConfig() {
  const base = {
    // ?? not ||: an empty gateway means same-origin and is a deliberate
    // value, but || treats it as unset and would fall back to localhost.
    GATEWAY: window.__RENTEZ_GATEWAY_ENDPOINT__ ?? "",
  };
  let overrides = {};
  try {
    overrides = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
  } catch {
    overrides = {};
  }
  return { ...base, ...overrides };
}
export function ConfigProvider({ children }) {
  const [config, setConfig] = useState(loadConfig);
  const updateConfig = useCallback((patch) => {
    setConfig((prev) => {
      const next = { ...prev, ...patch };
      const base = {
        // ?? not ||: an empty gateway means same-origin and is a deliberate
    // value, but || treats it as unset and would fall back to localhost.
    GATEWAY: window.__RENTEZ_GATEWAY_ENDPOINT__ ?? "",
      };
      const overrides = {};
      Object.keys(next).forEach((key) => {
        if (next[key] !== base[key]) {
          overrides[key] = next[key];
        }
      });
      localStorage.setItem(STORAGE_KEY, JSON.stringify(overrides));
      return next;
    });
  }, []);
  return (
    <ConfigContext.Provider value={{ config, updateConfig }}>
      {" "}
      {children}{" "}
    </ConfigContext.Provider>
  );
}
export function useConfig() {
  const ctx = useContext(ConfigContext);
  if (!ctx) {
    throw new Error("useConfig must be used within ConfigProvider");
  }
  return ctx;
}
