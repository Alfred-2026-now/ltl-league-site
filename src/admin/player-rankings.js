import { getApiBase } from "../config/api.js";

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

async function loadRankings() {
  const body = document.getElementById("rankingBody");
  try {
    const response = await fetch(`${getApiBase()}/admin/players`, { credentials: "include" });
    const result = await response.json();
    if (!response.ok || result.code !== 200) {
      throw new Error(result.message || "加载失败");
    }
    const players = [...(result.data || [])].sort((a, b) =>
      (b.deposit ?? 0) - (a.deposit ?? 0) || (a.id ?? 0) - (b.id ?? 0));
    body.innerHTML = players.length ? players.map((player, index) => `
      <tr>
        <td>${index + 1}</td>
        <td>${escapeHtml(player.name || "-")}</td>
        <td>${Number(player.deposit ?? 0).toLocaleString("zh-CN")} P</td>
      </tr>
    `).join("") : '<tr><td colspan="3" class="muted">暂无选手数据。</td></tr>';
  } catch (error) {
    body.innerHTML = `<tr><td colspan="3" class="muted">${escapeHtml(error.message)}</td></tr>`;
  }
}

document.addEventListener("DOMContentLoaded", loadRankings);
