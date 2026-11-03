import { getApiBase } from "../config/api.js";

const API = getApiBase();
const message = document.getElementById("adminTaskMessage");
let taskSettings = { anonymousMinimumFee: 50, anonymousFeeRate: 10, publicationFeeRate: 10, publicationFeeCap: 1000 };
let taskById = new Map();
let claimHistory = [];
let completionHistory = [];
let categories = [];
// 分页状态：各区块独立翻页
const PAGE_SIZES = { pending: 20, published: 20, closed: 20, completions: 15, claims: 15 };
const SIZE_OPTIONS = [10, 15, 20, 50, 100];
// 记住用户选择的每页条数；localStorage 不可用时静默降级
const savedSectionSizes = (() => {
  try { return JSON.parse(window.localStorage?.getItem?.("ltl-task-page-sizes") || "{}"); } catch { return {}; }
})();
const sectionState = Object.fromEntries(Object.keys(PAGE_SIZES).map(key => [key, { page: 1, total: 0, size: savedSectionSizes[key] || PAGE_SIZES[key] }]));
function rememberSectionSizes() {
  try {
    window.localStorage?.setItem?.("ltl-task-page-sizes", JSON.stringify(Object.fromEntries(
      Object.entries(sectionState).map(([name, state]) => [name, state.size]))));
  } catch { /* 忽略存储失败 */ }
}
const claimFilters = { taskTitle: "", player: "", status: "" };
let claimFilterTimer = null;

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
// 分类下拉选项：空值 = 未分类
const categoryOptions = selectedId => `<option value="">未分类</option>${categories
  .map(category => `<option value="${category.id}" ${String(selectedId) === String(category.id) ? "selected" : ""}>${esc(category.name)}</option>`)
  .join("")}`;
const anonymousFee = (task, maxRewardRecipients) => Math.max(
  taskSettings.anonymousMinimumFee,
  Math.ceil((Number(task.pReward) || 0) * maxRewardRecipients * taskSettings.anonymousFeeRate / 100)
);
const publicationFee = (task, maxRewardRecipients) => Math.min(taskSettings.publicationFeeCap,
  Math.ceil((Number(task.pReward) || 0) * maxRewardRecipients * taskSettings.publicationFeeRate / 100));

function pendingTask(task) {
  const privacy = task.anonymous
    ? `<p class="task-review-comment"><strong>匿名发布：</strong>当前费率 ${taskSettings.anonymousFeeRate}%，最低 ${taskSettings.anonymousMinimumFee}P；按当前奖励人数预计收取 <strong data-anonymous-preview="${task.id}">${anonymousFee(task, 1)}P</strong>，此费用另收且不退。</p>`
    : "";
  return `<article class="panel task-card"><div class="task-card-heading"><div><span class="task-status">待审核</span><h3>${esc(task.title)}</h3><p class="muted">发布者：${esc(publisherLabel(task))} · ${time(task.createdAt)}</p></div><div class="task-rewards"><strong>${task.pReward}P</strong><strong>🪙 ${task.bountyReward}</strong></div></div><div class="task-requirements">${esc(task.requirements).replaceAll("\n", "<br>")}</div>${task.budgetNote ? `<p class="task-note">预算备注：${esc(task.budgetNote)}</p>` : ""}<p class="task-note">${task.repeatable ? "允许重复接取" : "每人限接一次"}</p><p class="task-review-comment">冻结额：每人P币奖励 × 最大奖励人数；发布费 <strong data-publication-preview="${task.id}">${publicationFee(task, 1)}P</strong>（10%，最多1000P）。</p>${privacy}<div class="task-form-grid task-admin-config"><label class="field"><span class="field-label">接取费用</span><input class="input" type="number" min="0" value="0" data-fee="${task.id}" /></label><label class="field"><span class="field-label">最大接取人数</span><input class="input" type="number" min="1" max="999" value="1" data-max="${task.id}" /></label><label class="field"><span class="field-label">最大奖励人数</span><input class="input" type="number" min="1" max="999" value="1" data-reward-max="${task.id}" /></label><label class="field"><span class="field-label">任务分类</span><select class="input" data-category="${task.id}">${categoryOptions(task.categoryId)}</select></label></div><div class="task-actions"><button class="btn primary" data-publish="${task.id}">通过并发布</button><button class="btn ghost" data-return-task="${task.id}">打回</button></div></article>`;
}

