import test from "node:test";
import assert from "node:assert/strict";
import { mountBatchAdjustment } from "../src/admin/batch-player-adjustment.js";

test("batch selection survives search and submits once with explicit confirmation", async () => {
  const fields = Object.fromEntries(["search", "players", "count", "all", "clear", "amount", "reason", "submit", "feedback"].map(key => [key, {
    value: "", textContent: "", innerHTML: "", listeners: {},
    addEventListener(type, listener) { this.listeners[type] = listener; }
  }]));
  const panel = { style: {}, querySelector: selector => fields[selector.slice(6, -1)], querySelectorAll: () => Object.values(fields) };
  const originalDocument = globalThis.document;
  const originalConfirm = globalThis.confirm;
  globalThis.document = { createElement: () => panel };
  let confirmation;
  globalThis.confirm = value => { confirmation = value; return true; };
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  const writes = [];
  let refreshed = 0;
  try {
    mountBatchAdjustment({ anchor: { after() {} }, currency: "bounty", getPlayers: () => [
      { id: 1, name: "甲", bounty: 5 }, { id: 2, name: "乙", bounty: 10 }
    ], request: async (url, options) => { writes.push({ url, body: JSON.parse(options.body) }); await pending; return [{ id: 1, name: "甲", bounty: 25 }, { id: 2, name: "乙", bounty: 30 }]; }, onSuccess: async () => { refreshed++; } });
    fields.players.listeners.change({ target: { value: "1", checked: true } });
    fields.search.value = "乙";
    fields.search.listeners.input();
    fields.all.listeners.click();
    assert.equal(fields.count.textContent, "已选择 2 人");
    fields.amount.value = "20"; fields.reason.value = " 活动奖励 ";
    const first = fields.submit.listeners.click();
    await fields.submit.listeners.click();
    assert.equal(writes.length, 1);
    assert.deepEqual(writes[0].body, { playerIds: [1, 2], amount: 20, reason: "活动奖励" });
    assert.match(confirmation, /共 2 人/);
    assert.match(confirmation, /甲：5 → 25/);
    release(); await first;
    assert.equal(refreshed, 1);
    assert.match(fields.feedback.textContent, /成功调整 2 名/);
    assert.equal(fields.count.textContent, "已选择 0 人");
    fields.amount.value = "1.5";
    await fields.submit.listeners.click();
    assert.equal(writes.length, 1);
  } finally { globalThis.document = originalDocument; globalThis.confirm = originalConfirm; }
});
