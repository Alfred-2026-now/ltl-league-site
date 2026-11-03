import test from "node:test";
import assert from "node:assert/strict";

function element() {
  return {
    innerHTML: "", textContent: "", value: "", listeners: {},
    classList: { toggle() {} },
    addEventListener(type, listener) { this.listeners[type] = listener; },
    showModal() { this.open = true; },
    close() { this.open = false; }
  };
}

function fakeDocument(ids) {
  const elements = Object.fromEntries(ids.map(id => [id, element()]));
  const listeners = {};
  return {
    elements, listeners,
    getElementById(id) { return elements[id]; },
    addEventListener(type, listener) { listeners[type] = listener; },
    querySelector(selector) { return this.inputs?.[selector] ?? null; },
    querySelectorAll() { return []; }
  };
}

function response(data) {
  return { ok: true, json: async () => ({ code: 200, data }) };
}

test("admin task page exposes edit, cancellation, and claim filters", async () => {
  const doc = fakeDocument([
    "adminTaskMessage", "pendingTaskList", "pendingProofList", "publishedTaskList",
    "completedTaskList", "closedTaskList", "claimHistoryList", "claimTaskFilter",
    "claimPlayerFilter", "claimStatusFilter", "officialTaskForm", "adminTaskEditDialog",
    "adminTaskEditForm", "cancelAdminTaskEdit", "addCategoryForm", "categoryAdminList",
    "officialCategorySelect"
  ]);
  const form = doc.elements.adminTaskEditForm;
  form.elements = Object.fromEntries(["taskId", "title", "requirements", "pReward", "bountyReward", "maxClaimants", "maxRewardRecipients", "budgetNote", "repeatable", "categoryId"]
    .map(name => [name, { value: "" }]));
  const task = {
    id: 10, season: "s2", status: "PUBLISHED", title: "原任务", requirements: "原要求",
    budgetNote: "", publisherName: "发布者", official: false, anonymous: false,
    pReward: 100, bountyReward: 20, claimFee: 50, maxClaimants: 3, maxRewardRecipients: 3,
    claimedCount: 2, activeClaimCount: 1, completedCount: 1, escrowRemaining: 200, claims: []
  };
  let claims = [
    { id: 88, taskId: 10, taskTitle: "原任务", taskSeason: "s2", playerId: 2, playerName: "甲",
      taskStatus: "PUBLISHED", taskOfficial: false, taskPublisherName: "发布者", taskAnonymous: false,
      status: "CLAIMED", feeAmount: 50, pReward: 100, bountyReward: 20, claimedAt: "2026-09-20T12:00:00" },
    { id: 89, taskId: 10, taskTitle: "原任务", taskSeason: "s2", playerId: 3, playerName: "乙",
      taskStatus: "PUBLISHED", taskOfficial: false, taskPublisherName: "发布者", taskAnonymous: false,
      status: "COMPLETED", feeAmount: 50, pReward: 100, bountyReward: 20, claimedAt: "2026-09-19T12:00:00" }
  ];
  const writes = [];
  const confirmations = [];
  globalThis.document = doc;
  globalThis.window = { location: { hostname: "example.test" }, localStorage: { getItem: () => null } };
  globalThis.prompt = () => "管理员调整";
  globalThis.confirm = text => { confirmations.push(text); return true; };
  globalThis.fetch = async (url, options = {}) => {
    if (options.method && options.method !== "GET") writes.push({ url, options });
    if (url.endsWith("/admin/event-tasks/10/pin")) {
      task.pinned = JSON.parse(options.body).pinned;
      return response(task);
    }
    if (url.endsWith("/admin/event-tasks/10") && options.method === "PUT") {
      Object.assign(task, JSON.parse(options.body));
      task.escrowRemaining = task.pReward * (task.maxRewardRecipients - task.completedCount);
      return response(task);
    }
    if (url.includes("/admin/event-tasks?")) {
      const statuses = new URL(url, "https://example.test").searchParams.get("statuses") || "";
      const list = statuses.includes("PUBLISHED") ? [task] : [];
      return response({ list, total: list.length, page: 1, size: 20 });
    }
    if (url.endsWith("/admin/event-task-categories")) return response([
      { id: 1, name: "官方任务", sortOrder: 0, publishedCount: 1, taskCount: 2, createdAt: "2026-11-03T23:23:20" }
    ]);
    if (url.endsWith("/admin/event-task-proofs/pending")) return response([]);
    if (url.endsWith("/event-tasks/settings")) return response({ anonymousMinimumFee: 50, anonymousFeeRate: 10 });
    if (url.includes("/admin/event-task-claims?")) {
      const params = new URL(url, "https://example.test").searchParams;
      if ((params.get("statuses") || "").includes("COMPLETED")) {
        return response({ list: [], total: 0, page: 1, size: 15 });
      }
      const status = params.get("status");
      const list = status ? claims.filter(claim => claim.status === status) : claims;
      return response({ list, total: list.length, page: 1, size: 15 });
    }
    if (url.endsWith("/admin/event-task-claims/88/cancel")) {
      claims = [{ ...claims[0], status: "ADMIN_CANCELLED", adminCancelReason: "管理员调整" }, claims[1]];
      return response(claims[0]);
    }
    return response(task);
  };

  await import(`../src/admin/event-tasks.js?ui=${Date.now()}`);
  assert.match(doc.elements.officialCategorySelect.innerHTML, /<option value="1"\s*>官方任务<\/option>/);
  assert.match(doc.elements.categoryAdminList.innerHTML, /官方任务/);
  assert.match(doc.elements.categoryAdminList.innerHTML, /data-delete-category="1"/);
  assert.match(doc.elements.publishedTaskList.innerHTML, /data-edit-published="10"/);
  assert.match(doc.elements.publishedTaskList.innerHTML, /data-pin-task="10"/);
  assert.match(doc.elements.claimHistoryList.innerHTML, /选手：甲/);
  assert.match(doc.elements.claimHistoryList.innerHTML, /data-cancel-claim="88"/);
  doc.elements.claimStatusFilter.value = "COMPLETED";
  await doc.elements.claimStatusFilter.listeners.change();
  assert.doesNotMatch(doc.elements.claimHistoryList.innerHTML, /选手：甲/);
  assert.match(doc.elements.claimHistoryList.innerHTML, /选手：乙/);
  doc.elements.claimStatusFilter.value = "";
  await doc.elements.claimStatusFilter.listeners.change();

  await doc.listeners.click({ target: { closest: selector => selector === "[data-edit-published]"
    ? { dataset: { editPublished: "10" } } : null } });
  assert.equal(doc.elements.adminTaskEditDialog.open, true);
  assert.equal(form.elements.maxClaimants.value, 3);
  assert.equal(form.elements.maxRewardRecipients.value, 3);
  assert.equal(form.elements.maxClaimants.min, 1);
  assert.equal(form.elements.maxRewardRecipients.min, 1);
  form.elements.title.value = "新任务";
  form.elements.requirements.value = "新要求";
  form.elements.pReward.value = "150";
  form.elements.bountyReward.value = "30";
  form.elements.maxClaimants.value = "5";
  form.elements.maxRewardRecipients.value = "4";
  await form.listeners.submit({ preventDefault() {}, target: form });
  assert.equal(doc.elements.adminTaskEditDialog.open, false);
  const edit = writes.find(write => write.options.method === "PUT");
  assert.equal(edit.url, "/api/admin/event-tasks/10");
  assert.equal(JSON.parse(edit.options.body).pReward, 150);
  assert.equal(JSON.parse(edit.options.body).maxClaimants, 5);
  assert.equal(JSON.parse(edit.options.body).maxRewardRecipients, 4);
  assert.match(confirmations.at(-1), /追加冻结 250P/);
  assert.match(doc.elements.publishedTaskList.innerHTML, /当前接取 1\/5/);
  assert.match(doc.elements.publishedTaskList.innerHTML, /已奖励 1\/4/);

  await doc.listeners.click({ target: { closest: selector => selector === "[data-edit-published]"
    ? { dataset: { editPublished: "10" } } : null } });
  form.elements.maxClaimants.value = "1";
  form.elements.maxRewardRecipients.value = "2";
  await form.listeners.submit({ preventDefault() {}, target: form });
  assert.match(confirmations.at(-1), /退还 300P冻结额/);
  assert.equal(task.maxClaimants, 1);
  assert.equal(task.maxRewardRecipients, 2);

  const pinCheckbox = { dataset: { pinTask: "10" }, checked: true, disabled: false };
  await doc.listeners.change({ target: { closest: selector => selector === "[data-pin-task]" ? pinCheckbox : null } });
  assert.ok(writes.some(write => write.url === "/api/admin/event-tasks/10/pin" && JSON.parse(write.options.body).pinned));
  assert.match(doc.elements.publishedTaskList.innerHTML, /\[置顶\]/);

  await doc.listeners.click({ target: { closest: selector => selector === "[data-cancel-claim]"
    ? { dataset: { cancelClaim: "88", playerName: "甲", feeAmount: "50" } } : null } });
  assert.ok(writes.some(write => write.url === "/api/admin/event-task-claims/88/cancel"));
  assert.match(doc.elements.claimHistoryList.innerHTML, /管理员已取消/);

  await doc.listeners.click({ target: { closest: selector => selector === "[data-rename-category]"
    ? { dataset: { renameCategory: "1" } } : null } });
  assert.match(doc.elements.categoryAdminList.innerHTML, /data-edit-name="1"/);
  doc.inputs = {
    '[data-edit-name="1"]': { value: "官方任务改" },
    '[data-edit-sort="1"]': { value: "2" }
  };
  await doc.listeners.click({ target: { closest: selector => selector === "[data-save-category]"
    ? { dataset: { saveCategory: "1" } } : null } });
  const categoryWrite = writes.find(write => write.url === "/api/admin/event-task-categories/1");
  assert.ok(categoryWrite);
  assert.deepEqual(JSON.parse(categoryWrite.options.body), { name: "官方任务改", sortOrder: 2 });
});

