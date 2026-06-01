import { getTeams, listPLedgers, manualAddPLedger, voidPLedger, deductAllTeamsSalary, voidDeductAllTeamsSalary } from "./api.js";

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

  // 手动添加相关元素
  els.addTeam = document.getElementById("addTeam");
  els.addAmount = document.getElementById("addAmount");
  els.addReason = document.getElementById("addReason");
  els.addBtn = document.getElementById("addBtn");

  // 扣除队伍工资相关元素
  els.deductSalaryRate = document.getElementById("deductSalaryRate");
  els.deductSalaryPreview = document.getElementById("deductSalaryPreview");
  els.deductSalaryBtn = document.getElementById("deductSalaryBtn");
  els.voidDeductSalaryBtn = document.getElementById("voidDeductSalaryBtn");
}

function renderTeamOptions() {
  const teamOpts = `<option value="">全部</option>${teams.map(t => `<option value="${t.id}">${t.state} · ${t.name}</option>`).join("")}`;
  const addTeamOpts = `<option value="">选择队伍</option>${teams.map(t => `<option value="${t.id}">${t.state} · ${t.name}</option>`).join("")}`;
  els.filterTeam.innerHTML = teamOpts;
  els.addTeam.innerHTML = addTeamOpts;
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
    els.ledgerBody.innerHTML = `<tr><td colspan="9" style="padding:1rem;" class="muted">暂无流水。</td></tr>`;
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
      <td style="padding:.75rem 1rem;">${!row.isVoided ? `<button class="btn" style="padding:.25rem .5rem;font-size:.875rem;" data-id="${row.id}" data-action="void">撤回</button>` : "-"}</td>
    </tr>
  `).join("");
}

async function refresh() {
  try {
    els.ledgerBody.innerHTML = `<tr><td colspan="9" style="padding:1rem;" class="muted">加载中…</td></tr>`;
    renderRows(await listPLedgers(collectFilters()));
  } catch (e) {
    els.ledgerBody.innerHTML = `<tr><td colspan="9" style="padding:1rem;color:#ff9f9f;">加载失败：${e.message}</td></tr>`;
  }
}

async function submitManualAdd() {
  try {
    if (!els.addTeam.value) {
      alert("请选择队伍");
      return;
    }
    if (!els.addAmount.value || Number(els.addAmount.value) === 0) {
      alert("请填写有效的金额");
      return;
    }
    if (!els.addReason.value) {
      alert("请填写原因");
      return;
    }
    await manualAddPLedger({
      teamId: Number(els.addTeam.value),
      amount: Number(els.addAmount.value),
      reason: els.addReason.value.trim()
    });
    alert("P币流水已添加");
    els.addAmount.value = "";
    els.addReason.value = "";
    await refresh();
  } catch (e) {
    alert(`添加失败：${e.message}`);
  }
}

async function handleVoidClick(e) {
  if (!e.target.matches("[data-action=\"void\"]")) return;
  const ledgerId = e.target.dataset.id;
  if (!confirm("确定要撤回这条流水吗？")) return;
  try {
    await voidPLedger(ledgerId, "管理员撤回");
    alert("流水已撤回");
    await refresh();
  } catch (err) {
    alert(`撤回失败：${err.message}`);
  }
}

function updateDeductSalaryPreview() {
  const rate = Number(els.deductSalaryRate.value);
  if (!rate || rate < 1 || rate > 100) {
    els.deductSalaryPreview.value = "";
    return;
  }

  // 计算将影响多少有选手的队伍
  const teamsWithPlayers = teams.filter(t => {
    // 这里简化处理，假设所有队伍都有选手
    return true;
  });
  els.deductSalaryPreview.value = `将影响 ${teamsWithPlayers.length} 个队伍`;
}

async function submitDeductSalary() {
  try {
    const rate = Number(els.deductSalaryRate.value);
    if (!rate || rate < 1 || rate > 100) {
      alert("请填写有效的工资比例（1-100）");
      return;
    }

    const confirmMsg = `确认为所有队伍扣除工资？\n工资比例：${rate}%\n金额 = 队伍选手总身价 × ${rate}%`;
    if (!confirm(confirmMsg)) {
      return;
    }

    await deductAllTeamsSalary(rate);
    alert("工资扣除成功！");
    els.deductSalaryRate.value = "10";
    els.deductSalaryPreview.value = "";
    await refresh();
  } catch (e) {
    alert(`扣除工资失败：${e.message}`);
  }
}

async function submitVoidDeductSalary() {
  try {
    if (!confirm("确定要撤回最近一次的工资扣除吗？这将作废该批次的所有工资扣除流水，并恢复队伍的P币余额。")) {
      return;
    }
    await voidDeductAllTeamsSalary();
    alert("工资扣除已撤回");
    await refresh();
  } catch (e) {
    alert(`撤回失败：${e.message}`);
  }
}

async function init() {
  bindEls();
  teams = await getTeams();
  renderTeamOptions();
  updateDeductSalaryPreview();
  els.refreshBtn.addEventListener("click", refresh);
  els.addBtn.addEventListener("click", submitManualAdd);
  els.deductSalaryBtn.addEventListener("click", submitDeductSalary);
  els.voidDeductSalaryBtn.addEventListener("click", submitVoidDeductSalary);
  els.deductSalaryRate.addEventListener("input", updateDeductSalaryPreview);
  els.ledgerBody.addEventListener("click", handleVoidClick);
  await refresh();
}

document.addEventListener("DOMContentLoaded", init);
