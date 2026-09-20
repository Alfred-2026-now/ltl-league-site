import { getApiBase } from "./config/api.js";
import { authApi } from "./util/auth.js";

const API = getApiBase();
const els = {
  content: document.getElementById("taskContent"),
  message: document.getElementById("taskMessage"),
  dialog: document.getElementById("taskEditDialog")
};
let currentUser = null;
let activeTab = "hall";
let cachedPublished = [];

async function request(path, options = {}) {
  const response = await fetch(`${API}${path}`, { credentials: "include", ...options });
  const data = await response.json().catch(() => null);
  if (!response.ok || !data || data.code !== 200) {
    throw new Error(data?.message || `请求失败（${response.status}）`);
  }
  return data.data;
}

function jsonOptions(method, payload) {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: payload == null ? undefined : JSON.stringify(payload)
  };
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function formatTime(value) {
  if (!value) return "-";
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

const statusText = {
  PENDING_REVIEW: "待管理员审核",
  RETURNED: "已打回",
  PUBLISHED: "进行中",
  CLOSED: "已注销",
  ABANDONED: "已放弃",
  CLAIMED: "已接取",
  PROOF_PENDING: "证明待审核",
  PROOF_RETURNED: "证明已打回",
  COMPLETED: "已完成",
  COMPLETION_REVOKED: "完成已撤回",
  TERMINATED: "因任务注销终止",
  PENDING: "待审核",
  APPROVED: "已通过",
  REVOKED: "完成已撤回",
  VOIDED: "已作废"
};

function setMessage(message, error = false) {
  els.message.textContent = message || "";
  els.message.classList.toggle("error", error);
}

function loginNotice() {
  const redirect = encodeURIComponent(window.location.href);
  return `<div class="panel task-empty">请先<a href="login.html?redirect=${redirect}">登录</a>后使用该功能。</div>`;
}

function taskCard(task, mode = "hall") {
  const claimButton = task.canClaim
    ? `<button class="btn primary" data-claim-task="${task.id}">支付 ${task.claimFee || 0}P 并接取</button>`
    : task.viewerClaimStatus
      ? `<span class="task-status">我的状态：${escapeHtml(statusText[task.viewerClaimStatus] || task.viewerClaimStatus)}</span>`
      : "";
  const editActions = mode === "published" && task.status === "RETURNED"
    ? `<button class="btn" data-edit-task="${task.id}">修改</button>
       <button class="btn primary" data-resubmit-task="${task.id}">重新投送</button>
       <button class="btn ghost" data-abandon-publication="${task.id}">放弃投送</button>`
    : "";
  const rewards = mode === "published"
    ? `<div class="task-rewards"><strong>${task.pReward || 0}P</strong><strong>🪙 ${task.bountyReward || 0}</strong></div>`
    : "";
  return `<article class="panel task-card">
    <div class="task-card-heading">
      <div>
        <div class="task-meta"><span class="task-status">${escapeHtml(statusText[task.status] || task.status)}</span>${task.official ? '<span class="task-official">官方任务</span>' : ""}</div>
        <h2>${escapeHtml(task.title)}</h2>
        <p class="muted">发布者：${escapeHtml(task.official ? "LTL官方" : task.publisherName)} · ${formatTime(task.publishedAt || task.createdAt)}</p>
      </div>
      ${rewards}
    </div>
    <div class="task-requirements">${escapeHtml(task.requirements).replaceAll("\n", "<br>")}</div>
    ${mode === "published" && task.budgetNote ? `<p class="task-note"><strong>预算备注：</strong>${escapeHtml(task.budgetNote)}</p>` : ""}
    ${task.latestReviewComment ? `<p class="task-review-comment"><strong>审核意见：</strong>${escapeHtml(task.latestReviewComment)}</p>` : ""}
    <div class="task-facts">
      ${mode === "hall" ? `<span class="task-reward-fact">完成奖励：${task.pReward || 0}P币</span><span class="task-reward-fact">悬赏积分：${task.bountyReward || 0}</span>` : ""}
      <span>接取费：${task.claimFee ?? "待审核"}P</span>
      <span>已接取：${task.claimedCount || 0}${task.maxClaimants != null ? ` / ${task.maxClaimants}` : ""}</span>
      <span>剩余：${task.remainingSlots ?? "-"}</span>
      <span>已完成：${task.completedCount || 0}</span>
    </div>
    <div class="task-actions">${claimButton}${editActions}</div>
  </article>`;
}

async function renderHall() {
  const tasks = await request("/event-tasks");
  els.content.innerHTML = tasks.length
    ? `<div class="task-list">${tasks.map(task => taskCard(task)).join("")}</div>`
    : `<div class="panel task-empty">当前暂无进行中的任务。</div>`;
}

function publishForm() {
  if (!currentUser) return loginNotice();
  return `<form class="panel task-form" id="publishTaskForm">
    <h2>发布新任务</h2>
    <p class="muted">提交后由管理员审核。管理员设置接取费用和人数并通过后，任务直接公开并冻结足额P币悬赏。</p>
    <label class="field"><span class="field-label">任务标题</span><input class="input" name="title" maxlength="200" required /></label>
    <label class="field"><span class="field-label">详细任务要求</span><textarea class="input" name="requirements" rows="7" maxlength="10000" required></textarea></label>
    <div class="task-form-grid">
      <label class="field"><span class="field-label">每位完成者P币奖励</span><input class="input" name="pReward" type="number" min="0" value="0" required /></label>
      <label class="field"><span class="field-label">每位完成者赏金积分</span><input class="input" name="bountyReward" type="number" min="0" value="0" required /></label>
    </div>
    <label class="field"><span class="field-label">最大预算备注（仅供管理员参考，不参与计算）</span><textarea class="input" name="budgetNote" rows="2" maxlength="500"></textarea></label>
    <button class="btn primary" type="submit">提交管理员审核</button>
  </form>`;
}

async function renderMinePublished() {
  if (!currentUser) {
    els.content.innerHTML = loginNotice();
    return;
  }
  cachedPublished = await request("/event-tasks/mine/published");
  els.content.innerHTML = cachedPublished.length
    ? `<div class="task-list">${cachedPublished.map(task => taskCard(task, "published")).join("")}</div>`
    : `<div class="panel task-empty">你还没有发布过任务。</div>`;
}

function proofHistory(proofs) {
  if (!proofs?.length) return "";
  return `<div class="task-proof-history"><strong>提交记录</strong>${proofs.map(proof => `
    <div class="task-proof-record">
      <span>第${proof.attemptNo}次 · ${escapeHtml(statusText[proof.status] || proof.status)} · ${formatTime(proof.createdAt)}</span>
      ${proof.description ? `<p>${escapeHtml(proof.description)}</p>` : ""}
      ${proof.reviewComment ? `<p class="task-review-comment">审核意见：${escapeHtml(proof.reviewComment)}</p>` : ""}
      <div class="task-proof-images">${(proof.images || []).map(image => `<a href="${escapeHtml(image.url)}" target="_blank" rel="noopener"><img src="${escapeHtml(image.url)}" alt="${escapeHtml(image.label)}" /></a>`).join("")}</div>
    </div>`).join("")}</div>`;
}

function claimCard(claim) {
  const active = ["CLAIMED", "PROOF_PENDING", "PROOF_RETURNED"].includes(claim.status);
  const canUpload = ["CLAIMED", "PROOF_RETURNED"].includes(claim.status);
  return `<article class="panel task-card">
    <div class="task-card-heading"><div><span class="task-status">${escapeHtml(statusText[claim.status] || claim.status)}</span><h2>${escapeHtml(claim.taskTitle)}</h2></div><div class="task-rewards"><strong>${claim.pReward}P</strong><strong>🪙 ${claim.bountyReward}</strong></div></div>
    <div class="task-facts"><span>接取费：${claim.feeAmount}P</span><span>接取时间：${formatTime(claim.claimedAt)}</span><span>无损放弃截止：${formatTime(claim.freeAbandonUntil)}</span></div>
    ${active ? `<div class="task-actions"><button class="btn ghost" data-abandon-claim="${claim.id}">${claim.freeAbandonAvailable ? "无损放弃并退费" : "放弃任务（费用不退）"}</button></div>` : ""}
    ${canUpload ? `<form class="task-proof-form" data-proof-form="${claim.id}"><label class="field"><span class="field-label">完成说明</span><textarea class="input" name="description" rows="3" maxlength="5000"></textarea></label><label class="field"><span class="field-label">证明截图（1至5张，单张≤10MB）</span><input class="input" type="file" name="files" accept="image/jpeg,image/png,image/webp" multiple required /></label><button class="btn primary" type="submit">提交完成证明</button></form>` : ""}
    ${proofHistory(claim.proofs)}
  </article>`;
}

async function renderMineClaimed() {
  if (!currentUser) {
    els.content.innerHTML = loginNotice();
    return;
  }
  const claims = await request("/event-tasks/mine/claimed");
  els.content.innerHTML = claims.length
    ? `<div class="task-list">${claims.map(claimCard).join("")}</div>`
    : `<div class="panel task-empty">你还没有接取过任务。</div>`;
}

async function renderActive() {
  setMessage("");
  document.querySelectorAll("[data-task-tab]").forEach(button => button.classList.toggle("primary", button.dataset.taskTab === activeTab));
  els.content.innerHTML = `<div class="panel task-empty">加载中…</div>`;
  try {
    if (activeTab === "hall") await renderHall();
    if (activeTab === "publish") els.content.innerHTML = publishForm();
    if (activeTab === "mine-published") await renderMinePublished();
    if (activeTab === "mine-claimed") await renderMineClaimed();
  } catch (error) {
    els.content.innerHTML = `<div class="panel task-empty task-error">${escapeHtml(error.message)}</div>`;
  }
}

function taskPayload(form) {
  const data = new FormData(form);
  return {
    title: String(data.get("title") || "").trim(),
    requirements: String(data.get("requirements") || "").trim(),
    pReward: Number(data.get("pReward") || 0),
    bountyReward: Number(data.get("bountyReward") || 0),
    budgetNote: String(data.get("budgetNote") || "").trim()
  };
}

document.addEventListener("click", async event => {
  const tab = event.target.closest("[data-task-tab]");
  if (tab) {
    activeTab = tab.dataset.taskTab;
    await renderActive();
    return;
  }
  const claimButton = event.target.closest("[data-claim-task]");
  if (claimButton) {
    if (!currentUser) return window.location.assign(`login.html?redirect=${encodeURIComponent(window.location.href)}`);
    if (!confirm("确认支付接取费用并接取任务？接取后30分钟内可无损放弃。")) return;
    await runAction(() => request(`/event-tasks/${claimButton.dataset.claimTask}/claims`, { method: "POST" }), "任务接取成功");
  }
  const abandonClaim = event.target.closest("[data-abandon-claim]");
  if (abandonClaim) {
    if (!confirm("确认放弃任务？30分钟内会退还接取费，超过30分钟费用不退，名额都会返还。")) return;
    await runAction(() => request(`/event-task-claims/${abandonClaim.dataset.abandonClaim}/abandon`, { method: "POST" }), "已放弃任务");
  }
  const edit = event.target.closest("[data-edit-task]");
  if (edit) openEdit(Number(edit.dataset.editTask));
  const resubmit = event.target.closest("[data-resubmit-task]");
  if (resubmit) await runAction(() => request(`/event-tasks/${resubmit.dataset.resubmitTask}/resubmit`, { method: "POST" }), "已重新提交审核");
  const abandonPublication = event.target.closest("[data-abandon-publication]");
  if (abandonPublication && confirm("确认放弃投送该任务？")) {
    await runAction(() => request(`/event-tasks/${abandonPublication.dataset.abandonPublication}/abandon-publication`, { method: "POST" }), "已放弃投送");
  }
});

document.addEventListener("submit", async event => {
  if (event.target.id === "publishTaskForm") {
    event.preventDefault();
    await runAction(() => request("/event-tasks", jsonOptions("POST", taskPayload(event.target))), "任务已提交审核");
  }
  const claimId = event.target.dataset.proofForm;
  if (claimId) {
    event.preventDefault();
    const data = new FormData(event.target);
    const files = event.target.querySelector('[name="files"]').files;
    data.delete("files");
    [...files].forEach(file => data.append("files", file));
    await runAction(() => request(`/event-task-claims/${claimId}/proofs`, { method: "POST", body: data }), "证明已提交审核");
  }
});

function openEdit(taskId) {
  const task = cachedPublished.find(item => item.id === taskId);
  if (!task) return;
  document.getElementById("editTaskId").value = task.id;
  document.getElementById("editTaskTitle").value = task.title;
  document.getElementById("editTaskRequirements").value = task.requirements;
  document.getElementById("editTaskPReward").value = task.pReward;
  document.getElementById("editTaskBountyReward").value = task.bountyReward;
  document.getElementById("editTaskBudgetNote").value = task.budgetNote || "";
  els.dialog.showModal();
}

document.getElementById("saveTaskEditBtn").addEventListener("click", async () => {
  const id = document.getElementById("editTaskId").value;
  const payload = {
    title: document.getElementById("editTaskTitle").value.trim(),
    requirements: document.getElementById("editTaskRequirements").value.trim(),
    pReward: Number(document.getElementById("editTaskPReward").value || 0),
    bountyReward: Number(document.getElementById("editTaskBountyReward").value || 0),
    budgetNote: document.getElementById("editTaskBudgetNote").value.trim()
  };
  await runAction(() => request(`/event-tasks/${id}`, jsonOptions("PUT", payload)), "修改已保存", false);
  els.dialog.close();
  await renderActive();
});

async function runAction(action, success, rerender = true) {
  try {
    setMessage("处理中…");
    await action();
    setMessage(success);
    if (rerender) await renderActive();
  } catch (error) {
    setMessage(error.message, true);
  }
}

currentUser = await authApi.getCurrentUser();
await renderActive();
