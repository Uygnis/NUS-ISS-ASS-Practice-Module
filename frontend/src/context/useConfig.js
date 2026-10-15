import { createContext, useContext } from "react";

// Kept apart from ConfigProvider so the .jsx file exports only a component.
export const ConfigContext = createContext(null);

export function useConfig() {
  const ctx = useContext(ConfigContext);
  if (!ctx) {
    throw new Error("useConfig must be used within ConfigProvider");
  }
  return ctx;
}
