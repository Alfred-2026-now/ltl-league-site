import { getApiBase } from "../config/api.js";

const API = getApiBase();
const message = document.getElementById("adminPredictionMessage");
const optionInputs = document.getElementById("predictionOptionInputs");
const editDialog = document.getElementById("predictionEditDialog");
const settleDialog = document.getElementById("predictionSettleDialog");
let statusFilter = "PUBLISHED";
let predictions = [];

async function request(path, options = {}) {
  const response = await fetch(`${API}${path}`, { credentials: "include", ...options });
  const data = await response.json().catch(() => null);
  if (!response.ok || !data || data.code !== 200) throw new Error(data?.message || `请求失败（${response.status}）`);
  return data.data;
}
const json = (payload, method = "POST") => ({ method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload || {}) });
const esc = value => String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
const time = value => value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "-";

function setMessage(text, error = false) {
  message.textContent = text || "";
  message.classList.toggle("error", error);
}

// ==================== 发布表单 ====================

const quickFillPresets = {
  bo2: ["2:0", "1:1", "0:2"],
  bo3: ["2:0", "2:1", "1:2", "0:2"],
  bo5: ["3:0", "3:1", "3:2", "2:3", "1:3", "0:3"]
};

function optionInputRow(value = "") {
  const index = optionInputs.children.length + 1;
  const row = document.createElement("div");
  row.className = "task-actions";
  row.innerHTML = `<input class="input" maxlength="100" placeholder="选项${index}" value="${esc(value)}" required />
    <button class="btn ghost" type="button" data-remove-option>移除</button>`;
  row.querySelector("[data-remove-option]").addEventListener("click", () => {
    if (optionInputs.children.length <= 2) return setMessage("至少保留2个选项", true);
    row.remove();
    refreshOptionPlaceholders();
  });
  return row;
}

function refreshOptionPlaceholders() {
  [...optionInputs.children].forEach((row, index) => {
    row.querySelector("input").placeholder = `选项${index + 1}`;
  });
}

function renderOptionInputs(values = ["", ""]) {
  optionInputs.innerHTML = "";
  values.forEach(value => optionInputs.append(optionInputRow(value)));
  refreshOptionPlaceholders();
}

document.getElementById("addPredictionOptionBtn").addEventListener("click", () => {
  if (optionInputs.children.length >= 6) return setMessage("最多6个选项", true);
  optionInputs.append(optionInputRow());
  refreshOptionPlaceholders();
});

document.querySelectorAll("[data-quick-fill]").forEach(button => {
  button.addEventListener("click", () => {
    const preset = quickFillPresets[button.dataset.quickFill] || [];
    renderOptionInputs(preset);
    setMessage(`已填充 ${preset.length} 个比分选项，可继续调整`);
  });
});

async function publish(event) {
  event.preventDefault();
  const form = event.target;
  const labels = [...optionInputs.querySelectorAll("input")].map(input => input.value.trim()).filter(Boolean);
  if (labels.length < 2) return setMessage("请至少填写2个选项", true);
  const payload = {
    title: form.title.value.trim(),
    description: form.description.value.trim() || null,
    options: labels,
    rewardP: Number(form.rewardP.value || 0),
    rewardBounty: Number(form.rewardBounty.value || 0),
    deadlineAt: form.deadlineAt.value || null
  };
  try {
    setMessage("发布中…");
    await request("/admin/predictions", json(payload));
    setMessage("竞猜已发布");
    form.reset();
    renderOptionInputs();
    await loadList();
  } catch (error) {
    setMessage(error.message, true);
  }
}

// ==================== 列表 ====================

