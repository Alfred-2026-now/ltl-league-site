import { authApi, requireAuth } from "./util/auth.js";
import { getApiBase } from "./config/api.js";

const state = {
  user: null,
  userInfo: null,
  players: [],
  recipientType: "TEAM",
  preview: null
};

const els = {
  donorName: document.getElementById("donorName"),
  donorBalance: document.getElementById("donorBalance"),
  teamRecipient: document.getElementById("teamRecipient"),
  teamRecipientField: document.getElementById("teamRecipientField"),
  playerRecipientField: document.getElementById("playerRecipientField"),
  recipientPlayer: document.getElementById("recipientPlayer"),
  amountInput: document.getElementById("amountInput"),
  previewAmount: document.getElementById("previewAmount"),
  previewFee: document.getElementById("previewFee"),
  previewTotal: document.getElementById("previewTotal"),
  previewBalanceAfter: document.getElementById("previewBalanceAfter"),
  statusLine: document.getElementById("statusLine"),
  submitBtn: document.getElementById("submitBtn"),
  historyList: document.getElementById("historyList"),
  confirmDialog: document.getElementById("confirmDialog"),
  confirmRecipient: document.getElementById("confirmRecipient"),
  confirmAmount: document.getElementById("confirmAmount"),
  confirmFee: document.getElementById("confirmFee"),
  confirmTotal: document.getElementById("confirmTotal"),
  cancelConfirmBtn: document.getElementById("cancelConfirmBtn"),
  confirmSubmitBtn: document.getElementById("confirmSubmitBtn")
};

let previewTimer = null;

