import { getApiBase } from "../config/api.js";

const API = getApiBase();
const message = document.getElementById("adminTaskMessage");
let taskSettings = { anonymousMinimumFee: 50, anonymousFeeRate: 10 };
let taskById = new Map();
let claimHistory = [];

const claimStatusText = {
  CLAIMED: "已接取", PROOF_PENDING: "证明待审", PROOF_RETURNED: "证明已打回",
  COMPLETED: "已完成", ADMIN_CANCELLED: "管理员已取消", ABANDONED: "已主动放弃",
  TERMINATED: "任务注销终止", COMPLETION_REVOKED: "完成已撤回"
};
const unfinishedStatuses = ["CLAIMED", "PROOF_PENDING", "PROOF_RETURNED"];

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

const publisherLabel = task => `${task.publisherName}${task.anonymous ? "（匿名）" : ""}`;
const anonymousFee = (task, maxClaimants) => Math.max(
  taskSettings.anonymousMinimumFee,
  Math.ceil((Number(task.pReward) || 0) * maxClaimants * taskSettings.anonymousFeeRate / 100)
);

function pendingTask(task) {
  const privacy = task.anonymous
    ? `<p class="task-review-comment"><strong>匿名发布：</strong>当前费率 ${taskSettings.anonymousFeeRate}%，最低 ${taskSettings.anonymousMinimumFee}P；按 1 个名额预计收取 <strong data-anonymous-preview="${task.id}">${anonymousFee(task, 1)}P</strong>，通过时从发布者账户扣除且不退。</p>`
    : "";
  return `<article class="panel task-card"><div class="task-card-heading"><div><span class="task-status">待审核</span><h3>${esc(task.title)}</h3><p class="muted">发布者：${esc(publisherLabel(task))} · ${time(task.createdAt)}</p></div><div class="task-rewards"><strong>${task.pReward}P</strong><strong>🪙 ${task.bountyReward}</strong></div></div><div class="task-requirements">${esc(task.requirements).replaceAll("\n", "<br>")}</div>${task.budgetNote ? `<p class="task-note">预算备注：${esc(task.budgetNote)}</p>` : ""}${privacy}<div class="task-form-grid task-admin-config"><label class="field"><span class="field-label">接取费用</span><input class="input" type="number" min="0" value="0" data-fee="${task.id}" /></label><label class="field"><span class="field-label">最大接取人数</span><input class="input" type="number" min="1" max="100" value="1" data-max="${task.id}" /></label></div><div class="task-actions"><button class="btn primary" data-publish="${task.id}">通过并发布</button><button class="btn ghost" data-return-task="${task.id}">打回</button></div></article>`;
}

function proofCard(proof, taskMap) {
  const task = taskMap.get(proof.taskId);
  return `<article class="panel task-card"><div class="task-card-heading"><div><span class="task-status">证明待审核</span><h3>${esc(task?.title || `任务 #${proof.taskId}`)}</h3><p class="muted">提交者：${esc(proof.playerName)} · 第${proof.attemptNo}次提交 · ${time(proof.createdAt)}</p></div></div>${proof.description ? `<div class="task-requirements">${esc(proof.description)}</div>` : ""}<div class="task-proof-images">${(proof.images || []).map(image => `<a href="${esc(image.url)}" target="_blank" rel="noopener"><img src="${esc(image.url)}" alt="${esc(image.label)}" /></a>`).join("")}</div><div class="task-actions"><button class="btn primary" data-approve-proof="${proof.id}">确认完成并发奖</button><button class="btn ghost" data-return-proof="${proof.id}">打回证明</button></div></article>`;
}

