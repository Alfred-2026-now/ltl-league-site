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

async function getPlayers() {
  return request("/players");
}

async function getTeams() {
  return request("/teams");
}

async function listPlayerDepositLedgers(params) {
  const query = new URLSearchParams();
  Object.entries(params || {}).forEach(([k, v]) => {
    if (v === null || v === undefined || String(v).trim() === "") return;
    query.set(k, String(v).trim());
  });
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return request(`/admin/players/deposit-ledgers${suffix}`);
}

async function voidPlayerDepositLedger(ledgerId, reason) {
  return request(`/admin/players/deposit-ledgers/${ledgerId}/void?reason=${encodeURIComponent(reason || "")}`, {
    method: "POST"
  });
}

async function adjustPlayerDeposit(payload) {
  return request("/admin/players/deposit", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

async function paySalary(rate) {
  return request("/admin/players/salary", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ rate })
  });
}

async function voidSalary(batchId) {
  return request(`/admin/players/salary/${batchId}/void?reason=${encodeURIComponent("管理员撤回")}`, {
    method: "POST"
  });
}

let players = [];
let teams = [];
let ledgers = [];
const els = {};

function bindEls() {
  els.filterPlayer = document.getElementById("filterPlayer");
  els.filterTeam = document.getElementById("filterTeam");
  els.filterType = document.getElementById("filterType");
  els.filterVoided = document.getElementById("filterVoided");
  els.refreshBtn = document.getElementById("refreshBtn");
  els.ledgerBody = document.getElementById("ledgerBody");

  // 调整积分相关元素
  els.adjustPlayer = document.getElementById("adjustPlayer");
  els.adjustAmount = document.getElementById("adjustAmount");
  els.adjustReason = document.getElementById("adjustReason");
  els.adjustBtn = document.getElementById("adjustBtn");
  els.currentDepositHint = document.getElementById("currentDepositHint");

  // 发工资相关元素
  els.salaryRate = document.getElementById("salaryRate");
  els.salaryPreview = document.getElementById("salaryPreview");
  els.salaryBtn = document.getElementById("salaryBtn");
  els.voidSalaryBtn = document.getElementById("voidSalaryBtn");
}

function renderPlayerOptions() {
  const playerOpts = `<option value="">全部</option>${players.map(p => `<option value="${p.id}">${p.name} · 积分 ${p.deposit || 0}P</option>`).join("")}`;
  els.filterPlayer.innerHTML = playerOpts;

  const adjustPlayerOpts = `<option value="">选择选手</option>${players.map(p => `<option value="${p.id}">${p.name} · 积分 ${p.deposit || 0}P</option>`).join("")}`;
  els.adjustPlayer.innerHTML = adjustPlayerOpts;
}

function renderTeamOptions() {
  const teamOpts = `<option value="">全部</option>${teams.map(t => `<option value="${t.id}">${t.state} · ${t.name}</option>`).join("")}`;
  els.filterTeam.innerHTML = teamOpts;
}

function collectFilters() {
  const playerId = els.filterPlayer.value;
  const teamId = els.filterTeam.value;
  let type = els.filterType.value;
  let isVoided = els.filterVoided.value;

  // 如果选择了队伍，则过滤出该队伍的选手
  if (teamId && !playerId) {
    return {
      playerId: null,
      isVoided: isVoided || null,
      limit: 200
    };
  }

  return {
    playerId: playerId || null,
    isVoided: isVoided || null,
    limit: 200
  };
}

function formatAmount(value) {
  const n = Number(value || 0);
  return `<span style="color:${n >= 0 ? "#7CFFB2" : "#ff9f9f"};">${n > 0 ? "+" : ""}${n}P</span>`;
}

function filterLedgers() {
  let filtered = ledgers;

  // 按队伍过滤
  if (els.filterTeam.value) {
    const teamId = Number(els.filterTeam.value);
    filtered = filtered.filter(l => l.teamId === teamId);
  }

  // 按类型过滤
  if (els.filterType.value) {
    filtered = filtered.filter(l => l.type === els.filterType.value);
  }

  return filtered;
}

function renderRows(rows) {
  if (!rows.length) {
    els.ledgerBody.innerHTML = `<tr><td colspan="9" style="padding:1rem;" class="muted">暂无流水。</td></tr>`;
    return;
  }

  els.ledgerBody.innerHTML = rows.map(row => `
    <tr>
      <td style="padding:.75rem 1rem;">${row.createdAt || "-"}</td>
      <td style="padding:.75rem 1rem;">${row.playerName || "-"}</td>
      <td style="padding:.75rem 1rem;">${row.teamState || "-"}</td>
      <td style="padding:.75rem 1rem;">${getTypeText(row.type)}</td>
      <td style="padding:.75rem 1rem;">${formatAmount(row.amount)}</td>
      <td style="padding:.75rem 1rem;">${row.balanceBefore ?? "-"} → ${row.balanceAfter ?? "-"}</td>
      <td style="padding:.75rem 1rem;"><span class="status-badge" data-tone="${row.isVoided ? "danger" : "success"}">${row.isVoided ? "已作废" : "有效"}</span></td>
      <td style="padding:.75rem 1rem;">${row.reason || "-"}</td>
      <td style="padding:.75rem 1rem;">${!row.isVoided ? `<button class="btn" style="padding:.25rem .5rem;font-size:.875rem;" data-id="${row.id}" data-action="void">撤回</button>` : "-"}</td>
    </tr>
  `).join("");
}

