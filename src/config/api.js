const DEFAULT_API_BASE = "/api";
const LOCAL_API_BASE = "http://127.0.0.1:8080/api";
const STORAGE_KEY = "LTL_API_BASE";

function normalizeApiBase(value) {
  return value.replace(/\/+$/, "");
}

export function getApiBase() {
  try {
    const override = window.localStorage?.getItem(STORAGE_KEY)?.trim();
    if (override) {
      return normalizeApiBase(override);
    }
    if (window.location.hostname === "127.0.0.1" || window.location.hostname === "localhost") {
      return LOCAL_API_BASE;
    }
    return DEFAULT_API_BASE;
  } catch (error) {
    return DEFAULT_API_BASE;
  }
}

export { DEFAULT_API_BASE, LOCAL_API_BASE, STORAGE_KEY };
