import { getTeams, listPLedgers } from "./api.js";

let teams = [];
const els = {};

function bindEls() {
  els.filterTeam = document.getElementById("filterTeam");
  els.filterType = document.getElementById("filterType");
  els.filterVoided = document.getElementById("filterVoided");
  els.filterMatchId = document.getElementById("filterMatchId");
  els.filterSource = document.getElementById("filterSource");
  els.refreshBtn = document.getElementById("refreshBtn");
  els.ledgerBody = document.getElementById("ledgerBody");
}

function renderTeamOptions() {
  els.filterTeam.innerHTML = `<option value="">全部</option>${teams.map(t => `<option value="${t.id}">${t.state} · ${t.name}</option>`).join("")}`;
}

function collectFilters() {
  return {
    teamId: els.filterTeam.value,
    type: els.filterType.value,
    isVoided: els.filterVoided.value,
    matchId: els.filterMatchId.value,
    source: els.filterSource.value,
    limit: 200
  };
}

function formatAmount(value) {
  const n = Number(value || 0);
  return `<span style="color:${n >= 0 ? "#7CFFB2" : "#ff9f9f"};">${n > 0 ? "+" : ""}${n}P</span>`;
}

function renderRows(rows) {
  if (!rows.length) {
    els.ledgerBody.innerHTML = `<tr><td colspan="8" style="padding:1rem;" class="muted">暂无流水。</td></tr>`;
    return;
  }
  els.ledgerBody.innerHTML = rows.map(row => `
    <tr>
      <td style="padding:.75rem 1rem;">${row.createdAt || "-"}</td>
      <td style="padding:.75rem 1rem;">${row.teamState || "-"}</td>
      <td style="padding:.75rem 1rem;">${row.type}</td>
      <td style="padding:.75rem 1rem;">${formatAmount(row.amount)}</td>
      <td style="padding:.75rem 1rem;">${row.balanceBefore ?? "-"} → ${row.balanceAfter ?? "-"}</td>
      <td style="padding:.75rem 1rem;">#${row.matchId || "-"} ${row.version || ""}</td>
      <td style="padding:.75rem 1rem;"><span class="status-badge" data-tone="${row.isVoided ? "danger" : "success"}">${row.isVoided ? "已作废" : "有效"}</span></td>
      <td style="padding:.75rem 1rem;">${row.reason || "-"}</td>
    </tr>
  `).join("");
}

async function refresh() {
  try {
    els.ledgerBody.innerHTML = `<tr><td colspan="8" style="padding:1rem;" class="muted">加载中…</td></tr>`;
    renderRows(await listPLedgers(collectFilters()));
  } catch (e) {
    els.ledgerBody.innerHTML = `<tr><td colspan="8" style="padding:1rem;color:#ff9f9f;">加载失败：${e.message}</td></tr>`;
  }
}

async function init() {
  bindEls();
  teams = await getTeams();
  renderTeamOptions();
  els.refreshBtn.addEventListener("click", refresh);
  await refresh();
}

document.addEventListener("DOMContentLoaded", init);