function updateCurrentDepositHint() {
  const player = players.find(p => String(p.id) === String(els.adjustPlayer.value));
  els.currentDepositHint.textContent = player ? `当前积分：${player.deposit || 0}P${player.status === 3 ? '（自由人）' : ''}` : "请选择选手。";
}

function getTypeText(type) {
  const typeMap = {
    loan_fee: "租借费",
    manual_adjustment: "手动调整",
    race_reward: "比赛奖励",
    transfer_out: "转赠扣款",
    transfer_in: "转赠收款"
  };
  return typeMap[type] || type;
}

async function refresh() {
  try {
    els.ledgerBody.innerHTML = `<tr><td colspan="9" style="padding:1rem;" class="muted">加载中…</td></tr>`;
    ledgers = await listPlayerDepositLedgers(collectFilters());
    renderRows(filterLedgers());
  } catch (e) {
    els.ledgerBody.innerHTML = `<tr><td colspan="9" style="padding:1rem;color:#ff9f9f;">加载失败：${e.message}</td></tr>`;
  }
}

async function handleVoidClick(e) {
  if (!e.target.matches("[data-action=\"void\"]")) return;
  const ledgerId = e.target.dataset.id;
  if (!confirm("确定要撤回这条流水吗？撤回后该选手后续的所有流水也将被撤回。")) return;
  try {
    await voidPlayerDepositLedger(ledgerId, "管理员撤回");
    alert("流水已撤回");
    await refresh();
  } catch (err) {
    alert(`撤回失败：${err.message}`);
  }
}

async function submitAdjust() {
  try {
    if (!els.adjustPlayer.value) {
      alert("请选择选手");
      return;
    }
    if (!els.adjustAmount.value || Number(els.adjustAmount.value) === 0) {
      alert("请填写有效的调整金额");
      return;
    }
    if (!els.adjustReason.value) {
      alert("请填写原因");
      return;
    }
    await adjustPlayerDeposit({
      playerId: Number(els.adjustPlayer.value),
      amount: Number(els.adjustAmount.value),
      reason: els.adjustReason.value.trim()
    });
    alert("积分已调整");
    els.adjustAmount.value = "";
    els.adjustReason.value = "";
    await refresh();
  } catch (e) {
    alert(`调整失败：${e.message}`);
  }
}

async function init() {
  bindEls();
  try {
    teams = await getTeams();
    players = await getPlayers();
    renderPlayerOptions();
    renderTeamOptions();
    updateCurrentDepositHint();

    els.refreshBtn.addEventListener("click", refresh);
    els.ledgerBody.addEventListener("click", handleVoidClick);
    els.adjustBtn.addEventListener("click", submitAdjust);
    els.adjustPlayer.addEventListener("change", updateCurrentDepositHint);
    els.salaryBtn.addEventListener("click", submitSalary);
    els.voidSalaryBtn.addEventListener("click", submitVoidSalary);
    els.salaryRate.addEventListener("input", updateSalaryPreview);
    els.filterPlayer.addEventListener("change", refresh);
    els.filterTeam.addEventListener("change", () => {
      // 队伍变更时重置选手选择
      els.filterPlayer.value = "";
      renderRows(filterLedgers());
    });
    els.filterType.addEventListener("change", () => renderRows(filterLedgers()));
    els.filterVoided.addEventListener("change", refresh);

    await refresh();
  } catch (e) {
    els.ledgerBody.innerHTML = `<tr><td colspan="9" style="padding:1rem;color:#ff9f9f;">初始化失败：${e.message}</td></tr>`;
  }
}

function updateSalaryPreview() {
  const rate = Number(els.salaryRate.value);
  if (!rate || rate < 1 || rate > 100) {
    els.salaryPreview.value = "";
    return;
  }

  // 计算将影响多少在职且在队伍中的选手
  const affectedPlayers = players.filter(p => p.status === 1 && p.teamId != null);
  els.salaryPreview.value = `将影响 ${affectedPlayers.length} 名选手`;
}

async function submitSalary() {
  try {
    const rate = Number(els.salaryRate.value);
    if (!rate || rate < 1 || rate > 100) {
      alert("请填写有效的工资比例（1-100）");
      return;
    }

    const affectedPlayers = players.filter(p => p.status === 1 && p.teamId != null);
    if (affectedPlayers.length === 0) {
      alert("没有在职的选手可以发放工资");
      return;
    }

    const totalSalary = affectedPlayers.reduce((sum, p) => sum + Math.floor(p.value * rate / 100), 0);
    const confirmMsg = `确认为 ${affectedPlayers.length} 名在职选手发放工资？\n工资比例：${rate}%\n总金额：${totalSalary}P`;
    if (!confirm(confirmMsg)) {
      return;
    }

    await paySalary(rate);
    alert(`工资发放成功！共发放 ${totalSalary}P`);
    els.salaryRate.value = "10";
    els.salaryPreview.value = "";
    await refresh();
  } catch (e) {
    alert(`发工资失败：${e.message}`);
  }
}

async function submitVoidSalary() {
  try {
    if (!confirm("确定要撤回最近一次的工资发放吗？这将作废该批次的所有工资流水，并恢复选手的积分余额。")) {
      return;
    }
    // 使用 0 作为 batchId，后端会撤回最近一次的工资发放
    await voidSalary(0);
    alert("工资已撤回");
    await refresh();
  } catch (e) {
    alert(`撤回失败：${e.message}`);
  }
}

document.addEventListener("DOMContentLoaded", init);
