import { getApiBase } from "../config/api.js";
import { listAdminMatches, getCurrentTeams } from "./api.js";

const API = getApiBase();
const message = document.getElementById("adminPredictionMessage");
const optionInputs = document.getElementById("predictionOptionInputs");
const matchSelect = document.getElementById("predictionMatchSelect");
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
const toLocalInput = value => value ? String(value).slice(0, 16) : "";

function setMessage(text, error = false) {
  message.textContent = text || "";
  message.classList.toggle("error", error);
}

// ==================== 发布表单 ====================

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

function renderOptionInputs(count = 2) {
  optionInputs.innerHTML = "";
  for (let i = 0; i < count; i += 1) optionInputs.append(optionInputRow());
}

document.getElementById("addPredictionOptionBtn").addEventListener("click", () => {
  if (optionInputs.children.length >= 6) return setMessage("最多6个选项", true);
  optionInputs.append(optionInputRow());
  refreshOptionPlaceholders();
});

async function loadMatchOptions() {
  try {
    const [matches, teams] = await Promise.all([listAdminMatches({}), getCurrentTeams()]);
    const teamName = id => teams.find(team => team.id === id)?.name || `队伍#${id}`;
    matches
      .filter(match => match.id != null)
      .forEach(match => {
        const label = `${match.roundLabel || `第${match.round}轮`} · ${teamName(match.homeTeamId)} vs ${teamName(match.awayTeamId)} · ${time(match.matchDate)}`;
        matchSelect.insertAdjacentHTML("beforeend", `<option value="${match.id}">${esc(label)}</option>`);
      });
  } catch (error) {
    setMessage(`比赛列表加载失败：${error.message}`, true);
  }
}

async function publish(event) {
  event.preventDefault();
  const form = event.target;
  const labels = [...optionInputs.querySelectorAll("input")].map(input => input.value.trim()).filter(Boolean);
  if (labels.length < 2) return setMessage("请至少填写2个选项", true);
  const payload = {
    title: form.title.value.trim(),
    description: form.description.value.trim() || null,
    matchId: form.matchId.value ? Number(form.matchId.value) : null,
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
  const settleReady = prediction.status === "PUBLISHED" && !prediction.bettingOpen;
  const chips = prediction.status === "PUBLISHED"
    ? (prediction.bettingOpen ? "进行中" : "已截止 · 可结算")
    : (prediction.status === "SETTLED" ? "已结算" : "已作废");
  const match = prediction.matchId
    ? `<p class="muted">关联比赛：${esc(prediction.matchRoundLabel || `#${prediction.matchId}`)}${prediction.homeTeamName ? ` · ${esc(prediction.homeTeamName)} vs ${esc(prediction.awayTeamName)}` : ""}</p>`
    : "";
  const options = (prediction.options || []).map(option => {
    const votes = option.voteCount != null ? `${option.voteCount}票` : "票数保密";
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
        <button class="btn" data-edit-prediction="${prediction.id}">编辑 / 延长截止</button>
        <button class="btn primary" data-settle-prediction="${prediction.id}" ${settleReady ? "" : "disabled title=\"需过截止时间后才能结算\""}>结算发奖</button>
        <button class="btn ghost" data-cancel-prediction="${prediction.id}">作废</button>
      </div>`
    : "";
  return `<article class="panel task-card">
    <div class="task-card-heading">
      <div><span class="task-status">${chips}</span><h3>${esc(prediction.title)}</h3><p class="muted">发布于 ${time(prediction.createdAt)}</p>${match}</div>
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
  form.deadlineAt.min = toLocalInput(prediction.deadlineAt);
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
  if (!confirm("确认结算？将立即向猜对选手发放奖励，且不可撤销。")) return;
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

// ==================== 作废 ====================

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
  if (settle && !settle.disabled) return openSettle(Number(settle.dataset.settlePrediction));
  const cancel = event.target.closest("[data-cancel-prediction]");
  if (cancel) return cancelPrediction(Number(cancel.dataset.cancelPrediction));
});

document.getElementById("predictionPublishForm").addEventListener("submit", publish);
document.getElementById("predictionEditForm").addEventListener("submit", saveEdit);
document.getElementById("predictionSettleForm").addEventListener("submit", confirmSettle);
document.getElementById("previewSettleBtn").addEventListener("click", previewSettle);
document.getElementById("cancelPredictionEdit").addEventListener("click", () => editDialog.close());
document.getElementById("cancelSettleBtn").addEventListener("click", () => settleDialog.close());

renderOptionInputs();
await loadMatchOptions();
await loadList();