function proofCard(proof, taskMap) {
  const task = taskMap.get(proof.taskId);
  return `<article class="panel task-card"><div class="task-card-heading"><div><span class="task-status">证明待审核</span><h3>${esc(task?.title || `任务 #${proof.taskId}`)}</h3><p class="muted">提交者：${esc(proof.playerName)} · 第${proof.attemptNo}次提交 · ${time(proof.createdAt)}</p></div></div>${proof.description ? `<div class="task-requirements">${esc(proof.description)}</div>` : ""}<div class="task-proof-images">${(proof.images || []).map(image => `<a href="${esc(image.url)}" target="_blank" rel="noopener"><img src="${esc(image.url)}" alt="${esc(image.label)}" /></a>`).join("")}</div><div class="task-actions"><button class="btn primary" data-approve-proof="${proof.id}">确认完成并发奖</button><button class="btn ghost" data-return-proof="${proof.id}">打回证明</button></div></article>`;
}

function publishedTask(task) {
  const unfinished = (task.claims || []).filter(claim => unfinishedStatuses.includes(claim.status)).length;
  const pending = (task.claims || []).filter(claim => claim.status === "PROOF_PENDING").length;
  return `<article class="panel task-card"><div class="task-card-heading"><div>${task.pinned ? '<span class="task-pinned">[置顶]</span>' : ""}<span class="task-status">${task.official ? "官方任务" : "进行中"}</span><h3>${esc(task.title)}</h3><p class="muted">发布者：${esc(publisherLabel(task))} · ${time(task.publishedAt)}</p></div><div class="task-rewards"><strong>${task.pReward}P</strong><strong>🪙 ${task.bountyReward}</strong></div></div><div class="task-requirements">${esc(task.requirements).replaceAll("\n", "<br>")}</div>${task.budgetNote ? `<p class="task-note">备注：${esc(task.budgetNote)}</p>` : ""}<div class="task-facts"><span>当前接取 ${task.activeClaimCount}/${task.maxClaimants}</span><span>已奖励 ${task.completedCount}/${task.maxRewardRecipients}</span><span>${task.repeatable ? "可重复接取" : "每人限接一次"}</span><span>未完成 ${unfinished}</span><span>待审 ${pending}</span><span>剩余冻结 ${task.escrowRemaining}P</span>${task.categoryName ? `<span>分类：${esc(task.categoryName)}</span>` : ""}${!task.official ? `<span>发布费 ${task.publicationFeeAmount}P</span>` : ""}${task.anonymous ? `<span>匿名发布费 ${task.anonymousFeeAmount}P（费率快照 ${task.anonymousFeeRateSnapshot}%）</span>` : ""}</div><div class="task-actions"><label class="task-pin-option"><input type="checkbox" data-pin-task="${task.id}" ${task.pinned ? "checked" : ""} />置顶</label><label class="task-pin-option">分类<select class="input" style="width:auto;" data-task-category="${task.id}">${categoryOptions(task.categoryId)}</select></label><button class="btn" data-edit-published="${task.id}">编辑任务</button><button class="btn ghost" data-close-task="${task.id}" data-close-summary="未完成${unfinished}人、待审${pending}份、剩余冻结${task.escrowRemaining}P">注销任务</button></div></article>`;
}

