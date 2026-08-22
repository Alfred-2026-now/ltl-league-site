import {
  applyPopulationSubsidy,
  deductAllTeamsSalary,
  getCurrentTeams,
  listPLedgers,
  manualAddPLedger,
  previewPopulationSubsidy,
  voidDeductAllTeamsSalary,
  voidPLedger
} from "./api.js";

let teams = [];
let subsidyPreview = null;
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

  // 人口补贴相关元素
  els.subsidySelectAll = document.getElementById("subsidySelectAll");
  els.subsidyTeamOptions = document.getElementById("subsidyTeamOptions");
  els.subsidyPerPlayerAmount = document.getElementById("subsidyPerPlayerAmount");
  els.subsidyPreviewBtn = document.getElementById("subsidyPreviewBtn");
  els.subsidyApplyBtn = document.getElementById("subsidyApplyBtn");
  els.subsidyPreviewPanel = document.getElementById("subsidyPreviewPanel");
  els.subsidyPreviewSummary = document.getElementById("subsidyPreviewSummary");
  els.subsidyPreviewBody = document.getElementById("subsidyPreviewBody");
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function renderTeamOptions() {
  const teamOpts = `<option value="">全部</option>${teams.map(t => `<option value="${t.id}">${t.state} · ${t.name}</option>`).join("")}`;
  const addTeamOpts = `<option value="">选择队伍</option>${teams.map(t => `<option value="${t.id}">${t.state} · ${t.name}</option>`).join("")}`;
  els.filterTeam.innerHTML = teamOpts;
  els.addTeam.innerHTML = addTeamOpts;
  els.subsidyTeamOptions.innerHTML = teams.length
    ? teams.map(t => `
      <label style="display:flex;align-items:center;gap:.5rem;padding:.5rem .6rem;border:1px solid rgba(255,255,255,.1);border-radius:6px;">
        <input type="checkbox" data-subsidy-team-id="${t.id}" />
        <span>${escapeHtml(t.state)} · ${escapeHtml(t.name)}</span>
      </label>
    `).join("")
    : '<span class="muted">当前赛季暂无可选队伍。</span>';
  syncSubsidySelectAll();
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

function getTypeText(type) {
  const typeMap = {
    match_reward: "比赛奖励",
    luxury_tax: "奢侈税",
    loan_fee: "租借费",
    player_donation: "选手赠与",
    player_sign_loss: "买入损耗",
    player_release_loss: "解约损耗",
    salary_deduct: "工资扣除",
    population_subsidy: "人口补贴",
    manual_admin: "管理员调整"
  };
  return typeMap[type] || type;
}

function getSubsidyTeamCheckboxes() {
  return Array.from(els.subsidyTeamOptions.querySelectorAll("[data-subsidy-team-id]"));
}

function getSelectedSubsidyTeamIds() {
  return getSubsidyTeamCheckboxes()
    .filter(input => input.checked)
    .map(input => Number(input.dataset.subsidyTeamId));
}

function syncSubsidySelectAll() {
  const inputs = getSubsidyTeamCheckboxes();
  const checkedCount = inputs.filter(input => input.checked).length;
  els.subsidySelectAll.checked = inputs.length > 0 && checkedCount === inputs.length;
  els.subsidySelectAll.indeterminate = checkedCount > 0 && checkedCount < inputs.length;
  els.subsidySelectAll.disabled = inputs.length === 0;
}

function invalidateSubsidyPreview() {
  subsidyPreview = null;
  els.subsidyApplyBtn.disabled = true;
  els.subsidyPreviewPanel.style.display = "none";
  els.subsidyPreviewBody.innerHTML = "";
  els.subsidyPreviewSummary.textContent = "";
}

function getSubsidyInput() {
  const teamIds = getSelectedSubsidyTeamIds();
  const perPlayerAmount = Number(els.subsidyPerPlayerAmount.value);
  if (!teamIds.length) {
    throw new Error("请至少勾选一个目标队伍");
  }
  if (!Number.isInteger(perPlayerAmount) || perPlayerAmount <= 0) {
    throw new Error("每人补贴金额必须为大于 0 的整数");
  }
  return { teamIds, perPlayerAmount };
}

function renderSubsidyPreview(result) {
  subsidyPreview = result;
  els.subsidyPreviewSummary.textContent = `共选择 ${result.selectedTeamCount} 支队伍，`
    + `其中 ${result.affectedTeamCount} 支有补贴收入；涉及 ${result.eligiblePlayerCount} 名非队长在职队员，`
    + `合计发放 ${result.totalAmount}P。`;
  els.subsidyPreviewBody.innerHTML = result.teams.map(item => `
    <tr>
      <td style="padding:.65rem .75rem;">${escapeHtml(item.teamState)} · ${escapeHtml(item.teamName)}</td>
      <td style="padding:.65rem .75rem;text-align:right;">${item.eligiblePlayerCount}</td>
      <td style="padding:.65rem .75rem;text-align:right;">${item.perPlayerAmount}P</td>
      <td style="padding:.65rem .75rem;text-align:right;color:#7CFFB2;">+${item.subsidyAmount}P</td>
      <td style="padding:.65rem .75rem;text-align:right;">${item.balanceBefore}P → ${item.balanceAfter}P</td>
    </tr>
  `).join("");
  els.subsidyPreviewPanel.style.display = "block";
  els.subsidyApplyBtn.disabled = !result.previewToken || result.totalAmount <= 0;
}

async function submitPopulationSubsidyPreview() {
  const originalText = els.subsidyPreviewBtn.textContent;
  try {
    const payload = getSubsidyInput();
    els.subsidyPreviewBtn.disabled = true;
    els.subsidyPreviewBtn.textContent = "预览中…";
    renderSubsidyPreview(await previewPopulationSubsidy(payload));
  } catch (e) {
    invalidateSubsidyPreview();
    alert(`预览失败：${e.message}`);
  } finally {
    els.subsidyPreviewBtn.disabled = false;
    els.subsidyPreviewBtn.textContent = originalText;
  }
}

async function submitPopulationSubsidy() {
  if (!subsidyPreview?.previewToken) {
    alert("请先预览人口补贴");
    return;
  }
  let payload;
  try {
    payload = getSubsidyInput();
  } catch (e) {
    invalidateSubsidyPreview();
    alert(e.message);
    return;
  }
  const message = `确认发放人口补贴？\n目标队伍：${subsidyPreview.selectedTeamCount} 支`
    + `\n非队长在职队员：${subsidyPreview.eligiblePlayerCount} 人`
    + `\n每人补贴：${subsidyPreview.perPlayerAmount}P`
    + `\n合计发放：${subsidyPreview.totalAmount}P`;
  if (!confirm(message)) return;

  const originalText = els.subsidyApplyBtn.textContent;
  try {
    els.subsidyApplyBtn.disabled = true;
    els.subsidyApplyBtn.textContent = "发放中…";
    const result = await applyPopulationSubsidy({
      ...payload,
      previewToken: subsidyPreview.previewToken
    });
    alert(`人口补贴已发放：${result.affectedTeamCount} 支队伍，共 ${result.totalAmount}P。`);
    invalidateSubsidyPreview();
    await refresh();
  } catch (e) {
    invalidateSubsidyPreview();
    alert(`发放失败：${e.message}`);
  } finally {
    els.subsidyApplyBtn.textContent = originalText;
  }
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
      <td style="padding:.75rem 1rem;">${getTypeText(row.type)}</td>
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

    const confirmMsg = `确认为所有队伍扣除工资？\n工资比例：${rate}%\n金额与发给在职队员的工资总额一致（不含队长）`;
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
  teams = await getCurrentTeams();
  renderTeamOptions();
  updateDeductSalaryPreview();
  els.refreshBtn.addEventListener("click", refresh);
  els.addBtn.addEventListener("click", submitManualAdd);
  els.deductSalaryBtn.addEventListener("click", submitDeductSalary);
  els.voidDeductSalaryBtn.addEventListener("click", submitVoidDeductSalary);
  els.deductSalaryRate.addEventListener("input", updateDeductSalaryPreview);
  els.subsidySelectAll.addEventListener("change", () => {
    getSubsidyTeamCheckboxes().forEach(input => {
      input.checked = els.subsidySelectAll.checked;
    });
    syncSubsidySelectAll();
    invalidateSubsidyPreview();
  });
  els.subsidyTeamOptions.addEventListener("change", event => {
    if (!event.target.matches("[data-subsidy-team-id]")) return;
    syncSubsidySelectAll();
    invalidateSubsidyPreview();
  });
  els.subsidyPerPlayerAmount.addEventListener("input", invalidateSubsidyPreview);
  els.subsidyPreviewBtn.addEventListener("click", submitPopulationSubsidyPreview);
  els.subsidyApplyBtn.addEventListener("click", submitPopulationSubsidy);
  els.ledgerBody.addEventListener("click", handleVoidClick);
  await refresh();
}

document.addEventListener("DOMContentLoaded", init);
