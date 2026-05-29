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

export async function getPlayers() {
  return request("/players");
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

export async function getAdminMatch(id) {
  return request(`/admin/matches/${id}`);
}

export async function getMatchResult(matchId) {
  return request(`/admin/matches/${matchId}/result`);
}

export async function createResultDraft(matchId, payload) {
  return request(`/admin/matches/${matchId}/result/draft`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function updateResultDraft(matchId, resultId, payload) {
  return request(`/admin/matches/${matchId}/result/draft/${resultId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function previewResultSettlement(matchId, payload) {
  return request(`/admin/matches/${matchId}/result/settlement-preview`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function publishResult(matchId, resultId) {
  return request(`/admin/matches/${matchId}/result/${resultId}/publish`, { method: "POST" });
}

export async function withdrawResult(matchId, resultId, withdrawReason) {
  return request(`/admin/matches/${matchId}/result/${resultId}/withdraw`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ withdrawReason })
  });
}

export async function uploadGameScreenshot(matchId, resultId, gameIndex, file) {
  const form = new FormData();
  form.append("file", file);
  const url = `${API_BASE_URL}/admin/matches/${matchId}/result/${resultId}/games/${gameIndex}/screenshots`;
  const response = await fetch(url, { method: "POST", body: form });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const data = await response.json();
  if (data.code !== 200) throw new Error(data.message || "上传失败");
  return data.data;
}

export async function deleteAttachment(attachmentId) {
  return request(`/admin/attachments/${attachmentId}`, { method: "DELETE" });
}

export async function listPLedgers(params) {
  const query = new URLSearchParams();
  Object.entries(params || {}).forEach(([k, v]) => {
    if (v === null || v === undefined || String(v).trim() === "") return;
    query.set(k, String(v).trim());
  });
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return request(`/admin/p-ledger${suffix}`);
}

export async function listValuationChanges(params) {
  const query = new URLSearchParams();
  Object.entries(params || {}).forEach(([k, v]) => {
    if (v === null || v === undefined || String(v).trim() === "") return;
    query.set(k, String(v).trim());
  });
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return request(`/admin/valuation-changes${suffix}`);
}

export async function createManualValuationAdjustment(payload) {
  return request("/admin/valuation-changes/manual-adjustment", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function listRewardRules(format) {
  const query = format ? `?format=${encodeURIComponent(format)}` : "";
  return request(`/admin/reward-rules${query}`);
}

export async function createRewardRule(payload) {
  return request("/admin/reward-rules", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function updateRewardRule(id, payload) {
  return request(`/admin/reward-rules/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function deleteRewardRule(id) {
  return request(`/admin/reward-rules/${id}`, { method: "DELETE" });
}

