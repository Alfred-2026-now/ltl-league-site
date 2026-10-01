export function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));
}

export function mountBatchAdjustment({ anchor, currency, request, getPlayers, onSuccess }) {
  const label = currency === "bounty" ? "赏金" : "积分";
  const balanceKey = currency === "bounty" ? "bounty" : "deposit";
  const panel = document.createElement("div");
  panel.className = "panel";
  panel.style.cssText = "padding:1rem;margin-top:1rem";
  panel.innerHTML = `<h3>批量调整${label}</h3>
    <label class="field"><span class="field-label">搜索选手</span><input class="input" data-search placeholder="输入选手姓名" /></label>
    <div style="display:flex;gap:.5rem;margin:.75rem 0"><button class="btn" data-all type="button">全选搜索结果</button><button class="btn" data-clear type="button">清空选择</button><span data-count aria-live="polite"></span></div>
    <div data-players style="max-height:240px;overflow:auto;display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:.5rem"></div>
    <div class="admin-form-grid" style="display:flex;flex-wrap:wrap;gap:.75rem;margin-top:1rem">
      <label class="field"><span class="field-label">每人调整金额（正增负减）</span><input class="input" data-amount type="number" step="1" /></label>
      <label class="field"><span class="field-label">原因（必填）</span><input class="input" data-reason maxlength="200" /></label>
      <button class="btn primary" data-submit type="button">核对并提交</button>
    </div><p data-feedback role="status" style="white-space:pre-wrap"></p>`;
  anchor.after(panel);
  const find = selector => panel.querySelector(selector);
  const selected = new Set();
  let busy = false;
  const filtered = () => getPlayers().filter(p => (p.name || "").includes(find("[data-search]").value.trim()));
  function render() {
    const valid = new Set(getPlayers().map(p => String(p.id)));
    for (const id of selected) if (!valid.has(id)) selected.delete(id);
    find("[data-players]").innerHTML = filtered().map(p => `<label><input type="checkbox" value="${p.id}" ${selected.has(String(p.id)) ? "checked" : ""} /> ${escapeHtml(p.name)} · ${label} ${p[balanceKey] || 0}</label>`).join("") || "无匹配选手";
    find("[data-count]").textContent = `已选择 ${selected.size} 人`;
  }
  find("[data-search]").addEventListener("input", render);
  find("[data-players]").addEventListener("change", event => {
    if (event.target.checked) selected.add(event.target.value); else selected.delete(event.target.value);
    find("[data-count]").textContent = `已选择 ${selected.size} 人`;
  });
  find("[data-all]").addEventListener("click", () => { filtered().forEach(p => selected.add(String(p.id))); render(); });
  find("[data-clear]").addEventListener("click", () => { selected.clear(); render(); });
  find("[data-submit]").addEventListener("click", async () => {
    if (busy) return;
    const targets = getPlayers().filter(p => selected.has(String(p.id)));
    const amount = Number(find("[data-amount]").value);
    const reason = find("[data-reason]").value.trim();
    const feedback = find("[data-feedback]");
    if (!targets.length || targets.length > 500 || !Number.isInteger(amount) || !amount || amount < -2147483648 || amount > 2147483647 || !reason) {
      feedback.textContent = "请选择1至500名选手，填写非零整数金额和调整原因。"; return;
    }
    const summary = targets.map(p => `${p.name}：${p[balanceKey] || 0} → ${(p[balanceKey] || 0) + amount}`).join("\n");
    if (!confirm(`请核对${label}调整\n共 ${targets.length} 人，每人 ${amount > 0 ? "+" : ""}${amount}，总变动 ${targets.length * amount}\n原因：${reason}\n\n${summary}\n\n确认后整批执行。`)) return;
    busy = true;
    panel.querySelectorAll("input,button").forEach(el => { el.disabled = true; });
    try {
      const updated = await request(`/admin/players/${currency}/batch`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ playerIds: targets.map(p => p.id), amount, reason }) });
      feedback.textContent = `成功调整 ${updated.length} 名选手，每人 ${amount > 0 ? "+" : ""}${amount}，总变动 ${updated.length * amount}。\n` + updated.map(p => `${p.name}：当前${label} ${p[balanceKey]}`).join("\n");
      selected.clear(); find("[data-amount]").value = ""; find("[data-reason]").value = "";
      try { await onSuccess(); } catch (error) { feedback.textContent += `\n调整已成功，但刷新失败：${error.message}。请刷新查看，勿重复提交。`; }
      render();
    } catch (error) { feedback.textContent = `提交未确认成功：${error.message}。如网络中断，请先刷新核对流水，勿直接重复提交。`; }
    finally { busy = false; panel.querySelectorAll("input,button").forEach(el => { el.disabled = false; }); }
  });
  render();
  return render;
}
