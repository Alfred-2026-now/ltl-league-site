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

async function listRules() {
  return request("/admin/rules");
}

async function createRule(payload) {
  return request("/admin/rules", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

async function updateRule(ruleId, payload) {
  return request(`/admin/rules/${ruleId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

async function deleteRule(ruleId) {
  return request(`/admin/rules/${ruleId}`, { method: "DELETE" });
}

let rules = [];
let currentEditingRule = null;
const els = {};

function bindEls() {
  els.createRuleBtn = document.getElementById("createRuleBtn");
  els.ruleDialog = document.getElementById("ruleDialog");
  els.closeDialogBtn = document.getElementById("closeDialogBtn");
  els.dialogTitle = document.getElementById("dialogTitle");
  els.saveBtn = document.getElementById("saveBtn");
  els.formTitle = document.getElementById("formTitle");
  els.formDisplayOrder = document.getElementById("formDisplayOrder");
  els.formContent = document.getElementById("formContent");
  els.formIsOpen = document.getElementById("formIsOpen");
  els.formRuleId = document.getElementById("formRuleId");
  els.rulesBody = document.getElementById("rulesBody");
}

function renderRows() {
  if (!rules.length) {
    els.rulesBody.innerHTML = `<tr><td colspan="4" style="padding:1rem;" class="muted">暂无规则。</td></tr>`;
    return;
  }

  els.rulesBody.innerHTML = rules.map(rule => `
    <tr data-rule-id="${rule.id}">
      <td style="padding:.75rem 1rem;">${rule.displayOrder}</td>
      <td style="padding:.75rem 1rem;">${rule.title}</td>
      <td style="padding:.75rem 1rem;">${rule.isOpen ? '<span style="background:rgba(124, 255, 178, 0.2);color:#7cffb2;padding:2px 8px;border-radius:4px;font-size:0.75rem;">展开</span>' : '<span style="background:rgba(121, 231, 255, 0.2);color:#79e7ff;padding:2px 8px;border-radius:4px;font-size:0.75rem;">收起</span>'}</td>
      <td style="padding:.75rem 1rem;">
        <button class="btn ghost" style="padding:.25rem .5rem;font-size:.875rem;" data-id="${rule.id}" data-action="edit">编辑</button>
        <button class="btn ghost" style="padding:.25rem .5rem;font-size:.875rem;color:#ff6b6b;" data-id="${rule.id}" data-action="delete">删除</button>
      </td>
    </tr>
  `).join("");
}

async function refresh() {
  try {
    els.rulesBody.innerHTML = `<tr><td colspan="4" style="padding:1rem;" class="muted">加载中…</td></tr>`;
    rules = await listRules();
    renderRows();
  } catch (e) {
    els.rulesBody.innerHTML = `<tr><td colspan="4" style="padding:1rem;color:#ff9f9f;">加载失败：${e.message}</td></tr>`;
  }
}

function openCreateDialog() {
  currentEditingRule = null;
  els.dialogTitle.textContent = "创建规则";
  els.formTitle.value = "";
  els.formDisplayOrder.value = "0";
  els.formContent.value = "";
  els.formIsOpen.checked = false;
  els.formRuleId.value = "";
  els.ruleDialog.showModal();
}

function openEditDialog(rule) {
  currentEditingRule = rule;
  els.dialogTitle.textContent = "编辑规则";
  els.formTitle.value = rule.title || "";
  els.formDisplayOrder.value = rule.displayOrder || 0;
  els.formContent.value = rule.content || "";
  els.formIsOpen.checked = rule.isOpen === 1;
  els.formRuleId.value = rule.id;
  els.ruleDialog.showModal();
}

async function saveRule() {
  try {
    const title = els.formTitle.value.trim();
    const content = els.formContent.value.trim();
    const displayOrder = Number(els.formDisplayOrder.value);
    const isOpen = els.formIsOpen.checked ? 1 : 0;

    if (!title) {
      alert("请填写规则标题");
      return;
    }
    if (!content) {
      alert("请填写规则内容");
      return;
    }

    const payload = {
      title: title,
      content: content,
      displayOrder: displayOrder,
      isOpen: isOpen
    };

    if (currentEditingRule) {
      await updateRule(currentEditingRule.id, payload);
      alert("规则已更新");
    } else {
      await createRule(payload);
      alert("规则已创建");
    }

    els.ruleDialog.close();
    await refresh();
  } catch (e) {
    alert(`${currentEditingRule ? '更新' : '创建'}失败：${e.message}`);
  }
}

function handleTableClick(e) {
  const row = e.target.closest("tr");
  if (!row) return;
  const ruleId = Number(row.dataset.ruleId);
  const action = e.target.dataset.action;

  if (action === "edit") {
    const rule = rules.find(r => r.id === ruleId);
    if (rule) {
      openEditDialog(rule);
    }
  } else if (action === "delete") {
    if (!confirm("确认删除该规则？")) return;
    try {
      deleteRule(ruleId);
      alert("规则已删除");
      refresh();
    } catch (err) {
      alert(`删除失败：${err.message}`);
    }
  }
}

async function init() {
  bindEls();
  try {
    await refresh();
    els.createRuleBtn.addEventListener("click", openCreateDialog);
    els.closeDialogBtn.addEventListener("click", () => els.ruleDialog.close());
    els.saveBtn.addEventListener("click", saveRule);
    els.rulesBody.addEventListener("click", handleTableClick);
  } catch (e) {
    els.rulesBody.innerHTML = `<tr><td colspan="4" style="padding:1rem;color:#ff9f9f;">初始化失败：${e.message}</td></tr>`;
  }
}

document.addEventListener("DOMContentLoaded", init);