function predictionCard(prediction) {
  const chips = prediction.status === "PUBLISHED"
    ? (prediction.bettingOpen ? "进行中" : "已截止 · 待结算")
    : (prediction.status === "SETTLED" ? "已结算" : "已作废");
  const options = (prediction.options || []).map(option => {
    const votes = `${option.voteCount ?? 0}票`;
    const correct = option.isCorrect ? "✅ " : "";
    return `<span>${correct}${esc(option.label)}（${votes}）</span>`;
  }).join("");
  const facts = `<div class="task-facts">
      <span>截止：${time(prediction.deadlineAt)}</span>
      <span>总投注：${prediction.totalBets || 0}票</span>
      <span>正确答案：${prediction.status === "PUBLISHED" ? "未定" : esc(prediction.correctOptionLabel || (prediction.winnerCount === 0 ? "无人猜中" : "-"))}</span>
      ${prediction.status === "SETTLED" && prediction.winnerCount > 0 ? `<span>${prediction.winnerCount}人猜对 · 每人 ${prediction.rewardPPerWinner}P + 🪙${prediction.rewardBountyPerWinner}</span>` : ""}
    </div>`;
  const winners = prediction.status === "SETTLED" && (prediction.winners || []).length
    ? `<p class="task-note">中奖选手：${prediction.winners.map(winner => esc(winner.playerName)).join("、")}</p>`
    : "";
  const cancelled = prediction.status === "CANCELLED"
    ? `<p class="task-review-comment">作废原因：${esc(prediction.cancelReason || "-")}</p>`
    : "";
  const actions = prediction.status === "PUBLISHED"
    ? `<div class="task-actions">
        <button class="btn" data-edit-prediction="${prediction.id}">编辑</button>
        <button class="btn primary" data-settle-prediction="${prediction.id}">结算发奖</button>
        <button class="btn ghost" data-cancel-prediction="${prediction.id}">作废</button>
        <button class="btn ghost" data-delete-prediction="${prediction.id}">删除</button>
      </div>`
    : `<div class="task-actions">
        ${prediction.status === "SETTLED" ? `<button class="btn" data-revoke-prediction="${prediction.id}">撤回结算</button>` : ""}
        <button class="btn ghost" data-delete-prediction="${prediction.id}">删除</button>
      </div>`;
  return `<article class="panel task-card">
    <div class="task-card-heading">
      <div><span class="task-status">${chips}</span><h3>${esc(prediction.title)}</h3><p class="muted">发布于 ${time(prediction.createdAt)}</p></div>
      <div class="task-rewards"><strong>${prediction.rewardPTotal}P</strong><strong>🪙 ${prediction.rewardBountyTotal}</strong></div>
    </div>
    ${prediction.description ? `<div class="task-requirements">${esc(prediction.description).replaceAll("\n", "<br>")}</div>` : ""}
    <div class="task-facts">${options}</div>
    ${facts}${winners}${cancelled}${actions}
  </article>`;
}

async function loadList() {
  try {
    predictions = await request("/admin/predictions");
    renderList();
  } catch (error) {
    setMessage(error.message, true);
  }
}

function renderList() {
  document.querySelectorAll("[data-admin-prediction-tab]").forEach(button =>
    button.classList.toggle("primary", button.dataset.adminPredictionTab === statusFilter));
  const visible = predictions.filter(prediction => prediction.status === statusFilter);
  document.getElementById("adminPredictionList").innerHTML = visible.length
    ? visible.map(predictionCard).join("")
    : `<div class="panel task-empty">该分类下暂无竞猜。</div>`;
}

// ==================== 编辑 ====================

function openEdit(id) {
  const prediction = predictions.find(item => item.id === id);
  if (!prediction) return;
  const form = editDialog.querySelector("form");
  form.predictionId.value = prediction.id;
  form.title.value = prediction.title;
  form.description.value = prediction.description || "";
  form.deadlineAt.value = "";
  editDialog.showModal();
}

async function saveEdit(event) {
  event.preventDefault();
  const form = event.target;
  const payload = {
    title: form.title.value.trim(),
    description: form.description.value.trim() || null,
    deadlineAt: form.deadlineAt.value || null
  };
  try {
    setMessage("保存中…");
    await request(`/admin/predictions/${form.predictionId.value}`, json(payload, "PUT"));
    setMessage("修改已保存");
    editDialog.close();
    await loadList();
  } catch (error) {
    setMessage(error.message, true);
  }
}

// ==================== 结算 ====================

let previewedOptionId = null;

function openSettle(id) {
  const prediction = predictions.find(item => item.id === id);
  if (!prediction) return;
  const form = settleDialog.querySelector("form");
  form.predictionId.value = prediction.id;
  previewedOptionId = null;
  document.getElementById("settlePreview").innerHTML = "";
  document.getElementById("confirmSettleBtn").disabled = true;
  document.getElementById("settleOptionRadios").innerHTML = (prediction.options || []).map(option =>
    `<label class="task-anonymous-option"><input type="radio" name="correctOptionId" value="${option.id}" required /><span>${esc(option.label)}</span></label>`
  ).join("");
  settleDialog.showModal();
}

async function previewSettle() {
  const form = settleDialog.querySelector("form");
  const checked = form.querySelector('input[name="correctOptionId"]:checked');
  if (!checked) return setMessage("请先选择正确选项", true);
  previewedOptionId = null;
  document.getElementById("settlePreview").innerHTML = `<p class="muted">预览加载中…</p>`;
  try {
    const preview = await request(`/admin/predictions/${form.predictionId.value}/settle-preview?correctOptionId=${checked.value}`);
    previewedOptionId = Number(checked.value);
    const winners = preview.winnerCount > 0
      ? `<p>中奖选手：${preview.winners.map(winner => esc(winner.playerName)).join("、")}</p>`
      : "<p>无人猜中，奖励不发放。</p>";
    document.getElementById("settlePreview").innerHTML = `<div class="prediction-result-banner">
      <p>正确选项：<strong>${esc(preview.correctOptionLabel)}</strong> · ${preview.winnerCount}人猜对</p>
      <p>每人获得：<strong>${preview.rewardPPerWinner}P</strong> + <strong>🪙 ${preview.rewardBountyPerWinner}</strong> 赏金（实发合计 ${preview.actualPTotal}P + 🪙${preview.actualBountyTotal}）</p>
      ${winners}
    </div>`;
    document.getElementById("confirmSettleBtn").disabled = false;
  } catch (error) {
    document.getElementById("settlePreview").innerHTML = `<p class="task-error">${esc(error.message)}</p>`;
  }
}

