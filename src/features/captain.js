import { getApiBase } from "../config/api.js";

const API_BASE = getApiBase();

async function request(endpoint, options = {}) {
  const url = `${API_BASE}${endpoint}`;
  const response = await fetch(url, { credentials: "include", ...options });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const data = await response.json();
  if (data.code !== 200) throw new Error(data.message || "请求失败");
  return data.data;
}

let currentContext = null;
let currentTarget = null;

function showSection(id) {
  ["errorNotice", "noTeamNotice", "teamSection", "membersSection"].forEach(s => {
    document.getElementById(s).style.display = (s === id) ? "block" : "none";
  });
}

function showError(msg) {
  document.getElementById("errorMessage").textContent = msg;
  document.getElementById("errorNotice").style.display = "block";
  ["noTeamNotice", "teamSection", "membersSection"].forEach(s => {
    document.getElementById(s).style.display = "none";
  });
}

async function loadContext() {
  try {
    currentContext = await request("/captain/context");
  } catch (e) {
    showError(`加载失败：${e.message}（仅队长可访问）`);
    return;
  }

  const hasTeam = currentContext.teamId != null;
  if (!hasTeam) {
    // 队长尚未归属队伍
    document.getElementById("noTeamNotice").style.display = "block";
    document.getElementById("teamSection").style.display = "none";
    document.getElementById("membersSection").style.display = "none";
    document.getElementById("errorNotice").style.display = "none";
    return;
  }

  // 有队伍：渲染信息
  document.getElementById("teamTitle").textContent = `${currentContext.teamState} · ${currentContext.teamName}`;
  renderLogo(currentContext.logoUrl);
  document.getElementById("descriptionInput").value = currentContext.description || "";
  document.getElementById("teamCoinsDisplay").textContent = currentContext.teamPCoins ?? 0;
  document.getElementById("captainDepositDisplay").textContent = currentContext.captainDeposit ?? 0;

  document.getElementById("teamSection").style.display = "block";
  document.getElementById("membersSection").style.display = "block";
  document.getElementById("noTeamNotice").style.display = "none";
  document.getElementById("errorNotice").style.display = "none";

  renderMembers(currentContext.members);
}

function renderLogo(url) {
  const el = document.getElementById("logoPreview");
  if (url) {
    el.innerHTML = `<img src="${url}" alt="队徽" class="team-logo" />`;
  } else {
    el.innerHTML = `<div class="team-logo-empty">未上传</div>`;
  }
}

function renderMembers(members) {
  const body = document.getElementById("membersBody");
  if (!members || members.length === 0) {
    body.innerHTML = `<tr><td colspan="6" style="padding:1rem;color:#888;text-align:center;">暂无队员</td></tr>`;
    return;
  }
  body.innerHTML = members.map(m => {
    const roleTag = m.isCaptain
      ? `<span class="role-tag captain">队长</span>`
      : (m.isSubstitute ? `<span class="role-tag sub">替补</span>` : `<span class="role-tag member">队员</span>`);
    return `
      <tr>
        <td>${m.name}</td>
        <td>${m.position || '-'}</td>
        <td>${m.value ?? 0}</td>
        <td>${m.deposit ?? 0}</td>
        <td>${roleTag}</td>
        <td style="text-align:right;"><button class="btn ghost sm" data-id="${m.id}" data-action="pay">发工资</button></td>
      </tr>
    `;
  }).join("");
}

async function uploadLogo() {
  const fileInput = document.getElementById("logoFile");
  const msg = document.getElementById("logoMsg");
  msg.style.color = "#ef4444";
  if (!fileInput.files.length) { msg.textContent = "请先选择文件"; return; }
  const form = new FormData();
  form.append("file", fileInput.files[0]);
  try {
    msg.textContent = "上传中...";
    const data = await request("/captain/team-logo", { method: "POST", body: form });
    msg.style.color = "#22c55e";
    msg.textContent = "上传成功";
    renderLogo(data.url);
    fileInput.value = "";
  } catch (e) {
    msg.textContent = e.message;
  }
}

async function saveDescription() {
  const desc = document.getElementById("descriptionInput").value.trim();
  const msg = document.getElementById("descMsg");
  try {
    await request("/captain/team-info", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ description: desc })
    });
    msg.style.color = "#22c55e";
    msg.textContent = "已保存";
    if (currentContext) currentContext.description = desc;
  } catch (e) {
    msg.style.color = "#ef4444";
    msg.textContent = e.message;
  }
}

async function depositToTeam() {
  const amount = Number(document.getElementById("depositAmount").value);
  const reason = document.getElementById("depositReason").value.trim();
  const msg = document.getElementById("depositMsg");
  msg.style.color = "#ef4444";
  if (!amount || amount <= 0) { msg.textContent = "请输入正数金额"; return; }
  try {
    await request("/captain/deposit-to-team", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ amount, reason })
    });
    msg.style.color = "#22c55e";
    msg.textContent = "转入成功";
    document.getElementById("depositAmount").value = "";
    document.getElementById("depositReason").value = "";
    await loadContext();
  } catch (e) {
    msg.textContent = e.message;
  }
}

function openSalaryDialog(member) {
  currentTarget = member;
  document.getElementById("salaryTargetName").textContent = member.name;
  document.getElementById("salaryTeamBalance").textContent = currentContext.teamPCoins ?? 0;
  document.getElementById("salaryAmount").value = "";
  document.getElementById("salaryReason").value = "";
  document.getElementById("salaryError").textContent = "";
  document.getElementById("salaryDialog").showModal();
}

async function confirmPay() {
  const amount = Number(document.getElementById("salaryAmount").value);
  const reason = document.getElementById("salaryReason").value.trim();
  const errEl = document.getElementById("salaryError");
  if (!amount || amount <= 0) {
    errEl.textContent = "请输入正数金额";
    return;
  }
  errEl.textContent = "";
  try {
    await request("/captain/pay-salary", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ targetPlayerId: currentTarget.id, amount, reason })
    });
    document.getElementById("salaryDialog").close();
    await loadContext();
  } catch (e) {
    errEl.textContent = e.message;
  }
}

function bindEvents() {
  document.getElementById("membersBody").addEventListener("click", (e) => {
    if (!e.target.matches("[data-action='pay']")) return;
    const id = Number(e.target.dataset.id);
    const m = currentContext && currentContext.members.find(x => x.id === id);
    if (m) openSalaryDialog(m);
  });
  document.getElementById("salaryCancel").addEventListener("click", () => {
    document.getElementById("salaryDialog").close();
  });
  document.getElementById("salaryConfirm").addEventListener("click", confirmPay);
  document.getElementById("uploadLogoBtn").addEventListener("click", uploadLogo);
  document.getElementById("saveDescBtn").addEventListener("click", saveDescription);
  document.getElementById("depositBtn").addEventListener("click", depositToTeam);
}

async function init() {
  bindEvents();
  await loadContext();
}

document.addEventListener("DOMContentLoaded", init);