function renderClaimHistory() {
  document.getElementById("claimHistoryList").innerHTML = claimHistory.map(claim => {
    const canCancel = claim.taskStatus === "PUBLISHED" && unfinishedStatuses.includes(claim.status);
    return `<article class="panel task-card"><div class="task-card-heading"><div><span class="task-status">${esc(claimStatusText[claim.status] || claim.status)}</span><h3>${esc(claim.taskTitle)} <small>(${esc(claim.taskSeason)})</small></h3><p class="muted">选手：${esc(claim.playerName)} (#${claim.playerId}) · 接取记录 #${claim.id}</p></div><div class="task-rewards"><strong>${claim.pReward}P</strong><strong>🪙 ${claim.bountyReward}</strong></div></div><div class="task-facts"><span>接取时间：${time(claim.claimedAt)}</span><span>接取费：${claim.feeAmount}P</span>${claim.completedAt ? `<span>完成：${time(claim.completedAt)}</span>` : ""}${claim.reclaimAvailableAt ? `<span>可再次接取：${time(claim.reclaimAvailableAt)}</span>` : ""}</div>${claim.adminCancelReason ? `<p class="task-note">管理员取消原因：${esc(claim.adminCancelReason)}；接取费已全额退还。</p>` : ""}${canCancel ? `<div class="task-actions"><button class="btn ghost" data-cancel-claim="${claim.id}" data-player-name="${esc(claim.playerName)}" data-fee-amount="${claim.feeAmount}">取消接取并退款</button></div>` : ""}</article>`;
  }).join("") || '<div class="panel task-empty">没有符合条件的接取记录。</div>';
}

function closedTask(task) {
  return `<article class="panel task-card"><div class="task-card-heading"><div><span class="task-status">${task.status === "CLOSED" ? "已注销" : "已放弃"}</span><h3>${esc(task.title)}</h3><p class="muted">发布者：${esc(publisherLabel(task))} · ${time(task.closedAt || task.createdAt)}</p></div></div>${task.closeReason ? `<p class="task-note">注销原因：${esc(task.closeReason)}</p>` : ""}<div class="task-facts"><span>完成 ${task.completedCount}</span><span>剩余冻结 ${task.escrowRemaining}P</span>${task.anonymousFeeAmount ? `<span>匿名发布费 ${task.anonymousFeeAmount}P（不退）</span>` : ""}</div></article>`;
}

function completionHistoryCard(claim) {
  const revoked = claim.status === "COMPLETION_REVOKED";
  const proof = (claim.proofs || []).find(item => ["APPROVED", "REVOKED"].includes(item.status));
  const images = proof?.images || [];
  const canRevoke = !revoked && claim.taskStatus === "PUBLISHED";
  const publisher = claim.taskOfficial
    ? "LTL官方"
    : `${claim.taskPublisherName ?? ""}${claim.taskAnonymous ? "（匿名）" : ""}`;
  return `<article class="panel task-card">
    <div class="task-card-heading">
      <div><span class="task-status">${revoked ? "完成已撤回" : "已完成"}</span><h3>${esc(claim.taskTitle)}</h3><p class="muted">发布者：${esc(publisher)} · 完成者：${esc(claim.playerName)} · 完成时间：${time(claim.completedAt)}</p></div>
      <div class="task-rewards"><strong>${claim.pReward}P</strong><strong>🪙 ${claim.bountyReward}</strong></div>
    </div>
    <div class="task-facts"><span>接取记录 #${claim.id}</span><span>接取费 ${claim.feeAmount}P（撤回不退）</span><span>任务状态：${claim.taskStatus === "PUBLISHED" ? "进行中" : "已结束"}</span></div>
    ${revoked ? `<p class="task-review-comment"><strong>撤回记录：</strong>${esc(claim.completionRevokeReason || "-")} · ${esc(claim.completionRevokedByName || "管理员")} · ${time(claim.completionRevokedAt)}</p>` : ""}
    <div class="task-proof-images">${images.map(image => `<a href="${esc(image.url)}" target="_blank" rel="noopener"><img src="${esc(image.url)}" alt="${esc(image.label)}" /></a>`).join("")}</div>
    ${canRevoke ? `<div class="task-actions"><button class="btn ghost" data-revoke-completion="${claim.id}" data-revoke-title="${esc(claim.taskTitle)}" data-revoke-player="${esc(claim.playerName)}" data-revoke-p="${claim.pReward}" data-revoke-bounty="${claim.bountyReward}">撤回完成并回退奖励</button></div>` : ""}
  </article>`;
}