async function request(endpoint, options = {}) {
  const response = await fetch(`${getApiBase()}${endpoint}`, {
    ...options,
    credentials: "include"
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  const data = await response.json();
  if (data.code !== 200) {
    throw new Error(data.message || "请求失败");
  }
  return data.data;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function formatP(value) {
  if (value === null || value === undefined || value === "") return "-";
  return `${Number(value).toLocaleString("zh-CN")}P`;
}

function formatDate(value) {
  if (!value) return "-";
  return new Date(value).toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  });
}

function setStatus(message, tone = "") {
  els.statusLine.textContent = message || "";
  els.statusLine.className = `status-line${tone ? ` ${tone}` : ""}`;
}

function getPayload() {
  const amount = els.amountInput.value ? Number(els.amountInput.value) : null;
  const payload = {
    recipientType: state.recipientType,
    amount
  };
  if (state.recipientType === "PLAYER") {
    payload.recipientPlayerId = els.recipientPlayer.value ? Number(els.recipientPlayer.value) : null;
  }
  return payload;
}

function resetPreview() {
  state.preview = null;
  els.previewAmount.textContent = "-";
  els.previewFee.textContent = "-";
  els.previewTotal.textContent = "-";
  els.previewBalanceAfter.textContent = "-";
  els.submitBtn.disabled = true;
}

function renderPreview(preview) {
  state.preview = preview;
  els.previewAmount.textContent = formatP(preview.amount);
  els.previewFee.textContent = formatP(preview.feeAmount);
  els.previewTotal.textContent = formatP(preview.totalCost);
  els.previewBalanceAfter.textContent = formatP(preview.balanceAfter);
  els.submitBtn.disabled = !preview.allowed;
  setStatus(preview.message || "", preview.allowed ? "ok" : "error");
}

function schedulePreview() {
  clearTimeout(previewTimer);
  resetPreview();
  previewTimer = setTimeout(loadPreview, 220);
}

async function loadPreview() {
  const payload = getPayload();
  if (!payload.amount) {
    setStatus("");
    return;
  }
  if (payload.amount % 10 !== 0 || payload.amount < 10 || payload.amount > 100000) {
    setStatus("额度必须是 10 到 100000 之间的 10 的倍数", "error");
    return;
  }
  if (payload.recipientType === "PLAYER" && !payload.recipientPlayerId) {
    setStatus("请选择受赠人", "error");
    return;
  }

  try {
    const preview = await request("/player-transfers/preview", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    renderPreview(preview);
  } catch (error) {
    setStatus(error.message, "error");
  }
}

function renderUserInfo() {
  const info = state.userInfo;
  els.donorName.textContent = info?.playerName || state.user?.playerName || "-";
  els.donorBalance.textContent = formatP(info?.deposit || 0);
  if (info?.teamId) {
    els.teamRecipient.textContent = `${info.teamState || ""} ${info.teamName || ""}`.trim();
  } else {
    els.teamRecipient.textContent = "自由人";
  }
}

function renderPlayers() {
  const currentPlayerId = state.userInfo?.playerId;
  const options = state.players
    .filter(player => player.id !== currentPlayerId)
    .map(player => `<option value="${player.id}">${escapeHtml(player.name)} · ${formatP(player.deposit || 0)}</option>`)
    .join("");
  els.recipientPlayer.innerHTML = `<option value="">选择受赠人</option>${options}`;
}

function renderHistory(rows) {
  if (!rows.length) {
    els.historyList.innerHTML = `
      <div class="history-item">
        <div class="history-meta">暂无记录</div>
      </div>
    `;
    return;
  }

  els.historyList.innerHTML = rows.map(row => {
    const recipient = row.recipientType === "PLAYER"
      ? row.recipientPlayerName
      : `${row.recipientTeamState || ""} ${row.recipientTeamName || ""}`.trim();
    return `
      <div class="history-item">
        <div class="history-main">
          <span>${escapeHtml(recipient || "-")}</span>
          <strong>${formatP(row.amount)}</strong>
        </div>
        <div class="history-meta">手续费 ${formatP(row.feeAmount)} · 总扣款 ${formatP(row.totalCost)}</div>
        <div class="history-meta">${formatDate(row.createdAt)}</div>
      </div>
    `;
  }).join("");
}

function switchType(type) {
  state.recipientType = type;
  document.querySelectorAll(".type-option").forEach(btn => {
    btn.classList.toggle("active", btn.dataset.type === type);
  });
  const isPlayer = type === "PLAYER";
  els.playerRecipientField.style.display = isPlayer ? "" : "none";
  els.teamRecipientField.style.display = isPlayer ? "none" : "";
  schedulePreview();
}

function getPreviewRecipientLabel() {
  const preview = state.preview;
  if (!preview) return "-";
  if (preview.recipientType === "PLAYER") {
    return preview.recipientPlayerName || "-";
  }
  return `${preview.recipientTeamState || ""} ${preview.recipientTeamName || ""}`.trim() || "-";
}

function openConfirm() {
  if (!state.preview || !state.preview.allowed) return;
  els.confirmRecipient.textContent = getPreviewRecipientLabel();
  els.confirmAmount.textContent = formatP(state.preview.amount);
  els.confirmFee.textContent = formatP(state.preview.feeAmount);
  els.confirmTotal.textContent = formatP(state.preview.totalCost);
  els.confirmDialog.showModal();
}

async function submitTransfer() {
  if (!state.preview || !state.preview.allowed) return;
  els.confirmSubmitBtn.disabled = true;
  try {
    await request("/player-transfers", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(getPayload())
    });
    els.confirmDialog.close();
    els.amountInput.value = "";
    await refreshAfterTransfer();
    setStatus("转赠成功", "ok");
  } catch (error) {
    setStatus(error.message, "error");
  } finally {
    els.confirmSubmitBtn.disabled = false;
  }
}

async function refreshAfterTransfer() {
  state.userInfo = await authApi.getUserInfo();
  renderUserInfo();
  resetPreview();
  const history = await request("/player-transfers/my?limit=20");
  renderHistory(history || []);
}

function bindEvents() {
  document.querySelectorAll(".type-option").forEach(btn => {
    btn.addEventListener("click", () => switchType(btn.dataset.type));
  });
  els.amountInput.addEventListener("input", schedulePreview);
  els.recipientPlayer.addEventListener("change", schedulePreview);
  els.submitBtn.addEventListener("click", openConfirm);
  els.cancelConfirmBtn.addEventListener("click", () => els.confirmDialog.close());
  els.confirmSubmitBtn.addEventListener("click", submitTransfer);
}

async function init() {
  state.user = await requireAuth();
  if (!state.user) return;

  bindEvents();
  const [userInfo, players, history] = await Promise.all([
    authApi.getUserInfo(),
    request("/players"),
    request("/player-transfers/my?limit=20")
  ]);
  state.userInfo = userInfo;
  state.players = players || [];
  renderUserInfo();
  renderPlayers();
  renderHistory(history || []);
}

init().catch(error => {
  setStatus(error.message || "加载失败", "error");
});
