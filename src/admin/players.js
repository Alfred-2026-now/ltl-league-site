import { getApiBase } from "../config/api.js";

const API_BASE_URL = getApiBase();

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

async function getPlayers() {
  return request("/players");
}

async function getTeams() {
  return request("/teams");
}

async function createPlayer(payload) {
  return request("/admin/players", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

async function updatePlayer(playerId, payload) {
  return request(`/admin/players/${playerId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

async function deletePlayer(playerId) {
  return request(`/admin/players/${playerId}`, {
    method: "DELETE"
  });
}

let players = [];
let teams = [];
let currentEditingPlayer = null;
const els = {};

function bindEls() {
  els.createPlayerBtn = document.getElementById("createPlayerBtn");
  els.playerDialog = document.getElementById("playerDialog");
  els.closeDialogBtn = document.getElementById("closeDialogBtn");
  els.dialogTitle = document.getElementById("dialogTitle");
  els.deletePlayerBtn = document.getElementById("deletePlayerBtn");
  els.saveBtn = document.getElementById("saveBtn");

  els.formName = document.getElementById("formName");
  els.formValue = document.getElementById("formValue");
  els.formPosition = document.getElementById("formPosition");
  els.formGameAccount = document.getElementById("formGameAccount");
  els.formPuuid = document.getElementById("formPuuid");
  els.formTeam = document.getElementById("formTeam");
  els.formStatus = document.getElementById("formStatus");
  els.formIsSubstitute = document.getElementById("formIsSubstitute");
  els.formPlayerId = document.getElementById("formPlayerId");

  els.filterTeam = document.getElementById("filterTeam");
  els.filterStatus = document.getElementById("filterStatus");
  els.refreshBtn = document.getElementById("refreshBtn");
  els.playersBody = document.getElementById("playersBody");
}

function renderTeamOptions() {
  return `<option value="">自由人</option>${teams.map(t => `<option value="${t.id}">${t.state} · ${t.name}</option>`).join("")}`;
}

function renderFilterTeamOptions() {
  return `<option value="">全部</option>${teams.map(t => `<option value="${t.id}">${t.state} · ${t.name}</option>`).join("")}`;
}

function getStatusText(status) {
  const statusMap = {
    1: "在职",
    2: "离队",
    3: "自由人"
  };
  return statusMap[status] || "未知";
}

function getStatusStyle(status) {
  if (status === 3) return "background:linear-gradient(135deg, #ff6b6b, #ee5a5a);color:white;padding:2px 8px;border-radius:4px;font-size:0.75rem;";
  if (status === 2) return "background:rgba(121, 231, 255, 0.2);color:#79e7ff;padding:2px 8px;border-radius:4px;font-size:0.75rem;";
  return "background:rgba(124, 255, 178, 0.2);color:#7cffb2;padding:2px 8px;border-radius:4px;font-size:0.75rem;";
}

function renderRows() {
  if (!players.length) {
    els.playersBody.innerHTML = `<tr><td colspan="6" style="padding:1rem;" class="muted">暂无选手。</td></tr>`;
    return;
  }

  let filteredPlayers = players;
  if (els.filterTeam.value) {
    filteredPlayers = filteredPlayers.filter(p => String(p.teamId) === String(els.filterTeam.value));
  }
  if (els.filterStatus.value) {
    filteredPlayers = filteredPlayers.filter(p => String(p.status) === String(els.filterStatus.value));
  }

  const teamMap = new Map(teams.map(t => [t.id, t]));

  els.playersBody.innerHTML = filteredPlayers.map(player => {
    const team = teamMap.get(player.teamId);
    const substituteText = player.isSubstitute ? ' <span style="background:rgba(121, 231, 255, 0.2);color:#7cffb2;padding:2px 6px;border-radius:4px;font-size:0.75rem;margin-left:4px;">替补</span>' : '';
    return `
      <tr>
        <td style="padding:.75rem 1rem;">${player.name || "-"}${substituteText}</td>
        <td style="padding:.75rem 1rem;">${team ? `${team.state} · ${team.name}` : "-"}</td>
        <td style="padding:.75rem 1rem;">${player.value || 0}P</td>
        <td style="padding:.75rem 1rem;">${player.deposit || 0}P</td>
        <td style="padding:.75rem 1rem;"><span style="${getStatusStyle(player.status)}">${getStatusText(player.status)}</span></td>
        <td style="padding:.75rem 1rem;"><button class="btn" style="padding:.25rem .5rem;font-size:.875rem;" data-id="${player.id}" data-action="edit">编辑</button></td>
      </tr>
    `;
  }).join("");
}

async function refresh() {
  try {
    els.playersBody.innerHTML = `<tr><td colspan="6" style="padding:1rem;" class="muted">加载中…</td></tr>`;
    players = await getPlayers();
    renderRows();
  } catch (e) {
    els.playersBody.innerHTML = `<tr><td colspan="6" style="padding:1rem;color:#ff9f9f;">加载失败：${e.message}</td></tr>`;
  }
}

function openCreateDialog() {
  currentEditingPlayer = null;
  els.dialogTitle.textContent = "创建选手";
  els.formName.value = "";
  els.formValue.value = "2000";
  els.formPosition.value = "";
  els.formGameAccount.value = "";
  els.formPuuid.value = "";
  els.formTeam.innerHTML = renderTeamOptions();
  els.formTeam.value = "";
  els.formStatus.value = "1";
  els.formIsSubstitute.checked = false;
  els.formPlayerId.value = "";
  els.formTeam.disabled = false;
  els.deletePlayerBtn.style.display = "none";
  els.playerDialog.showModal();
}

function openEditDialog(player) {
  currentEditingPlayer = player;
  els.dialogTitle.textContent = "编辑选手";
  els.formName.value = player.name || "";
  els.formValue.value = player.value || 2000;
  els.formPosition.value = player.position || "";
  els.formGameAccount.value = player.gameAccount || "";
  els.formPuuid.value = player.puuid || "";
  els.formTeam.innerHTML = renderTeamOptions();
  els.formTeam.value = player.teamId ? String(player.teamId) : "";
  els.formStatus.value = String(player.status || 1);
  els.formIsSubstitute.checked = player.isSubstitute === 1;
  els.formPlayerId.value = player.id;
  els.deletePlayerBtn.style.display = "";
  handleStatusChange();
  els.playerDialog.showModal();
}

async function savePlayer() {
  try {
    const status = Number(els.formStatus.value);
    const teamId = els.formTeam.value ? Number(els.formTeam.value) : null;

    // 验证：自由人不能有队伍
    if (status === 3 && teamId !== null) {
      alert("自由人不能属于任何队伍");
      return;
    }
    // 在职选手必须有队伍
    if (status === 1 && teamId === null) {
      alert("在职选手必须属于某个队伍");
      return;
    }

    const payload = {
      teamId: teamId,
      name: els.formName.value.trim(),
      value: Number(els.formValue.value),
      position: els.formPosition.value || null,
      gameAccount: els.formGameAccount.value || null,
      puuid: els.formPuuid.value || null,
      isSubstitute: els.formIsSubstitute.checked ? 1 : 0,
      status: status
    };

    if (currentEditingPlayer) {
      await updatePlayer(currentEditingPlayer.id, payload);
      alert("选手信息已更新");
    } else {
      await createPlayer(payload);
      alert("选手已创建");
    }

    els.playerDialog.close();
    await refresh();
  } catch (e) {
    alert(`${currentEditingPlayer ? '更新' : '创建'}失败：${e.message}`);
  }
}

async function deleteCurrentPlayer() {
  if (!currentEditingPlayer) return;
  const playerName = currentEditingPlayer.name || `#${currentEditingPlayer.id}`;
  if (!confirm(`确认删除选手「${playerName}」？删除后前台列表将不再显示该选手。`)) {
    return;
  }

  els.deletePlayerBtn.disabled = true;
  try {
    await deletePlayer(currentEditingPlayer.id);
    alert("选手已删除");
    els.playerDialog.close();
    currentEditingPlayer = null;
    await refresh();
  } catch (e) {
    alert(`删除失败：${e.message}`);
  } finally {
    els.deletePlayerBtn.disabled = false;
  }
}

function handleTableClick(e) {
  if (!e.target.matches("[data-action=\"edit\"]")) return;
  const playerId = Number(e.target.dataset.id);
  const player = players.find(p => p.id === playerId);
  if (player) {
    openEditDialog(player);
  }
}

function handleStatusChange() {
  const status = Number(els.formStatus.value);
  if (status === 3) {
    els.formTeam.value = "";
    els.formTeam.disabled = true;
  } else {
    els.formTeam.disabled = false;
  }
}

async function init() {
  bindEls();
  try {
    teams = await getTeams();
    players = await getPlayers();
    els.filterTeam.innerHTML = renderFilterTeamOptions();
    renderRows();

    els.createPlayerBtn.addEventListener("click", openCreateDialog);
    els.closeDialogBtn.addEventListener("click", () => els.playerDialog.close());
    els.deletePlayerBtn.addEventListener("click", deleteCurrentPlayer);
    els.saveBtn.addEventListener("click", savePlayer);
    els.refreshBtn.addEventListener("click", refresh);
    els.playersBody.addEventListener("click", handleTableClick);
    els.filterTeam.addEventListener("change", renderRows);
    els.filterStatus.addEventListener("change", renderRows);
    els.formStatus.addEventListener("change", handleStatusChange);
  } catch (e) {
    els.playersBody.innerHTML = `<tr><td colspan="6" style="padding:1rem;color:#ff9f9f;">初始化失败：${e.message}</td></tr>`;
  }
}

document.addEventListener("DOMContentLoaded", init);