function publishedTask(task) {
  const unfinished = (task.claims || []).filter(claim => unfinishedStatuses.includes(claim.status)).length;
  const pending = (task.claims || []).filter(claim => claim.status === "PROOF_PENDING").length;
  return `<article class="panel task-card"><div class="task-card-heading"><div><span class="task-status">${task.official ? "官方任务" : "进行中"}</span><h3>${esc(task.title)}</h3><p class="muted">发布者：${esc(publisherLabel(task))} · ${time(task.publishedAt)}</p></div><div class="task-rewards"><strong>${task.pReward}P</strong><strong>🪙 ${task.bountyReward}</strong></div></div><div class="task-requirements">${esc(task.requirements).replaceAll("\n", "<br>")}</div>${task.budgetNote ? `<p class="task-note">备注：${esc(task.budgetNote)}</p>` : ""}<div class="task-facts"><span>接取 ${task.claimedCount}/${task.maxClaimants}</span><span>完成 ${task.completedCount}</span><span>未完成 ${unfinished}</span><span>待审 ${pending}</span><span>剩余冻结 ${task.escrowRemaining}P</span>${task.anonymous ? `<span>匿名发布费 ${task.anonymousFeeAmount}P（费率快照 ${task.anonymousFeeRateSnapshot}%）</span>` : ""}</div><div class="task-actions"><button class="btn" data-edit-published="${task.id}">编辑任务</button><button class="btn ghost" data-close-task="${task.id}" data-close-summary="未完成${unfinished}人、待审${pending}份、剩余冻结${task.escrowRemaining}P">注销任务</button></div></article>`;
}

function renderClaimHistory() {
  const taskId = document.getElementById("claimTaskFilter").value;
  const player = document.getElementById("claimPlayerFilter").value.trim().toLowerCase();
  const status = document.getElementById("claimStatusFilter").value;
  const rows = claimHistory.filter(claim => (!taskId || String(claim.taskId) === taskId)
    && (!player || String(claim.playerId).includes(player) || claim.playerName.toLowerCase().includes(player))
    && (!status || claim.status === status));
  document.getElementById("claimHistoryList").innerHTML = rows.map(claim => {
    const currentTask = taskById.get(claim.taskId);
    const canCancel = currentTask?.status === "PUBLISHED" && unfinishedStatuses.includes(claim.status);
    return `<article class="panel task-card"><div class="task-card-heading"><div><span class="task-status">${esc(claimStatusText[claim.status] || claim.status)}</span><h3>${esc(claim.taskTitle)} <small>(${esc(claim.taskSeason)})</small></h3><p class="muted">选手：${esc(claim.playerName)} (#${claim.playerId}) · 接取记录 #${claim.id}</p></div><div class="task-rewards"><strong>${claim.pReward}P</strong><strong>🪙 ${claim.bountyReward}</strong></div></div><div class="task-facts"><span>接取时间：${time(claim.claimedAt)}</span><span>接取费：${claim.feeAmount}P</span>${claim.completedAt ? `<span>完成：${time(claim.completedAt)}</span>` : ""}${claim.reclaimAvailableAt ? `<span>可再次接取：${time(claim.reclaimAvailableAt)}</span>` : ""}</div>${claim.adminCancelReason ? `<p class="task-note">管理员取消原因：${esc(claim.adminCancelReason)}；接取费已全额退还。</p>` : ""}${canCancel ? `<div class="task-actions"><button class="btn ghost" data-cancel-claim="${claim.id}" data-player-name="${esc(claim.playerName)}" data-fee-amount="${claim.feeAmount}">取消接取并退款</button></div>` : ""}</article>`;
  }).join("") || '<div class="panel task-empty">没有符合条件的接取记录。</div>';
}

function closedTask(task) {
  return `<article class="panel task-card"><div class="task-card-heading"><div><span class="task-status">${task.status === "CLOSED" ? "已注销" : "已放弃"}</span><h3>${esc(task.title)}</h3><p class="muted">发布者：${esc(publisherLabel(task))} · ${time(task.closedAt || task.createdAt)}</p></div></div>${task.closeReason ? `<p class="task-note">注销原因：${esc(task.closeReason)}</p>` : ""}<div class="task-facts"><span>完成 ${task.completedCount}</span><span>剩余冻结 ${task.escrowRemaining}P</span>${task.anonymousFeeAmount ? `<span>匿名发布费 ${task.anonymousFeeAmount}P（不退）</span>` : ""}</div></article>`;
}

