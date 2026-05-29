import { createRewardRule, deleteRewardRule, listRewardRules, updateRewardRule } from "./api.js";

const els = {};

function bindEls() {
  els.ruleFormat = document.getElementById("ruleFormat");
  els.ruleWinner = document.getElementById("ruleWinner");
  els.ruleLoser = document.getElementById("ruleLoser");
  els.ruleDraw = document.getElementById("ruleDraw");
  els.ruleScore = document.getElementById("ruleScore");
  els.addRuleBtn = document.getElementById("addRuleBtn");
  els.rulesBody = document.getElementById("rulesBody");
}

function renderRows(rules) {
  if (!rules.length) {
    els.rulesBody.innerHTML = `<tr><td colspan="7" style="padding:1rem;" class="muted">暂无规则。请按赛制和比分添加奖励金额，否则赛果发布会因缺少规则被阻止。</td></tr>`;
    return;
  }
  els.rulesBody.innerHTML = rules.map(rule => `
    <tr data-rule-id="${rule.id}">
      <td style="padding:.75rem 1rem;">${rule.format}</td>
      <td style="padding:.75rem 1rem;">${rule.scorePattern}</td>
      <td style="padding:.75rem 1rem;" class="rule-winner"><span class="display">${rule.winnerAmount ?? "-"}P</span><input class="input edit-input" type="number" value="${rule.winnerAmount ?? ""}" style="display:none;width:80px;" /></td>
      <td style="padding:.75rem 1rem;" class="rule-loser"><span class="display">${rule.loserAmount ?? "-"}P</span><input class="input edit-input" type="number" value="${rule.loserAmount ?? ""}" style="display:none;width:80px;" /></td>
      <td style="padding:.75rem 1rem;" class="rule-draw"><span class="display">${rule.drawAmount ?? "-"}P</span><input class="input edit-input" type="number" value="${rule.drawAmount ?? ""}" style="display:none;width:80px;" /></td>
      <td style="padding:.75rem 1rem;"><span class="status-badge" data-tone="${rule.isActive ? "success" : "warning"}">${rule.isActive ? "启用" : "停用"}</span></td>
      <td style="padding:.75rem 1rem;">
        <button class="btn ghost edit-btn" type="button">编辑</button>
        <button class="btn ghost save-btn" type="button" style="display:none;">保存</button>
        <button class="btn ghost del-btn" type="button">删除</button>
      </td>
    </tr>
  `).join("");
}

async function refresh() {
  try {
    els.rulesBody.innerHTML = `<tr><td colspan="7" style="padding:1rem;" class="muted">加载中…</td></tr>`;
    renderRows(await listRewardRules());
  } catch (e) {
    els.rulesBody.innerHTML = `<tr><td colspan="7" style="padding:1rem;color:#ff9f9f;">加载失败：${e.message}</td></tr>`;
  }
}

async function addRule() {
  const format = els.ruleFormat.value;
  const scorePattern = els.ruleScore.value.trim();
  if (!scorePattern) { alert("请填写比分模式，如 2:1"); return; }
  try {
    await createRewardRule({
      format,
      scorePattern,
      winnerAmount: els.ruleWinner.value !== "" ? Number(els.ruleWinner.value) : null,
      loserAmount: els.ruleLoser.value !== "" ? Number(els.ruleLoser.value) : null,
      drawAmount: els.ruleDraw.value !== "" ? Number(els.ruleDraw.value) : null,
      isActive: 1
    });
    els.ruleScore.value = "";
    els.ruleWinner.value = "";
    els.ruleLoser.value = "";
    els.ruleDraw.value = "";
    await refresh();
  } catch (e) {
    alert(`添加失败：${e.message}`);
  }
}

function wireTableEvents() {
  els.rulesBody.addEventListener("click", async e => {
    const row = e.target.closest("tr");
    if (!row) return;
    const id = Number(row.dataset.ruleId);
    if (e.target.closest(".edit-btn")) {
      row.querySelectorAll(".edit-input").forEach(el => el.style.display = "");
      row.querySelectorAll(".display").forEach(el => el.style.display = "none");
      row.querySelector(".edit-btn").style.display = "none";
      row.querySelector(".save-btn").style.display = "";
    } else if (e.target.closest(".save-btn")) {
      try {
        await updateRewardRule(id, {
          winnerAmount: row.querySelector(".rule-winner .edit-input").value !== "" ? Number(row.querySelector(".rule-winner .edit-input").value) : null,
          loserAmount: row.querySelector(".rule-loser .edit-input").value !== "" ? Number(row.querySelector(".rule-loser .edit-input").value) : null,
          drawAmount: row.querySelector(".rule-draw .edit-input").value !== "" ? Number(row.querySelector(".rule-draw .edit-input").value) : null
        });
        await refresh();
      } catch (e) {
        alert(`保存失败：${e.message}`);
      }
    } else if (e.target.closest(".del-btn")) {
      if (!confirm("确认删除该规则？")) return;
      try {
        await deleteRewardRule(id);
        await refresh();
      } catch (e) {
        alert(`删除失败：${e.message}`);
      }
    }
  });
}

async function init() {
  bindEls();
  els.addRuleBtn.addEventListener("click", addRule);
  wireTableEvents();
  await refresh();
}

document.addEventListener("DOMContentLoaded", init);
