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
    "adminTaskEditForm", "cancelAdminTaskEdit"
  ]);
  const form = doc.elements.adminTaskEditForm;
  form.elements = Object.fromEntries(["taskId", "title", "requirements", "pReward", "bountyReward", "budgetNote"]
    .map(name => [name, { value: "" }]));
  const task = {
    id: 10, season: "s2", status: "PUBLISHED", title: "原任务", requirements: "原要求",
    budgetNote: "", publisherName: "发布者", official: false, anonymous: false,
    pReward: 100, bountyReward: 20, claimFee: 50, maxClaimants: 3,
    claimedCount: 1, completedCount: 0, escrowRemaining: 300, claims: []
  };
  let claims = [
    { id: 88, taskId: 10, taskTitle: "原任务", taskSeason: "s2", playerId: 2, playerName: "甲",
      status: "CLAIMED", feeAmount: 50, pReward: 100, bountyReward: 20, claimedAt: "2026-09-20T12:00:00" },
    { id: 89, taskId: 10, taskTitle: "原任务", taskSeason: "s2", playerId: 3, playerName: "乙",
      status: "COMPLETED", feeAmount: 50, pReward: 100, bountyReward: 20, claimedAt: "2026-09-19T12:00:00" }
  ];
  const writes = [];
  globalThis.document = doc;
  globalThis.window = { location: { hostname: "example.test" }, localStorage: { getItem: () => null } };
  globalThis.prompt = () => "管理员调整";
  globalThis.confirm = () => true;
  globalThis.fetch = async (url, options = {}) => {
    if (options.method && options.method !== "GET") writes.push({ url, options });
    if (url.endsWith("/admin/event-tasks/10/pin")) {
      task.pinned = JSON.parse(options.body).pinned;
      return response(task);
    }
    if (url.endsWith("/admin/event-tasks") || url.endsWith("/admin/event-tasks/10")) return response([task]);
    if (url.endsWith("/admin/event-task-proofs/pending")) return response([]);
    if (url.endsWith("/event-tasks/settings")) return response({ anonymousMinimumFee: 50, anonymousFeeRate: 10 });
    if (url.endsWith("/admin/event-task-claims")) return response(claims);
    if (url.endsWith("/admin/event-task-claims/88/cancel")) {
      claims = [{ ...claims[0], status: "ADMIN_CANCELLED", adminCancelReason: "管理员调整" }, claims[1]];
      return response(claims[0]);
    }
    return response(task);
  };

  await import(`../src/admin/event-tasks.js?ui=${Date.now()}`);
  assert.match(doc.elements.publishedTaskList.innerHTML, /data-edit-published="10"/);
  assert.match(doc.elements.publishedTaskList.innerHTML, /data-pin-task="10"/);
  assert.match(doc.elements.claimHistoryList.innerHTML, /选手：甲/);
  assert.match(doc.elements.claimHistoryList.innerHTML, /data-cancel-claim="88"/);
  doc.elements.claimStatusFilter.value = "COMPLETED";
  doc.elements.claimStatusFilter.listeners.change();
  assert.doesNotMatch(doc.elements.claimHistoryList.innerHTML, /选手：甲/);
  assert.match(doc.elements.claimHistoryList.innerHTML, /选手：乙/);
  doc.elements.claimStatusFilter.value = "";

  await doc.listeners.click({ target: { closest: selector => selector === "[data-edit-published]"
    ? { dataset: { editPublished: "10" } } : null } });
  assert.equal(doc.elements.adminTaskEditDialog.open, true);
  form.elements.title.value = "新任务";
  form.elements.requirements.value = "新要求";
  form.elements.pReward.value = "150";
  form.elements.bountyReward.value = "30";
  await form.listeners.submit({ preventDefault() {}, target: form });
  assert.equal(doc.elements.adminTaskEditDialog.open, false);
  const edit = writes.find(write => write.options.method === "PUT");
  assert.equal(edit.url, "/api/admin/event-tasks/10");
  assert.equal(JSON.parse(edit.options.body).pReward, 150);

  const pinCheckbox = { dataset: { pinTask: "10" }, checked: true, disabled: false };
  await doc.listeners.change({ target: { closest: selector => selector === "[data-pin-task]" ? pinCheckbox : null } });
  assert.ok(writes.some(write => write.url === "/api/admin/event-tasks/10/pin" && JSON.parse(write.options.body).pinned));
  assert.match(doc.elements.publishedTaskList.innerHTML, /\[置顶\]/);

  await doc.listeners.click({ target: { closest: selector => selector === "[data-cancel-claim]"
    ? { dataset: { cancelClaim: "88", playerName: "甲", feeAmount: "50" } } : null } });
  assert.ok(writes.some(write => write.url === "/api/admin/event-task-claims/88/cancel"));
  assert.match(doc.elements.claimHistoryList.innerHTML, /管理员已取消/);
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
    if (url.endsWith("/event-tasks")) return response([task]);
    return response([]);
  };

  await import(`../src/event-tasks.js?ui=${Date.now()}`);
  assert.match(doc.elements.taskContent.innerHTML, /冷却中，可重新接取时间/);
  assert.match(doc.elements.taskContent.innerHTML, /新要求/);
  assert.match(doc.elements.taskContent.innerHTML, /\[置顶\]/);
});