function completionHistoryCard(record) {
  const { task, claim } = record;
  const revoked = claim.status === "COMPLETION_REVOKED";
  const proof = (claim.proofs || []).find(item => ["APPROVED", "REVOKED"].includes(item.status));
  const images = proof?.images || [];
  const canRevoke = !revoked && task.status === "PUBLISHED";
  return `<article class="panel task-card">
    <div class="task-card-heading">
      <div><span class="task-status">${revoked ? "完成已撤回" : "已完成"}</span><h3>${esc(claim.taskTitle)}</h3><p class="muted">发布者：${esc(publisherLabel(task))} · 完成者：${esc(claim.playerName)} · 完成时间：${time(claim.completedAt)}</p></div>
      <div class="task-rewards"><strong>${claim.pReward}P</strong><strong>🪙 ${claim.bountyReward}</strong></div>
    </div>
    <div class="task-facts"><span>接取记录 #${claim.id}</span><span>接取费 ${claim.feeAmount}P（撤回不退）</span><span>任务状态：${task.status === "PUBLISHED" ? "进行中" : "已结束"}</span></div>
    ${revoked ? `<p class="task-review-comment"><strong>撤回记录：</strong>${esc(claim.completionRevokeReason || "-")} · ${esc(claim.completionRevokedByName || "管理员")} · ${time(claim.completionRevokedAt)}</p>` : ""}
    <div class="task-proof-images">${images.map(image => `<a href="${esc(image.url)}" target="_blank" rel="noopener"><img src="${esc(image.url)}" alt="${esc(image.label)}" /></a>`).join("")}</div>
    ${canRevoke ? `<div class="task-actions"><button class="btn ghost" data-revoke-completion="${claim.id}" data-revoke-title="${esc(task.title)}" data-revoke-player="${esc(claim.playerName)}" data-revoke-p="${claim.pReward}" data-revoke-bounty="${claim.bountyReward}">撤回完成并回退奖励</button></div>` : ""}
  </article>`;
}

async function load() {
  try {
    const [tasks, proofs, settings, claims] = await Promise.all([request("/admin/event-tasks"), request("/admin/event-task-proofs/pending"), request("/event-tasks/settings"), request("/admin/event-task-claims")]);
    taskSettings = settings;
    taskById = new Map(tasks.map(task => [task.id, task]));
    claimHistory = claims;
    const filter = document.getElementById("claimTaskFilter");
    const selectedTask = filter.value;
    const uniqueTasks = new Map(claims.map(claim => [claim.taskId, `${claim.taskTitle} (${claim.taskSeason})`]));
    filter.innerHTML = '<option value="">全部任务</option>' + [...uniqueTasks].map(([id, title]) => `<option value="${id}">${esc(title)}</option>`).join("");
    filter.value = selectedTask;
    const pending = tasks.filter(task => task.status === "PENDING_REVIEW");
    const published = tasks.filter(task => task.status === "PUBLISHED");
    const closed = tasks.filter(task => ["CLOSED", "ABANDONED"].includes(task.status));
    const completions = tasks.flatMap(task => (task.claims || [])
      .filter(claim => ["COMPLETED", "COMPLETION_REVOKED"].includes(claim.status))
      .map(claim => ({ task, claim })))
      .sort((left, right) => new Date(right.claim.completedAt || 0) - new Date(left.claim.completedAt || 0));
    document.getElementById("pendingTaskList").innerHTML = pending.map(pendingTask).join("") || '<div class="panel task-empty">暂无待审核任务。</div>';
    document.getElementById("pendingProofList").innerHTML = proofs.map(proof => proofCard(proof, taskById)).join("") || '<div class="panel task-empty">暂无待审核证明。</div>';
    document.getElementById("publishedTaskList").innerHTML = published.map(publishedTask).join("") || '<div class="panel task-empty">暂无进行中任务。</div>';
    document.getElementById("completedTaskList").innerHTML = completions.map(completionHistoryCard).join("") || '<div class="panel task-empty">暂无完成记录。</div>';
    document.getElementById("closedTaskList").innerHTML = closed.slice(0, 20).map(closedTask).join("") || '<div class="panel task-empty">暂无已结束任务。</div>';
    renderClaimHistory();
  } catch (error) {
    setMessage(error.message, true);
  }
}