let editingCategoryId = null;

function renderCategoryAdmin() {
  const list = document.getElementById("categoryAdminList");
  if (!list) return;
  if (!categories.length) {
    list.innerHTML = '<p class="muted" style="margin:.25rem 0 0;">暂无分类。添加后可在审核发布、官方任务发布或进行中任务上归类。</p>';
    return;
  }
  const row = category => String(editingCategoryId) === String(category.id)
    ? `<tr><td><input class="input" type="number" min="0" max="9999" value="${category.sortOrder ?? 0}" data-edit-sort="${category.id}" style="width:5rem;" /></td><td colspan="3"><input class="input" maxlength="50" value="${esc(category.name)}" data-edit-name="${category.id}" style="width:100%;" /></td><td style="white-space:nowrap;"><button class="btn primary" data-save-category="${category.id}">保存</button> <button class="btn ghost" type="button" data-cancel-edit-category>取消</button></td></tr>`
    : `<tr><td>${category.sortOrder ?? 0}</td><td><strong>${esc(category.name)}</strong></td><td>${category.taskCount ?? 0}</td><td>${category.publishedCount ?? 0}</td><td style="white-space:nowrap;"><button class="btn" data-rename-category="${category.id}">编辑</button> <button class="btn ghost" data-delete-category="${category.id}" data-name="${esc(category.name)}">删除</button></td></tr>`;
  list.innerHTML = `<table class="admin-table"><thead><tr><th>排序</th><th>分类名称</th><th>任务数</th><th>进行中</th><th>操作</th></tr></thead><tbody>${categories.map(row).join("")}</tbody></table>`;
}

// 各区块的分页查询参数
function sectionQuery(section) {
  const { page, size } = sectionState[section];
  const params = new URLSearchParams({ page, size });
  if (section === "pending") params.set("statuses", "PENDING_REVIEW");
  if (section === "published") params.set("statuses", "PUBLISHED");
  if (section === "closed") params.set("statuses", "CLOSED,ABANDONED");
  if (section === "completions") params.set("statuses", "COMPLETED,COMPLETION_REVOKED");
  if (section === "claims") {
    if (claimFilters.taskTitle) params.set("taskTitle", claimFilters.taskTitle);
    if (claimFilters.player) params.set("player", claimFilters.player);
    if (claimFilters.status) params.set("status", claimFilters.status);
  }
  return params.toString();
}

function pager(section) {
  const { page, total, size } = sectionState[section];
  const pages = Math.max(1, Math.ceil(total / size));
  // 总数不多的区块不渲染分页条；超过最小档位后提供每页条数选择
  if (total <= Math.min(...SIZE_OPTIONS)) return "";
  const sizeSelect = `<select class="input" data-page-size="${section}" style="width:auto;" aria-label="每页条数">${SIZE_OPTIONS
    .map(option => `<option value="${option}" ${option === size ? "selected" : ""}>${option} 条/页</option>`).join("")}</select>`;
  return `<div style="display:flex;gap:.5rem;align-items:center;margin-top:.5rem;">
    <button class="btn" data-page-section="${section}" data-page-delta="-1" ${page <= 1 ? "disabled" : ""}>上一页</button>
    <span class="muted">第 ${page} / ${pages} 页 · 共 ${total} 条</span>
    <button class="btn" data-page-section="${section}" data-page-delta="1" ${page >= pages ? "disabled" : ""}>下一页</button>
    ${sizeSelect}
  </div>`;
}

