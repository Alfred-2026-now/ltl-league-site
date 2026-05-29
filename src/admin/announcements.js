const API_BASE_URL = "http://123.57.19.160/api";

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

async function listAnnouncements(isActive) {
  const query = isActive !== undefined ? `?isActive=${isActive}` : "";
  return request(`/admin/announcements${query}`);
}

async function createAnnouncement(payload) {
  return request("/admin/announcements", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

async function updateAnnouncement(id, payload) {
  return request(`/admin/announcements/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

async function deleteAnnouncement(id) {
  return request(`/admin/announcements/${id}`, { method: "DELETE" });
}

let currentFilter = undefined;
const els = {};

function bindEls() {
  els.addTitle = document.getElementById("addTitle");
  els.addDate = document.getElementById("addDate");
  els.addContent = document.getElementById("addContent");
  els.addIsActive = document.getElementById("addIsActive");
  els.addBtn = document.getElementById("addBtn");
  els.showAllBtn = document.getElementById("showAllBtn");
  els.showActiveBtn = document.getElementById("showActiveBtn");
  els.showInactiveBtn = document.getElementById("showInactiveBtn");
  els.announcementBody = document.getElementById("announcementBody");
}

function formatDate(dateStr) {
  if (!dateStr) return "-";
  return dateStr;
}

function renderRows(rows) {
  if (!rows.length) {
    els.announcementBody.innerHTML = `<tr><td colspan="5" style="padding:1rem;" class="muted">暂无公告。</td></tr>`;
    return;
  }
  els.announcementBody.innerHTML = rows.map(row => `
    <tr>
      <td style="padding:.75rem 1rem;">${formatDate(row.announceDate)}</td>
      <td style="padding:.75rem 1rem;">${row.title || "-"}</td>
      <td style="padding:.75rem 1rem;max-width:400px;overflow:hidden;text-overflow:ellipsis;">${row.content || "-"}</td>
      <td style="padding:.75rem 1rem;"><span class="status-badge" data-tone="${row.isActive ? "success" : "muted"}">${row.isActive ? "已发布" : "草稿"}</span></td>
      <td style="padding:.75rem 1rem;">
        <button class="btn" style="padding:.25rem .5rem;font-size:.875rem;margin-right:.25rem;" data-id="${row.id}" data-action="toggle">${row.isActive ? "设为草稿" : "发布"}</button>
        <button class="btn" style="padding:.25rem .5rem;font-size:.875rem;" data-id="${row.id}" data-action="delete">删除</button>
      </td>
    </tr>
  `).join("");
}

async function refresh() {
  try {
    els.announcementBody.innerHTML = `<tr><td colspan="5" style="padding:1rem;" class="muted">加载中…</td></tr>`;
    renderRows(await listAnnouncements(currentFilter));
  } catch (e) {
    els.announcementBody.innerHTML = `<tr><td colspan="5" style="padding:1rem;color:#ff9f9f;">加载失败：${e.message}</td></tr>`;
  }
}

async function submitAdd() {
  try {
    if (!els.addTitle.value) {
      alert("请填写标题");
      return;
    }
    if (!els.addContent.value) {
      alert("请填写内容");
      return;
    }
    if (!els.addDate.value) {
      alert("请选择日期");
      return;
    }
    await createAnnouncement({
      title: els.addTitle.value.trim(),
      content: els.addContent.value.trim(),
      announceDate: els.addDate.value,
      isActive: els.addIsActive.checked ? 1 : 0
    });
    alert("公告已发布");
    els.addTitle.value = "";
    els.addContent.value = "";
    await refresh();
  } catch (e) {
    alert(`发布失败：${e.message}`);
  }
}

async function handleActionClick(e) {
  if (!e.target.matches("[data-action]")) return;
  const id = e.target.dataset.id;
  const action = e.target.dataset.action;

  if (action === "toggle") {
    try {
      const row = Array.from(els.announcementBody.querySelectorAll("tr")).find(tr => tr.querySelector(`[data-id="${id}"]`));
      const isActive = row && row.querySelector("[data-action=\"toggle\"]").textContent.includes("设为草稿");
      await updateAnnouncement(Number(id), { isActive: isActive ? 0 : 1 });
      alert("状态已更新");
      await refresh();
    } catch (err) {
      alert(`更新失败：${err.message}`);
    }
  } else if (action === "delete") {
    if (!confirm("确定要删除这条公告吗？")) return;
    try {
      await deleteAnnouncement(Number(id));
      alert("公告已删除");
      await refresh();
    } catch (err) {
      alert(`删除失败：${err.message}`);
    }
  }
}

async function init() {
  bindEls();
  els.showAllBtn.addEventListener("click", () => { currentFilter = undefined; refresh(); });
  els.showActiveBtn.addEventListener("click", () => { currentFilter = 1; refresh(); });
  els.showInactiveBtn.addEventListener("click", () => { currentFilter = 0; refresh(); });
  els.addBtn.addEventListener("click", submitAdd);
  els.announcementBody.addEventListener("click", handleActionClick);

  // 设置默认日期为今天
  els.addDate.value = new Date().toISOString().split("T")[0];

  await refresh();
}

document.addEventListener("DOMContentLoaded", init);
