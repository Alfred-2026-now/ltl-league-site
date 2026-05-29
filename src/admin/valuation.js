import { createManualValuationAdjustment, voidValuationChange, getPlayers, getTeams, listValuationChanges } from "./api.js";

let teams = [];
let players = [];
const els = {};

function bindEls() {
  els.adjustPlayer = document.getElementById("adjustPlayer");
  els.adjustAfterValue = document.getElementById("adjustAfterValue");
  els.adjustReason = document.getElementById("adjustReason");
  els.adjustBtn = document.getElementById("adjustBtn");
  els.currentValueHint = document.getElementById("currentValueHint");
  els.filterTeam = document.getElementById("filterTeam");
  els.filterPlayer = document.getElementById("filterPlayer");
  els.filterSource = document.getElementById("filterSource");
  els.filterVoided = document.getElementById("filterVoided");
  els.filterMatchId = document.getElementById("filterMatchId");
  els.refreshBtn = document.getElementById("refreshBtn");
  els.valuationBody = document.getElementById("valuationBody");
}

function playerOptions(selectedId = "") {
  return `<option value="">全部</option>${players.map(p => `<option value="${p.id}" ${String(p.id) === String(selectedId) ? "selected" : ""}>${p.name} · ${p.value ?? 0}P</option>`).join("")}`;
}

function teamOptions() {
  return `<option value="">全部</option>${teams.map(t => `<option value="${t.id}">${t.state} · ${t.name}</option>`).join("")}`;
}

function renderOptions() {
  els.adjustPlayer.innerHTML = playerOptions();
  els.filterPlayer.innerHTML = playerOptions();
  els.filterTeam.innerHTML = teamOptions();
}

function updateCurrentValueHint() {
  const player = players.find(p => String(p.id) === String(els.adjustPlayer.value));
  els.currentValueHint.textContent = player ? `当前身价：${player.value ?? 0}P` : "请选择选手。";
  if (player && !els.adjustAfterValue.value) {
    els.adjustAfterValue.value = player.value ?? 0;
  }
}

function collectFilters() {
  return {
    playerId: els.filterPlayer.value,
    teamId: els.filterTeam.value,
    source: els.filterSource.value,
    isVoided: els.filterVoided.value,
    matchId: els.filterMatchId.value,
    limit: 200
  };
}

function formatDelta(row) {
  const total = Number(row.objectiveDelta || 0) + Number(row.subjectiveDelta || 0);
  return `${row.beforeValue} → ${row.afterValue}（${total > 0 ? "+" : ""}${total}P）`;
}

function renderRows(rows) {
  if (!rows.length) {
    els.valuationBody.innerHTML = `<tr><td colspan="8" style="padding:1rem;" class="muted">暂无身价变化。</td></tr>`;
    return;
  }
  els.valuationBody.innerHTML = rows.map(row => `
    <tr>
      <td style="padding:.75rem 1rem;">${row.createdAt || "-"}</td>
      <td style="padding:.75rem 1rem;">${row.playerName || "-"}${row.teamState ? ` · ${row.teamState}` : ""}</td>
      <td style="padding:.75rem 1rem;">${row.source || "-"}</td>
      <td style="padding:.75rem 1rem;">#${row.matchId || "-"} ${row.version || ""}</td>
      <td style="padding:.75rem 1rem;">${formatDelta(row)}</td>
      <td style="padding:.75rem 1rem;"><span class="status-badge" data-tone="${row.isVoided ? "danger" : "success"}">${row.isVoided ? "已作废" : "有效"}</span></td>
      <td style="padding:.75rem 1rem;">${row.subjectiveReason || "-"}</td>
      <td style="padding:.75rem 1rem;">${!row.isVoided ? `<button class="btn" style="padding:.25rem .5rem;font-size:.875rem;" data-id="${row.id}" data-action="void">撤回</button>` : "-"}</td>
    </tr>
  `).join("");
}

async function refresh() {
  try {
    els.valuationBody.innerHTML = `<tr><td colspan="7" style="padding:1rem;" class="muted">加载中…</td></tr>`;
    renderRows(await listValuationChanges(collectFilters()));
  } catch (e) {
    els.valuationBody.innerHTML = `<tr><td colspan="7" style="padding:1rem;color:#ff9f9f;">加载失败：${e.message}</td></tr>`;
  }
}

async function submitAdjustment() {
  try {
    await createManualValuationAdjustment({
      playerId: els.adjustPlayer.value ? Number(els.adjustPlayer.value) : null,
      afterValue: els.adjustAfterValue.value !== "" ? Number(els.adjustAfterValue.value) : null,
      reason: els.adjustReason.value
    });
    alert("身价已调整");
    els.adjustReason.value = "";
    players = await getPlayers();
    renderOptions();
    await refresh();
  } catch (e) {
    alert(`调整失败：${e.message}`);
  }
}

async function init() {
  bindEls();
  teams = await getTeams();
  players = await getPlayers();
  renderOptions();
  updateCurrentValueHint();
  els.adjustPlayer.addEventListener("change", updateCurrentValueHint);
  els.adjustBtn.addEventListener("click", submitAdjustment);
  els.refreshBtn.addEventListener("click", refresh);
  els.valuationBody.addEventListener("click", handleVoidClick);
  await refresh();
}

async function handleVoidClick(e) {
  if (!e.target.matches("[data-action=\"void\"]")) return;
  const changeId = e.target.dataset.id;
  if (!confirm("确定要撤回这条身价变化吗？")) return;
  try {
    await voidValuationChange(changeId, "管理员撤回");
    alert("身价变化已撤回");
    players = await getPlayers();
    renderOptions();
    await refresh();
  } catch (err) {
    alert(`撤回失败：${err.message}`);
  }
}

document.addEventListener("DOMContentLoaded", init);
