import { getApiBase } from "../config/api.js";

const API_BASE_URL = getApiBase();

async function request(endpoint, options = {}) {
  const url = `${API_BASE_URL}${endpoint}`;
  const response = await fetch(url, { credentials: "include", ...options });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }
  const data = await response.json();
  if (data.code !== 200) {
    throw new Error(data.message || "请求失败");
  }
  return data.data;
}

async function getPlayers() {
  return request("/admin/players");
}

async function getTeams() {
  return request("/teams");
}

async function adjustBounty(payload) {
  return request("/admin/players/bounty", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

let players = [];
let teams = [];
const els = {};

function bindEls() {
  els.filterPlayer = document.getElementById("filterPlayer");
  els.filterTeam = document.getElementById("filterTeam");
  els.refreshBtn = document.getElementById("refreshBtn");
  els.bountyBody = document.getElementById("bountyBody");
  els.bountyDialog = document.getElementById("bountyDialog");
  els.dialogTitle = document.getElementById("dialogTitle");
  els.dialogPlayerName = document.getElementById("dialogPlayerName");
  els.closeDialogBtn = document.getElementById("closeDialogBtn");
  els.formAmount = document.getElementById("formAmount");
  els.formReason = document.getElementById("formReason");
  els.saveBtn = document.getElementById("saveBtn");
  els.formPlayerId = document.getElementById("formPlayerId");
}

function teamText(team) {
  return team ? team.name : "自由人";
}

function render() {
  const teamMap = new Map(teams.map(team => [team.id, team]));

  // 填充筛选下拉
  els.filterPlayer.innerHTML = `<option value="">全部选手</option>` +
    [...players].sort((a, b) => (a.name || "").localeCompare(b.name || "", "zh")).map(p =>
      `<option value="${p.id}">${escapeHtml(p.name)}</option>`
    ).join("");

  els.filterTeam.innerHTML = `<option value="">全部</option>` +
    teams.map(t => `<option value="${t.id}">${escapeHtml(t.name)}</option>`).join("");

  const playerFilter = els.filterPlayer.value;
  const teamFilter = els.filterTeam.value;

  const filtered = players.filter(p => {
    if (playerFilter && String(p.id) !== playerFilter) return false;
    if (teamFilter && String(p.teamId) !== teamFilter) return false;
    return true;
  }).sort((a, b) => (b.bounty || 0) - (a.bounty || 0));

  els.bountyBody.innerHTML = filtered.map((player, index) => {
    const team = teamMap.get(player.teamId);
    const rank = index + 1;
    return `
      <tr>
        <td style="text-align:left;padding:.75rem 1rem;">
          <span style="font-weight:bold;color:#79e7ff;">${rankDisplay(rank)}</span>
          ${escapeHtml(player.name || "-")}
        </td>
        <td style="text-align:left;padding:.75rem 1rem;color:#a8b6d6;">${escapeHtml(teamText(team))}</td>
        <td style="text-align:left;padding:.75rem 1rem;color:#ffd700;font-weight:600;">🪙 ${player.bounty || 0}</td>
        <td style="text-align:left;padding:.75rem 1rem;">
          <button class="btn" type="button" data-adjust-bounty="${player.id}" data-bounty-player="${escapeHtml(player.name)}">调整赏金</button>
        </td>
      </tr>
    `;
  }).join("") || `<tr><td colspan="4" style="padding:1rem;" class="muted">暂无选手数据。</td></tr>`;
}

function rankDisplay(rank) {
  if (rank === 1) return "🥇";
  if (rank === 2) return "🥈";
  if (rank === 3) return "🥉";
  return `${rank}.`;
}

function openAdjustDialog(playerId, playerName) {
  els.formPlayerId.value = playerId;
  els.dialogTitle.textContent = "调整赏金";
  els.dialogPlayerName.textContent = `选手：${playerName}`;
  els.formAmount.value = "";
  els.formReason.value = "";
  els.bountyDialog.showModal();
}

function bindEvents() {
  els.refreshBtn.addEventListener("click", init);
  els.filterPlayer.addEventListener("change", render);
  els.filterTeam.addEventListener("change", render);
  els.closeDialogBtn.addEventListener("click", () => els.bountyDialog.close());

  els.bountyBody.addEventListener("click", (event) => {
    const button = event.target.closest("[data-adjust-bounty]");
    if (!button) return;
    openAdjustDialog(button.dataset.adjustBounty, button.dataset.bountyPlayer);
  });

  els.saveBtn.addEventListener("click", async () => {
    const playerId = Number(els.formPlayerId.value);
    const amount = Number(els.formAmount.value);
    if (!playerId) return;
    if (!amount || Number.isNaN(amount) || amount === 0) {
      alert("请输入非零的调整金额");
      return;
    }
    try {
      await adjustBounty({
        playerId,
        amount,
        reason: els.formReason.value.trim() || "手动调整"
      });
      els.bountyDialog.close();
      await loadData();
      render();
    } catch (error) {
      alert(error.message);
    }
  });
}

async function loadData() {
  [players, teams] = await Promise.all([getPlayers(), getTeams()]);
}

async function init() {
  try {
    await loadData();
    render();
  } catch (error) {
    els.bountyBody.innerHTML = `<tr><td colspan="4" style="padding:1rem;color:#ff9f9f;">加载失败：${escapeHtml(error.message)}</td></tr>`;
  }
}

bindEls();
bindEvents();
init();