async function confirmSettle(event) {
  event.preventDefault();
  const form = settleDialog.querySelector("form");
  const checked = form.querySelector('input[name="correctOptionId"]:checked');
  if (!checked || Number(checked.value) !== previewedOptionId) {
    return setMessage("选项已变动，请重新预览后再确认", true);
  }
  if (!confirm("确认结算？将立即向猜对选手发放奖励。")) return;
  try {
    setMessage("结算中…");
    await request(`/admin/predictions/${form.predictionId.value}/settle`, json({ correctOptionId: Number(checked.value) }));
    setMessage("结算完成，奖励已发放");
    settleDialog.close();
    await loadList();
  } catch (error) {
    setMessage(error.message, true);
  }
}

// ==================== 撤回 / 作废 / 删除 ====================

async function revokePrediction(id) {
  const prediction = predictions.find(item => item.id === id);
  const reason = prompt(`撤回「${prediction?.title || ""}」的结算。将按结算快照扣回每位中奖选手的奖励（余额可为负），竞猜回到进行中，可重新结算。\n请输入撤回原因：`);
  if (reason == null) return;
  const trimmed = reason.trim();
  if (!trimmed) return setMessage("撤回原因不能为空", true);
  if (!confirm("确认撤回结算？已发放的奖励将被扣回。")) return;
  try {
    setMessage("撤回中…");
    await request(`/admin/predictions/${id}/revoke`, json({ reason: trimmed }));
    setMessage("结算已撤回，奖励已扣回，竞猜回到进行中");
    await loadList();
  } catch (error) {
    setMessage(error.message, true);
  }
}

async function cancelPrediction(id) {
  const reason = prompt("请输入作废原因（选手可见）：");
  if (reason == null) return;
  const trimmed = reason.trim();
  if (!trimmed) return setMessage("作废原因不能为空", true);
  if (!confirm("确认作废该竞猜？作废后不能恢复，不发放奖励。")) return;
  try {
    setMessage("处理中…");
    await request(`/admin/predictions/${id}/cancel`, json({ reason: trimmed }));
    setMessage("竞猜已作废");
    await loadList();
  } catch (error) {
    setMessage(error.message, true);
  }
}

async function deletePrediction(id) {
  if (!confirm("确认删除该竞猜？删除仅移除展示，不影响任何已发放的积分与流水记录。")) return;
  try {
    setMessage("处理中…");
    await request(`/admin/predictions/${id}/delete`, { method: "POST" });
    setMessage("竞猜已删除（仅移除展示）");
    await loadList();
  } catch (error) {
    setMessage(error.message, true);
  }
}

// ==================== 事件绑定 ====================

document.addEventListener("click", async event => {
  const tab = event.target.closest("[data-admin-prediction-tab]");
  if (tab) {
    statusFilter = tab.dataset.adminPredictionTab;
    renderList();
    return;
  }
  const edit = event.target.closest("[data-edit-prediction]");
  if (edit) return openEdit(Number(edit.dataset.editPrediction));
  const settle = event.target.closest("[data-settle-prediction]");
  if (settle) return openSettle(Number(settle.dataset.settlePrediction));
  const revoke = event.target.closest("[data-revoke-prediction]");
  if (revoke) return revokePrediction(Number(revoke.dataset.revokePrediction));
  const cancel = event.target.closest("[data-cancel-prediction]");
  if (cancel) return cancelPrediction(Number(cancel.dataset.cancelPrediction));
  const del = event.target.closest("[data-delete-prediction]");
  if (del) return deletePrediction(Number(del.dataset.deletePrediction));
});

document.getElementById("predictionPublishForm").addEventListener("submit", publish);
document.getElementById("predictionEditForm").addEventListener("submit", saveEdit);
document.getElementById("predictionSettleForm").addEventListener("submit", confirmSettle);
document.getElementById("previewSettleBtn").addEventListener("click", previewSettle);
document.getElementById("cancelPredictionEdit").addEventListener("click", () => editDialog.close());
document.getElementById("cancelSettleBtn").addEventListener("click", () => settleDialog.close());

renderOptionInputs();
await loadList();
