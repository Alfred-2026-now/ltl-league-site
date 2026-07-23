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
let ledgerPage = 1;
let ledgerPageSize = 20;
let ledgerTotalPages = 1;

const LEDGER_TYPE_MAP = {
  match_reward: "比赛奖励",
  luxury_tax: "奢侈税",
  loan_fee: "租借费",
  player_donation: "选手赠与",
  player_sign_loss: "买入损耗",
  player_release_loss: "解约损耗",
  salary_deduct: "工资扣除",
  captain_salary: "队长发工资",
  captain_deposit: "队长转入",
  manual_admin: "管理员调整"
};

function showError(msg) {
  document.getElementById("errorMessage").textContent = msg;
  document.getElementById("errorNotice").style.display = "block";
  ["noTeamNotice", "teamSection", "membersSection", "ledgerSection"].forEach(s => {
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
    document.getElementById("noTeamNotice").style.display = "block";
    document.getElementById("teamSection").style.display = "none";
    document.getElementById("membersSection").style.display = "none";
    document.getElementById("ledgerSection").style.display = "none";
    document.getElementById("errorNotice").style.display = "none";
    return;
  }

  document.getElementById("teamTitle").textContent = `${currentContext.teamState} · ${currentContext.teamName}`;
  renderLogo(currentContext.logoUrl);
  document.getElementById("teamStateInput").value = currentContext.teamState || "";
  document.getElementById("teamNameInput").value = currentContext.teamName || "";
  document.getElementById("descriptionInput").value = currentContext.description || "";
  document.getElementById("teamCoinsDisplay").textContent = currentContext.teamPCoins ?? 0;
  document.getElementById("captainDepositDisplay").textContent = currentContext.captainDeposit ?? 0;

  document.getElementById("teamSection").style.display = "block";
  document.getElementById("membersSection").style.display = "block";
  document.getElementById("ledgerSection").style.display = "block";
  document.getElementById("noTeamNotice").style.display = "none";
  document.getElementById("errorNotice").style.display = "none";

  renderMembers(currentContext.members);
  await loadLedger();
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
        <td>${escapeHtml(m.name)}</td>
        <td>${escapeHtml(m.position || "-")}</td>
        <td>${m.value ?? 0}</td>
        <td>${m.deposit ?? 0}</td>
        <td>${roleTag}</td>
        <td style="text-align:right;"><button class="btn ghost sm" data-id="${m.id}" data-action="pay">发工资</button></td>
      </tr>
    `;
  }).join("");
}

function escapeHtml(text) {
  return String(text ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function formatAmount(value) {
  const n = Number(value || 0);
  const cls = n >= 0 ? "amount-pos" : "amount-neg";
  return `<span class="${cls}">${n > 0 ? "+" : ""}${n}P</span>`;
}

function getTypeText(type) {
  return LEDGER_TYPE_MAP[type] || type || "-";
}

function updateLedgerPager(total, page, pageSize) {
  const totalPages = Math.max(1, Math.ceil((total || 0) / pageSize));
  ledgerTotalPages = totalPages;
  ledgerPage = Math.min(Math.max(1, page), totalPages);
  document.getElementById("ledgerTotalText").textContent = `共 ${total || 0} 条`;
  document.getElementById("ledgerTotalPages").textContent = String(totalPages);
  document.getElementById("ledgerPageInput").value = String(ledgerPage);
  document.getElementById("ledgerPageInput").max = String(totalPages);
  document.getElementById("ledgerPrevBtn").disabled = ledgerPage <= 1;
  document.getElementById("ledgerNextBtn").disabled = ledgerPage >= totalPages;
}

function renderLedgerRows(rows) {
  const body = document.getElementById("ledgerBody");
  if (!rows || rows.length === 0) {
    body.innerHTML = `<tr><td colspan="6" style="padding:1rem;color:#888;text-align:center;">暂无流水</td></tr>`;
    return;
  }
  body.innerHTML = rows.map(row => `
    <tr>
      <td>${escapeHtml(row.createdAt || "-")}</td>
      <td>${escapeHtml(getTypeText(row.type))}</td>
      <td>${formatAmount(row.amount)}</td>
      <td>${row.balanceBefore ?? "-"} → ${row.balanceAfter ?? "-"}</td>
      <td>${row.matchId ? `#${row.matchId}${row.version ? " " + escapeHtml(row.version) : ""}` : "-"}</td>
      <td>${escapeHtml(row.reason || "-")}</td>
    </tr>
  `).join("");
}

async function loadLedger() {
  const body = document.getElementById("ledgerBody");
  if (!currentContext || currentContext.teamId == null) {
    return;
  }
  body.innerHTML = `<tr><td colspan="6" style="padding:1rem;color:#888;text-align:center;">加载中…</td></tr>`;
  try {
    const data = await request(`/captain/p-ledger?page=${ledgerPage}&pageSize=${ledgerPageSize}`);
    updateLedgerPager(data.total, data.page, data.pageSize);
    renderLedgerRows(data.records || []);
  } catch (e) {
    body.innerHTML = `<tr><td colspan="6" style="padding:1rem;color:#ff9f9f;text-align:center;">加载失败：${escapeHtml(e.message)}</td></tr>`;
  }
}

async function jumpLedgerPage() {
  const input = Number(document.getElementById("ledgerPageInput").value);
  if (!input || input < 1) {
    document.getElementById("ledgerPageInput").value = String(ledgerPage);
    return;
  }
  ledgerPage = Math.min(input, ledgerTotalPages);
  await loadLedger();
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

async function saveTeamInfo() {
  const state = document.getElementById("teamStateInput").value.trim();
  const name = document.getElementById("teamNameInput").value.trim();
  const desc = document.getElementById("descriptionInput").value.trim();
  const msg = document.getElementById("descMsg");
  if (!state) {
    msg.style.color = "#ef4444";
    msg.textContent = "请填写简写";
    return;
  }
  if (!name) {
    msg.style.color = "#ef4444";
    msg.textContent = "请填写队名";
    return;
  }
  try {
    await request("/captain/team-info", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ state, name, description: desc })
    });
    msg.style.color = "#22c55e";
    msg.textContent = "已保存";
    if (currentContext) {
      currentContext.teamState = state;
      currentContext.teamName = name;
      currentContext.description = desc;
      document.getElementById("teamTitle").textContent = `${state} · ${name}`;
    }
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
    ledgerPage = 1;
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
    ledgerPage = 1;
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
  document.getElementById("saveDescBtn").addEventListener("click", saveTeamInfo);
  document.getElementById("depositBtn").addEventListener("click", depositToTeam);

  document.getElementById("ledgerPageSize").addEventListener("change", async (e) => {
    ledgerPageSize = Number(e.target.value) || 20;
    ledgerPage = 1;
    await loadLedger();
  });
  document.getElementById("ledgerPrevBtn").addEventListener("click", async () => {
    if (ledgerPage <= 1) return;
    ledgerPage -= 1;
    await loadLedger();
  });
  document.getElementById("ledgerNextBtn").addEventListener("click", async () => {
    if (ledgerPage >= ledgerTotalPages) return;
    ledgerPage += 1;
    await loadLedger();
  });
  document.getElementById("ledgerJumpBtn").addEventListener("click", jumpLedgerPage);
  document.getElementById("ledgerPageInput").addEventListener("keydown", (e) => {
    if (e.key === "Enter") jumpLedgerPage();
  });
  document.getElementById("ledgerRefreshBtn").addEventListener("click", loadLedger);
}

async function init() {
  bindEvents();
  await loadContext();
}

document.addEventListener("DOMContentLoaded", init);
