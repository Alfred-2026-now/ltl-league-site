const API_BASE_URL = "http://123.57.19.160/api";

async function request(endpoint, options = {}) {
  const url = `${API_BASE_URL}${endpoint}`;
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }
  const data = await response.json();
  if (data.code !== 200) {
    throw new Error(data.message || "请求失败");
  }
  return data.data;
}

export async function getTeams() {
  return request("/teams");
}

export async function listAdminMatches(params) {
  const query = new URLSearchParams();
  Object.entries(params || {}).forEach(([k, v]) => {
    if (v === null || v === undefined) return;
    const s = String(v).trim();
    if (!s) return;
    query.set(k, s);
  });
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return request(`/admin/matches${suffix}`);
}

export async function createAdminMatch(payload) {
  return request("/admin/matches", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function updateAdminMatch(id, payload) {
  return request(`/admin/matches/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function publishSchedule(id) {
  return request(`/admin/matches/${id}/publish-schedule`, { method: "POST" });
}

export async function unpublishSchedule(id) {
  return request(`/admin/matches/${id}/unpublish-schedule`, { method: "POST" });
}