test("player task hall shows the administrator cancellation cooldown", async () => {
  const doc = fakeDocument(["taskContent", "taskMessage", "taskEditDialog", "taskEditForm", "saveTaskEditBtn"]);
  const task = {
    id: 10, status: "PUBLISHED", title: "任务", requirements: "新要求", publisherName: "发布者",
    official: false, anonymous: false, pinned: true, pReward: 150, bountyReward: 30, claimFee: 50,
    claimedCount: 0, maxClaimants: 3, remainingSlots: 3, completedCount: 0,
    viewerClaimStatus: "ADMIN_CANCELLED", viewerReclaimAvailableAt: "2099-01-01T13:00:00", canClaim: false
  };
  globalThis.document = doc;
  globalThis.window = { location: { hostname: "example.test" }, localStorage: { getItem: () => null } };
  globalThis.fetch = async url => {
    if (url.endsWith("/auth/current")) return response({ id: 2, name: "甲" });
    if (url.endsWith("/event-tasks/settings")) return response({ anonymousMinimumFee: 50, anonymousFeeRate: 10 });
    if (url.includes("/event-tasks?")) return response({ list: [task], total: 1, page: 1, size: 12 });
    return response([]);
  };

  await import(`../src/event-tasks.js?ui=${Date.now()}`);
  assert.match(doc.elements.taskContent.innerHTML, /冷却中，可重新接取时间/);
  assert.match(doc.elements.taskContent.innerHTML, /新要求/);
  assert.match(doc.elements.taskContent.innerHTML, /\[置顶\]/);
});