async function load() {
  // 分类接口很小，先加载并渲染，避免被较大的任务列表接口拖住分类下拉与分类管理
  categories = await request("/admin/event-task-categories").catch(() => []);
  renderCategoryAdmin();
  const officialCategory = document.getElementById("officialCategorySelect");
  if (officialCategory) officialCategory.innerHTML = categoryOptions(officialCategory.value);
  try {
    const [pendingPage, publishedPage, closedPage, proofs, claimsPage, completionsPage, settings] = await Promise.all([
      request(`/admin/event-tasks?${sectionQuery("pending")}`),
      request(`/admin/event-tasks?${sectionQuery("published")}`),
      request(`/admin/event-tasks?${sectionQuery("closed")}`),
      request("/admin/event-task-proofs/pending"),
      request(`/admin/event-task-claims?${sectionQuery("claims")}`),
      request(`/admin/event-task-claims?${sectionQuery("completions")}`),
      request("/event-tasks/settings")
    ]);
    taskSettings = { ...taskSettings, ...settings };
    const tasks = [...pendingPage.list, ...publishedPage.list, ...closedPage.list];
    taskById = new Map(tasks.map(task => [task.id, task]));
    sectionState.pending.total = pendingPage.total;
    sectionState.published.total = publishedPage.total;
    sectionState.closed.total = closedPage.total;
    sectionState.claims.total = claimsPage.total;
    sectionState.completions.total = completionsPage.total;
    claimHistory = claimsPage.list;
    completionHistory = completionsPage.list;
    document.getElementById("pendingTaskList").innerHTML = (pendingPage.list.map(pendingTask).join("") || '<div class="panel task-empty">暂无待审核任务。</div>') + pager("pending");
    document.getElementById("pendingProofList").innerHTML = proofs.map(proof => proofCard(proof, taskById)).join("") || '<div class="panel task-empty">暂无待审核证明。</div>';
    document.getElementById("publishedTaskList").innerHTML = (publishedPage.list.map(publishedTask).join("") || '<div class="panel task-empty">暂无进行中任务。</div>') + pager("published");
    document.getElementById("completedTaskList").innerHTML = (completionHistory.map(completionHistoryCard).join("") || '<div class="panel task-empty">暂无完成记录。</div>') + pager("completions");
    document.getElementById("closedTaskList").innerHTML = (closedPage.list.map(closedTask).join("") || '<div class="panel task-empty">暂无已结束任务。</div>') + pager("closed");
    renderClaimHistory();
    document.getElementById("claimHistoryList").innerHTML = document.getElementById("claimHistoryList").innerHTML + pager("claims");
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
  // 分区翻页
  const pagerButton = event.target.closest("[data-page-section]");
  if (pagerButton) {
    const section = pagerButton.dataset.pageSection;
    const pages = Math.max(1, Math.ceil(sectionState[section].total / sectionState[section].size));
    const next = Math.min(Math.max(1, sectionState[section].page + Number(pagerButton.dataset.pageDelta)), pages);
    if (next !== sectionState[section].page) {
      sectionState[section].page = next;
      await load();
    }
    return;
  }
  const editPublished = event.target.closest("[data-edit-published]");
  if (editPublished) {
    const task = taskById.get(Number(editPublished.dataset.editPublished));
    const form = document.getElementById("adminTaskEditForm");
    for (const field of ["title", "requirements", "pReward", "bountyReward", "maxClaimants", "maxRewardRecipients", "budgetNote"]) form.elements[field].value = task[field] ?? "";
    form.elements.maxClaimants.min = Math.max(1, task.activeClaimCount);
    form.elements.maxRewardRecipients.min = Math.max(1, task.completedCount);
    form.elements.repeatable.checked = Boolean(task.repeatable);
    form.elements.categoryId.innerHTML = categoryOptions(task.categoryId);
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
    const maxRewardRecipients = Number(document.querySelector(`[data-reward-max="${id}"]`).value);
    const categoryId = document.querySelector(`[data-category="${id}"]`)?.value || null;
    const task = taskById.get(Number(id));
    const privacyText = task?.anonymous ? `，另收匿名发布费 ${anonymousFee(task, maxRewardRecipients)}P` : "";
    const categoryText = categoryId ? `，归类为「${categories.find(category => String(category.id) === String(categoryId))?.name || categoryId}」` : "";
    if (!confirm(`确认以接取费 ${claimFee}P、同时最多 ${maxClaimants} 人、最多奖励 ${maxRewardRecipients} 人发布？将冻结 ${task.pReward * maxRewardRecipients}P，并收取发布费 ${publicationFee(task, maxRewardRecipients)}P${privacyText}${categoryText}。`)) return;
    await act(() => request(`/admin/event-tasks/${id}/publish`, json({ claimFee, maxClaimants, maxRewardRecipients, categoryId })), "任务已发布");
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
  // 分类编辑：点击「编辑」进入行内编辑，排序和名称一次改完
  const renameCategory = event.target.closest("[data-rename-category]");
  if (renameCategory) {
    editingCategoryId = renameCategory.dataset.renameCategory;
    renderCategoryAdmin();
    document.querySelector(`[data-edit-name="${editingCategoryId}"]`)?.focus();
    return;
  }
  const cancelEditCategory = event.target.closest("[data-cancel-edit-category]");
  if (cancelEditCategory) {
    editingCategoryId = null;
    renderCategoryAdmin();
    return;
  }
  const saveCategory = event.target.closest("[data-save-category]");
  if (saveCategory) {
    const id = saveCategory.dataset.saveCategory;
    const name = document.querySelector(`[data-edit-name="${id}"]`)?.value.trim();
    if (!name) { setMessage("请填写分类名称", true); return; }
    const sortOrder = Number(document.querySelector(`[data-edit-sort="${id}"]`)?.value || 0);
    editingCategoryId = null;
    await act(() => request(`/admin/event-task-categories/${id}`, json({ name, sortOrder }, "PUT")), "分类已更新");
    return;
  }
  const deleteCategory = event.target.closest("[data-delete-category]");
  if (deleteCategory) {
    const name = deleteCategory.dataset.name;
    if (confirm(`确认删除分类「${name}」？该分类下的任务将自动归为未分类。`)) {
      await act(() => request(`/admin/event-task-categories/${deleteCategory.dataset.deleteCategory}`, { method: "DELETE" }), "分类已删除");
    }
  }
});

document.addEventListener("change", async event => {
  // 切换每页条数：回到第一页并记住选择
  const sizeSelect = event.target.closest("[data-page-size]");
  if (sizeSelect) {
    const section = sizeSelect.dataset.pageSize;
    const size = Number(sizeSelect.value);
    if (SIZE_OPTIONS.includes(size) && size !== sectionState[section]?.size) {
      sectionState[section].size = size;
      sectionState[section].page = 1;
      rememberSectionSizes();
      await load();
    }
    return;
  }
  const checkbox = event.target.closest("[data-pin-task]");
  if (checkbox) {
    checkbox.disabled = true;
    try {
      await request(`/admin/event-tasks/${checkbox.dataset.pinTask}/pin`, json({ pinned: checkbox.checked }, "PUT"));
      setMessage(checkbox.checked ? "任务已置顶" : "已取消置顶");
      await load();
    } catch (error) {
      checkbox.checked = !checkbox.checked;
      checkbox.disabled = false;
      setMessage(error.message, true);
    }
    return;
  }
  // 进行中任务：切换所属分类（未分类 = 清除归类）
  const categorySelect = event.target.closest("[data-task-category]");
  if (categorySelect) {
    categorySelect.disabled = true;
    const categoryId = categorySelect.value || null;
    try {
      await request(`/admin/event-tasks/${categorySelect.dataset.taskCategory}/category`, json({ categoryId }, "PUT"));
      setMessage(categoryId ? `已归类为「${categories.find(category => String(category.id) === String(categoryId))?.name || categoryId}」` : "已归为未分类");
      await load();
    } catch (error) {
      categorySelect.disabled = false;
      setMessage(error.message, true);
    }
  }
});

document.addEventListener("input", event => {
  const input = event.target.closest("[data-reward-max]");
  if (!input) return;
  const task = taskById.get(Number(input.dataset.rewardMax));
  const privacyPreview = document.querySelector(`[data-anonymous-preview="${input.dataset.rewardMax}"]`);
  const feePreview = document.querySelector(`[data-publication-preview="${input.dataset.rewardMax}"]`);
  const maxRewardRecipients = Number(input.value);
  if (task && Number.isFinite(maxRewardRecipients) && maxRewardRecipients > 0) {
    if (privacyPreview) privacyPreview.textContent = `${anonymousFee(task, maxRewardRecipients)}P`;
    if (feePreview) feePreview.textContent = `${publicationFee(task, maxRewardRecipients)}P`;
  }
});

// 接取记录筛选：任务名/选手/状态全部走服务端，配合分页
function refetchClaims() {
  sectionState.claims.page = 1;
  claimFilters.taskTitle = document.getElementById("claimTaskFilter").value.trim();
  claimFilters.player = document.getElementById("claimPlayerFilter").value.trim();
  claimFilters.status = document.getElementById("claimStatusFilter").value;
  return load();
}

for (const id of ["claimTaskFilter", "claimPlayerFilter", "claimStatusFilter"]) {
  const filterInput = document.getElementById(id);
  filterInput.addEventListener("change", async () => {
    clearTimeout(claimFilterTimer);
    await refetchClaims();
  });
  filterInput.addEventListener("input", () => {
    clearTimeout(claimFilterTimer);
    claimFilterTimer = setTimeout(refetchClaims, 400);
  });
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
    maxClaimants: Number(form.elements.maxClaimants.value),
    maxRewardRecipients: Number(form.elements.maxRewardRecipients.value),
    budgetNote: form.elements.budgetNote.value.trim(),
    repeatable: form.elements.repeatable.checked,
    categoryId: form.elements.categoryId?.value || null
  };
  if (payload.pReward + payload.bountyReward <= 0) { setMessage("P币和赏金积分至少一项大于0", true); return; }
  const delta = payload.pReward * (payload.maxRewardRecipients - task.completedCount) - task.escrowRemaining;
  const fundMessage = task.official ? "官方任务由系统发奖。" : delta > 0 ? `将从发布者余额追加冻结 ${delta}P。` : delta < 0 ? `将向发布者退还 ${-delta}P冻结额。` : "冻结额不变。";
  const categoryText = payload.categoryId ? `，归类为「${categories.find(category => String(category.id) === String(payload.categoryId))?.name || payload.categoryId}」` : "";
  if (!confirm(`确认修改“${task.title}”？同时最多 ${payload.maxClaimants} 人接取，最多奖励 ${payload.maxRewardRecipients} 人${categoryText}。未完成与未来接取将使用新内容和奖励；已完成记录不变。${fundMessage}发布费和匿名费不重算。`)) return;
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
    maxClaimants: Number(data.get("maxClaimants") || 0),
    maxRewardRecipients: Number(data.get("maxRewardRecipients") || 0),
    repeatable: data.get("repeatable") === "on",
    categoryId: data.get("categoryId") ? Number(data.get("categoryId")) : null
  };
  if (!confirm("确认无成本直接发布该官方任务？")) return;
  await act(() => request("/admin/event-tasks/official", json(payload)), "官方任务已发布");
  event.target.reset();
  const officialCategory = document.getElementById("officialCategorySelect");
  if (officialCategory) officialCategory.innerHTML = categoryOptions("");
});

document.getElementById("addCategoryForm").addEventListener("submit", async event => {
  event.preventDefault();
  const form = event.target;
  const payload = {
    name: form.elements.name.value.trim(),
    sortOrder: Number(form.elements.sortOrder.value || 0)
  };
  if (!payload.name) { setMessage("请填写分类名称", true); return; }
  await act(() => request("/admin/event-task-categories", json(payload)), "分类已添加");
  form.reset();
});

await load();