async function act(action, success) {
  try {
    setMessage("处理中…");
    await action();
    setMessage(success);
    await load();
  } catch (error) {
    setMessage(error.message, true);
  }
}

document.addEventListener("click", async event => {
  const editPublished = event.target.closest("[data-edit-published]");
  if (editPublished) {
    const task = taskById.get(Number(editPublished.dataset.editPublished));
    const form = document.getElementById("adminTaskEditForm");
    for (const field of ["title", "requirements", "pReward", "bountyReward", "budgetNote"]) form.elements[field].value = task[field] ?? "";
    form.elements.taskId.value = task.id;
    document.getElementById("adminTaskEditDialog").showModal();
    return;
  }
  const cancelClaim = event.target.closest("[data-cancel-claim]");
  if (cancelClaim) {
    const reason = prompt(`请输入取消 ${cancelClaim.dataset.playerName} 接取的原因（将全额退还 ${cancelClaim.dataset.feeAmount}P）：`)?.trim();
    if (reason && reason.length <= 500 && confirm("确认取消该接取？名额立即返还，待审证明作废；选手一小时后可重新接取。")) {
      await act(() => request(`/admin/event-task-claims/${cancelClaim.dataset.cancelClaim}/cancel`, json({ reason })), "已取消接取并全额退费");
    }
    return;
  }
  const publish = event.target.closest("[data-publish]");
  if (publish) {
    const id = publish.dataset.publish;
    const claimFee = Number(document.querySelector(`[data-fee="${id}"]`).value);
    const maxClaimants = Number(document.querySelector(`[data-max="${id}"]`).value);
    const task = taskById.get(Number(id));
    const privacyText = task?.anonymous ? `，并收取匿名发布费 ${anonymousFee(task, maxClaimants)}P` : "";
    if (!confirm(`确认以接取费 ${claimFee}P、最多 ${maxClaimants} 人通过并直接发布？系统会立即冻结发布者足额P币${privacyText}。`)) return;
    await act(() => request(`/admin/event-tasks/${id}/publish`, json({ claimFee, maxClaimants })), "任务已发布");
  }
  const returnTask = event.target.closest("[data-return-task]");
  if (returnTask) {
    const comment = prompt("请输入打回修改意见：")?.trim();
    if (comment) await act(() => request(`/admin/event-tasks/${returnTask.dataset.returnTask}/return`, json({ comment })), "任务已打回");
  }
  const approve = event.target.closest("[data-approve-proof]");
  if (approve && confirm("确认该选手已完成任务并立即发放P币与赏金积分？")) {
    await act(() => request(`/admin/event-task-proofs/${approve.dataset.approveProof}/approve`, { method: "POST" }), "审核通过，奖励已发放");
  }
  const returnProof = event.target.closest("[data-return-proof]");
  if (returnProof) {
    const comment = prompt("请输入证明未通过原因：")?.trim();
    if (comment) await act(() => request(`/admin/event-task-proofs/${returnProof.dataset.returnProof}/return`, json({ comment })), "证明已打回");
  }
  const revokeCompletion = event.target.closest("[data-revoke-completion]");
  if (revokeCompletion) {
    const reason = prompt("请输入撤回完成的原因（将记录在历史中）：")?.trim();
    if (reason && confirm(`确认撤回“${revokeCompletion.dataset.revokeTitle}”中${revokeCompletion.dataset.revokePlayer}的完成记录？\n将扣回 ${revokeCompletion.dataset.revokeP}P币和 ${revokeCompletion.dataset.revokeBounty} 赏金积分，返还一个接取名额；接取费不退。`)) {
      await act(
        () => request(`/admin/event-task-claims/${revokeCompletion.dataset.revokeCompletion}/revoke-completion`, json({ reason })),
        "完成记录已撤回，奖励和名额已回退"
      );
    }
  }
  const close = event.target.closest("[data-close-task]");
  if (close) {
    const reason = prompt(`注销影响：${close.dataset.closeSummary}。未完成接取费用不退。\n请输入注销原因：`)?.trim();
    if (reason && confirm("确认注销该任务？此操作会终止全部未完成接取。")) {
      await act(() => request(`/admin/event-tasks/${close.dataset.closeTask}/close`, json({ reason })), "任务已注销");
    }
  }
});