test("task hall allows claiming expanded slots without reserving reward places", async () => {
  const doc = fakeDocument(["taskContent", "taskMessage", "taskEditDialog", "taskEditForm", "saveTaskEditBtn"]);
  const task = {
    id: 38, status: "PUBLISHED", title: "木已成舟", requirements: "完成指定目标",
    publisherName: "发布者", pReward: 1200, bountyReward: 0, claimFee: 0,
    activeClaimCount: 1, claimedCount: 1, maxClaimants: 10, maxRewardRecipients: 1,
    completedCount: 0, remainingSlots: 9, canClaim: true
  };
  const writes = [];
  const confirmations = [];
  globalThis.document = doc;
  globalThis.window = { location: { hostname: "example.test" }, localStorage: { getItem: () => null } };
  globalThis.confirm = text => { confirmations.push(text); return true; };
  globalThis.fetch = async (url, options = {}) => {
    if (options.method === "POST") writes.push(url);
    if (url.endsWith("/auth/current")) return response({ id: 2, name: "另一位选手" });
    if (url.endsWith("/event-tasks/settings")) return response({});
    if (url.includes("/event-tasks?")) return response({ list: [task], total: 1, page: 1, size: 12 });
    return response({});
  };

  await import(`../src/event-tasks.js?expanded=${Date.now()}`);
  assert.match(doc.elements.taskContent.innerHTML, /当前接取：1 \/ 10/);
  assert.match(doc.elements.taskContent.innerHTML, /剩余：9/);
  assert.match(doc.elements.taskContent.innerHTML, /已奖励：0 \/ 1/);
  assert.match(doc.elements.taskContent.innerHTML, /data-claim-task="38"/);
  assert.match(doc.elements.taskContent.innerHTML, /接取不预留奖励名额/);
  await doc.listeners.click({ target: { closest: selector => selector === "[data-claim-task]"
    ? { dataset: { claimTask: "38", claimFee: "0" } } : null } });
  assert.deepEqual(writes, ["/api/event-tasks/38/claims"]);
  assert.match(confirmations[0], /接取不预留奖励名额/);
});

