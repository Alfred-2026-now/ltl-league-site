import {
  createRewardRule,
  deleteRewardRule,
  listRewardRules,
  listRuleParameterHistory,
  listRuleParameters,
  updateRewardRule,
  updateRuleParameter
} from "./api.js";

const els = {};
let rewardRules = [];
let ruleParameters = [];

const groupOrder = ["luxury_tax", "loan_fee", "player_transfer", "salary"];

function bindEls() {
  els.ruleFormat = document.getElementById("ruleFormat");
  els.ruleWinner = document.getElementById("ruleWinner");
  els.ruleLoser = document.getElementById("ruleLoser");
  els.ruleDraw = document.getElementById("ruleDraw");
  els.ruleScore = document.getElementById("ruleScore");
  els.addRuleBtn = document.getElementById("addRuleBtn");
  els.rulesBody = document.getElementById("rulesBody");
  els.rewardSummary = document.getElementById("rewardSummary");
  els.parameterGroups = document.getElementById("parameterGroups");
  els.historyList = document.getElementById("historyList");
  els.refreshHistoryBtn = document.getElementById("refreshHistoryBtn");
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function valueOrDash(value, suffix = "P") {
  return value === null || value === undefined || value === "" ? "-" : `${value}${suffix}`;
}

function renderRewardRows() {
  els.rewardSummary.textContent = `${rewardRules.length} 条规则`;
  if (!rewardRules.length) {
    els.rulesBody.innerHTML = `<tr><td colspan="7" class="empty-cell">暂无规则。请按赛制和比分添加奖励金额，否则赛果发布会因缺少规则被阻止。</td></tr>`;
    return;
  }
  els.rulesBody.innerHTML = rewardRules.map(rule => `
    <tr data-rule-id="${rule.id}">
      <td>${escapeHtml(rule.format)}</td>
      <td>${escapeHtml(rule.scorePattern)}</td>
      <td class="rule-winner"><span class="display">${valueOrDash(rule.winnerAmount)}</span><input class="input edit-input" type="number" value="${rule.winnerAmount ?? ""}" hidden /></td>
      <td class="rule-loser"><span class="display">${valueOrDash(rule.loserAmount)}</span><input class="input edit-input" type="number" value="${rule.loserAmount ?? ""}" hidden /></td>
      <td class="rule-draw"><span class="display">${valueOrDash(rule.drawAmount)}</span><input class="input edit-input" type="number" value="${rule.drawAmount ?? ""}" hidden /></td>
      <td><span class="status-badge" data-tone="${rule.isActive ? "success" : "warning"}">${rule.isActive ? "启用" : "停用"}</span></td>
      <td class="action-cell">
        <button class="btn ghost edit-rule-btn" type="button">编辑</button>
        <button class="btn ghost save-rule-btn" type="button" hidden>保存</button>
        <button class="btn ghost delete-rule-btn" type="button">删除</button>
      </td>
    </tr>
  `).join("");
}

function renderParameterGroups() {
  const grouped = new Map();
  ruleParameters.forEach(param => {
    if (!grouped.has(param.groupKey)) grouped.set(param.groupKey, []);
    grouped.get(param.groupKey).push(param);
  });

  els.parameterGroups.innerHTML = groupOrder
    .filter(groupKey => grouped.has(groupKey))
    .map(groupKey => {
      const params = grouped.get(groupKey);
      const groupName = params[0]?.groupName || groupKey;
      const latest = params
        .map(item => item.updatedAt)
        .filter(Boolean)
        .sort()
        .at(-1);
      return `
        <details class="rule-param-group" data-group="${escapeHtml(groupKey)}">
          <summary>
            <span>${escapeHtml(groupName)}</span>
            <small>${params.length} 个参数${latest ? ` · ${formatTime(latest)}` : ""}</small>
          </summary>
          <div class="rule-param-content">
            <div class="rule-table-wrap">
              <table class="admin-table">
                <thead><tr>
                  <th>参数名称</th>
                  <th>当前值</th>
                  <th>单位</th>
                  <th>说明</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr></thead>
                <tbody>${params.map(renderParameterRow).join("")}</tbody>
              </table>
            </div>
          </div>
        </details>
      `;
    }).join("");
}

function renderParameterRow(param) {
  return `
    <tr data-param-key="${escapeHtml(param.paramKey)}">
      <td>
        <strong>${escapeHtml(param.name)}</strong>
        <small class="param-key">${escapeHtml(param.paramKey)}</small>
      </td>
      <td><input class="input param-value" type="number" step="${param.valueType === "int" ? "1" : "0.01"}" value="${escapeHtml(param.valueText)}" /></td>
      <td>${escapeHtml(param.unit || "-")}</td>
      <td class="param-desc">${escapeHtml(param.description)}</td>
      <td>
        <label class="switch-inline">
          <input class="param-active" type="checkbox" ${param.isActive ? "checked" : ""} />
          <span>${param.isActive ? "启用" : "停用"}</span>
        </label>
      </td>
      <td><button class="btn ghost save-param-btn" type="button">保存</button></td>
    </tr>
  `;
}

function formatTime(value) {
  if (!value) return "-";
  return String(value).replace("T", " ").slice(0, 16);
}

async function refreshRewards() {
  els.rulesBody.innerHTML = `<tr><td colspan="7" class="empty-cell">加载中...</td></tr>`;
  rewardRules = await listRewardRules();
  renderRewardRows();
}

async function refreshParameters() {
  els.parameterGroups.innerHTML = `<div class="panel loading-panel">规则参数加载中...</div>`;
  ruleParameters = await listRuleParameters();
  renderParameterGroups();
}

async function refreshHistory() {
  els.historyList.innerHTML = `<p class="muted">加载中...</p>`;
  const rows = await listRuleParameterHistory({ limit: 80 });
  if (!rows.length) {
    els.historyList.innerHTML = `<p class="muted">暂无变更记录。</p>`;
    return;
  }
  els.historyList.innerHTML = rows.map(row => `
    <article class="history-item">
      <div class="history-meta">
        <strong>${escapeHtml(row.paramName)}</strong>
        <span>${formatTime(row.createdAt)}</span>
      </div>
      <p>${escapeHtml(row.oldValue || "-")} → ${escapeHtml(row.newValue || "-")}</p>
      <small>${escapeHtml(row.groupName)} · 操作人：${escapeHtml(row.operator || "admin")}</small>
      <small>原因：${escapeHtml(row.reason || "-")}</small>
    </article>
  `).join("");
}

async function addRule() {
  const format = els.ruleFormat.value;
  const scorePattern = els.ruleScore.value.trim();
  if (!scorePattern) {
    alert("请填写比分模式，例如 2:1");
    return;
  }
  const changeReason = prompt("请填写修改原因");
  if (!changeReason) return;
  await createRewardRule({
    format,
    scorePattern,
    winnerAmount: numberOrNull(els.ruleWinner.value),
    loserAmount: numberOrNull(els.ruleLoser.value),
    drawAmount: numberOrNull(els.ruleDraw.value),
    isActive: 1,
    changeReason
  });
  els.ruleScore.value = "";
  els.ruleWinner.value = "";
  els.ruleLoser.value = "";
  els.ruleDraw.value = "";
  await refreshRewards();
  await refreshHistory();
}

function numberOrNull(value) {
  return value !== "" && value !== null && value !== undefined ? Number(value) : null;
}

function wireRewardEvents() {
  els.addRuleBtn.addEventListener("click", async () => {
    try {
      await addRule();
    } catch (e) {
      alert(`添加失败：${e.message}`);
    }
  });

  els.rulesBody.addEventListener("click", async e => {
    const row = e.target.closest("tr[data-rule-id]");
    if (!row) return;
    const id = Number(row.dataset.ruleId);
    if (e.target.closest(".edit-rule-btn")) {
      row.querySelectorAll(".edit-input").forEach(input => input.hidden = false);
      row.querySelectorAll(".display").forEach(item => item.hidden = true);
      row.querySelector(".edit-rule-btn").hidden = true;
      row.querySelector(".save-rule-btn").hidden = false;
      return;
    }
    if (e.target.closest(".save-rule-btn")) {
      const changeReason = prompt("请填写修改原因");
      if (!changeReason) return;
      try {
        await updateRewardRule(id, {
          winnerAmount: numberOrNull(row.querySelector(".rule-winner .edit-input").value),
          loserAmount: numberOrNull(row.querySelector(".rule-loser .edit-input").value),
          drawAmount: numberOrNull(row.querySelector(".rule-draw .edit-input").value),
          changeReason
        });
        await refreshRewards();
        await refreshHistory();
      } catch (err) {
        alert(`保存失败：${err.message}`);
      }
      return;
    }
    if (e.target.closest(".delete-rule-btn")) {
      const reason = prompt("请填写删除原因");
      if (!reason) return;
      if (!confirm("确认删除该奖励规则？")) return;
      try {
        await deleteRewardRule(id, reason);
        await refreshRewards();
        await refreshHistory();
      } catch (err) {
        alert(`删除失败：${err.message}`);
      }
    }
  });
}

function wireParameterEvents() {
  els.parameterGroups.addEventListener("click", async e => {
    const button = e.target.closest(".save-param-btn");
    if (!button) return;
    const row = button.closest("tr[data-param-key]");
    const key = row.dataset.paramKey;
    const valueText = row.querySelector(".param-value").value.trim();
    const isActive = row.querySelector(".param-active").checked ? 1 : 0;
    const reason = prompt("请填写修改原因");
    if (!reason) return;
    try {
      await updateRuleParameter(key, { valueText, isActive, reason });
      await refreshParameters();
      await refreshHistory();
    } catch (err) {
      alert(`保存失败：${err.message}`);
    }
  });

  els.parameterGroups.addEventListener("change", e => {
    const checkbox = e.target.closest(".param-active");
    if (!checkbox) return;
    const label = checkbox.closest(".switch-inline")?.querySelector("span");
    if (label) label.textContent = checkbox.checked ? "启用" : "停用";
  });
}

async function init() {
  bindEls();
  wireRewardEvents();
  wireParameterEvents();
  els.refreshHistoryBtn.addEventListener("click", () => refreshHistory().catch(e => alert(`刷新失败：${e.message}`)));
  try {
    await Promise.all([refreshRewards(), refreshParameters(), refreshHistory()]);
  } catch (e) {
    alert(`加载失败：${e.message}`);
  }
}

document.addEventListener("DOMContentLoaded", init);