document.addEventListener("input", event => {
  if (event.target.id === "claimPlayerFilter") {
    renderClaimHistory();
    return;
  }
  const input = event.target.closest("[data-max]");
  if (!input) return;
  const task = taskById.get(Number(input.dataset.max));
  const preview = document.querySelector(`[data-anonymous-preview="${input.dataset.max}"]`);
  const maxClaimants = Number(input.value);
  if (task?.anonymous && preview && Number.isFinite(maxClaimants) && maxClaimants > 0) {
    preview.textContent = `${anonymousFee(task, maxClaimants)}P`;
  }
});

for (const id of ["claimTaskFilter", "claimStatusFilter"]) {
  document.getElementById(id).addEventListener("change", renderClaimHistory);
}

document.getElementById("cancelAdminTaskEdit").addEventListener("click", () => document.getElementById("adminTaskEditDialog").close());
document.getElementById("adminTaskEditForm").addEventListener("submit", async event => {
  event.preventDefault();
  const form = event.target;
  const task = taskById.get(Number(form.elements.taskId.value));
  const payload = {
    title: form.elements.title.value.trim(),
    requirements: form.elements.requirements.value.trim(),
    pReward: Number(form.elements.pReward.value),
    bountyReward: Number(form.elements.bountyReward.value),
    budgetNote: form.elements.budgetNote.value.trim()
  };
  if (payload.pReward + payload.bountyReward <= 0) { setMessage("P币和赏金积分至少一项大于0", true); return; }
  const delta = (payload.pReward - task.pReward) * (task.maxClaimants - task.completedCount);
  const fundMessage = task.official ? "官方任务由系统发奖。" : delta > 0 ? `将从发布者余额追加冻结 ${delta}P。` : delta < 0 ? `将向发布者退还 ${-delta}P冻结额。` : "冻结额不变。";
  if (!confirm(`确认修改“${task.title}”？未完成与未来接取将使用新内容和奖励；已完成记录不变。${fundMessage}匿名费不重算。`)) return;
  try {
    setMessage("处理中…");
    await request(`/admin/event-tasks/${task.id}`, json(payload, "PUT"));
    document.getElementById("adminTaskEditDialog").close();
    setMessage("任务已更新并记录编辑历史");
    await load();
  } catch (error) { setMessage(error.message, true); }
});

document.getElementById("officialTaskForm").addEventListener("submit", async event => {
  event.preventDefault();
  const data = new FormData(event.target);
  const payload = {
    title: String(data.get("title") || "").trim(),
    requirements: String(data.get("requirements") || "").trim(),
    pReward: Number(data.get("pReward") || 0),
    bountyReward: Number(data.get("bountyReward") || 0),
    budgetNote: String(data.get("budgetNote") || "").trim(),
    claimFee: Number(data.get("claimFee") || 0),
    maxClaimants: Number(data.get("maxClaimants") || 0)
  };
  if (!confirm("确认无成本直接发布该官方任务？")) return;
  await act(() => request("/admin/event-tasks/official", json(payload)), "官方任务已发布");
  event.target.reset();
});

await load();