test("admin publish sends separate claim and reward limits", async () => {
  const doc = fakeDocument([
    "adminTaskMessage", "pendingTaskList", "pendingProofList", "publishedTaskList",
    "completedTaskList", "closedTaskList", "claimHistoryList", "claimTaskFilter",
    "claimPlayerFilter", "claimStatusFilter", "officialTaskForm", "adminTaskEditDialog",
    "adminTaskEditForm", "cancelAdminTaskEdit", "addCategoryForm", "categoryAdminList",
    "officialCategorySelect"
  ]);
  doc.inputs = {
    '[data-fee="10"]': { value: "5" },
    '[data-max="10"]': { value: "999" },
    '[data-reward-max="10"]': { value: "3" },
    '[data-category="10"]': { value: "" }
  };
  const task = {
    id: 10, status: "PENDING_REVIEW", title: "长期任务", requirements: "多次完成",
    publisherName: "发布者", pReward: 100, bountyReward: 0, repeatable: true
  };
  const writes = [];
  globalThis.document = doc;
  globalThis.window = { location: { hostname: "example.test" }, localStorage: { getItem: () => null } };
  globalThis.confirm = () => true;
  globalThis.fetch = async (url, options = {}) => {
    if (options.method === "POST") writes.push({ url, body: JSON.parse(options.body) });
    if (url.includes("/admin/event-tasks?")) {
      const statuses = new URL(url, "https://example.test").searchParams.get("statuses") || "";
      const list = statuses.includes("PENDING_REVIEW") ? [task] : [];
      return response({ list, total: list.length, page: 1, size: 20 });
    }
    if (url.endsWith("/event-tasks/settings")) return response({ anonymousMinimumFee: 50, anonymousFeeRate: 10 });
    if (url.endsWith("/admin/event-task-categories")) return response([]);
    return response({ list: [], total: 0, page: 1, size: 15 });
  };

  await import(`../src/admin/event-tasks.js?publish=${Date.now()}`);
  assert.match(doc.elements.pendingTaskList.innerHTML, /data-reward-max="10"/);
  assert.match(doc.elements.pendingTaskList.innerHTML, /max="999"/);
  await doc.listeners.click({ target: { closest: selector => selector === "[data-publish]"
    ? { dataset: { publish: "10" } } : null } });
  assert.deepEqual(writes[0].body, { claimFee: 5, maxClaimants: 999, maxRewardRecipients: 3, categoryId: null });
});
