import { getApiBase } from "./config/api.js";
import { authApi } from "./util/auth.js";

const API = getApiBase();
const els = {
  content: document.getElementById("predictionContent"),
  message: document.getElementById("predictionMessage")
};
let currentUser = null;
let activeTab = "open";

async function request(path, options = {}) {
  const response = await fetch(`${API}${path}`, { credentials: "include", ...options });
  const data = await response.json().catch(() => null);
  if (!response.ok || !data || data.code !== 200) {
    throw new Error(data?.message || `请求失败（${response.status}）`);
  }
  if (options.method && options.method.toUpperCase() !== "GET") {
    window.dispatchEvent(new Event("ltl:balance-changed"));
  }
  return data.data;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function formatTime(value) {
  if (!value) return "-";
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

function remainingTime(deadlineAt) {
  const ms = new Date(deadlineAt).getTime() - Date.now();
  if (ms <= 0) return null;
  const minutes = Math.floor(ms / 60000);
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  if (days > 0) return `${days}天${hours}小时`;
  if (hours > 0) return `${hours}小时${minutes % 60}分钟`;
  return `${minutes}分钟`;
}

function setMessage(message, error = false) {
  els.message.textContent = message || "";
  els.message.classList.toggle("error", error);
}

function optionRow(prediction, option) {
  const mine = prediction.viewerBetOptionId === option.id;
  const revealed = option.voteCount != null;
  const votes = revealed
    ? `<span class="prediction-option-votes">
        <span class="prediction-option-bar" style="width:${Math.max(4, option.votePercent || 0) * 0.06}rem" aria-hidden="true"></span>
        <span>${option.voteCount}票 · ${option.votePercent ?? 0}%</span>
      </span>`
    : "";
  const marks = [
    option.isCorrect ? '<span title="正确选项">✅</span>' : "",
    mine ? '<span title="我的选择">🎯</span>' : ""
  ].filter(Boolean).join("");
  const label = `<span class="prediction-option-label">${escapeHtml(option.label)}${marks}</span>`;
  if (prediction.bettingOpen) {
    return `<button type="button" class="prediction-option${mine ? " mine" : ""}${option.isCorrect ? " correct" : ""}"
      data-bet-prediction="${prediction.id}" data-bet-option="${option.id}" data-bet-label="${escapeHtml(option.label)}">
      ${label}${votes}</button>`;
  }
  return `<div class="prediction-option${mine ? " mine" : ""}${option.isCorrect ? " correct" : ""}">${label}${votes}</div>`;
}

function statusChip(prediction) {
  if (prediction.status === "PUBLISHED") {
    return prediction.bettingOpen ? "进行中" : "已截止 · 待结算";
  }
  return prediction.status === "SETTLED" ? "已结算" : "已作废";
}

function settledBanner(prediction) {
  const winners = (prediction.winners || []).map(winner => escapeHtml(winner.playerName)).join("、");
  const summary = prediction.winnerCount > 0
    ? `正确选项：<strong>${escapeHtml(prediction.correctOptionLabel || "-")}</strong> · ${prediction.winnerCount}人猜对，每人获得 <strong>${prediction.rewardPPerWinner}P</strong> 与 <strong>🪙 ${prediction.rewardBountyPerWinner}</strong> 赏金<br>中奖选手：${winners}`
    : `正确选项：<strong>${escapeHtml(prediction.correctOptionLabel || "-")}</strong> · 无人猜中，奖励不发放`;
  let viewer = "";
  if (prediction.viewerBetOptionId != null) {
    viewer = prediction.viewerWon
      ? `<div class="prediction-result-banner win">🎉 你猜中了！获得 ${prediction.viewerRewardP}P 与 ${prediction.viewerRewardBounty} 赏金。</div>`
      : `<div class="prediction-result-banner lose">这次没有猜中，下次再接再厉。</div>`;
  }
  return `<div class="prediction-result-banner">${summary}</div>${viewer}`;
}

function predictionCard(prediction) {
  const mine = prediction.viewerBetOptionId != null
    ? `<p class="task-note">我的选择：<strong>${escapeHtml(prediction.viewerBetOptionLabel || "-")}</strong>${prediction.bettingOpen ? "（截止前可点击其他选项改票）" : ""}</p>`
    : "";
  const remaining = prediction.bettingOpen && remainingTime(prediction.deadlineAt)
    ? `<span>剩余 ${remainingTime(prediction.deadlineAt)}</span>`
    : "";
  const cancelled = prediction.status === "CANCELLED" && prediction.cancelReason
    ? `<p class="task-review-comment">作废原因：${escapeHtml(prediction.cancelReason)}</p>`
    : "";
  const description = prediction.description
    ? `<div class="task-requirements">${escapeHtml(prediction.description).replaceAll("\n", "<br>")}</div>`
    : "";
  return `<article class="panel task-card">
    <div class="task-card-heading">
      <div>
        <div class="task-meta"><span class="task-status">${statusChip(prediction)}</span></div>
        <h2>${escapeHtml(prediction.title)}</h2>
      </div>
      <div class="task-rewards"><strong>${prediction.rewardPTotal}P</strong><strong>🪙 ${prediction.rewardBountyTotal}</strong></div>
    </div>
    ${description}
    <div class="task-facts">
      <span>截止时间：${formatTime(prediction.deadlineAt)}</span>
      <span>总投注：${prediction.totalBets || 0}票</span>
      ${remaining}
    </div>
    <div class="prediction-options">${(prediction.options || []).map(option => optionRow(prediction, option)).join("")}</div>
    ${mine}
    ${prediction.status === "SETTLED" ? settledBanner(prediction) : ""}
    ${cancelled}
  </article>`;
}

async function renderActive() {
  setMessage("");
  document.querySelectorAll("[data-prediction-tab]").forEach(button =>
    button.classList.toggle("primary", button.dataset.predictionTab === activeTab));
  els.content.innerHTML = `<div class="panel task-empty">加载中…</div>`;
  try {
    const predictions = await request("/predictions");
    const visible = predictions.filter(prediction =>
      activeTab === "open" ? prediction.status === "PUBLISHED" : prediction.status !== "PUBLISHED");
    els.content.innerHTML = visible.length
      ? `<div class="task-list">${visible.map(predictionCard).join("")}</div>`
      : `<div class="panel task-empty">${activeTab === "open" ? "当前暂无进行中的竞猜。" : "暂无已结束的竞猜。"}</div>`;
  } catch (error) {
    els.content.innerHTML = `<div class="panel task-empty task-error">${escapeHtml(error.message)}</div>`;
  }
}

async function runAction(action, success) {
  try {
    setMessage("处理中…");
    await action();
    setMessage(success);
    await renderActive();
  } catch (error) {
    setMessage(error.message, true);
  }
}

document.addEventListener("click", async event => {
  const tab = event.target.closest("[data-prediction-tab]");
  if (tab) {
    activeTab = tab.dataset.predictionTab;
    await renderActive();
    return;
  }
  const bet = event.target.closest("[data-bet-option]");
  if (bet) {
    if (!currentUser) {
      window.location.assign(`login.html?redirect=${encodeURIComponent(window.location.href)}`);
      return;
    }
    const label = bet.dataset.betLabel;
    if (!confirm(`确认投给「${label}」？截止前可以随时改票。`)) return;
    await runAction(() => request(`/predictions/${bet.dataset.betPrediction}/bets`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ optionId: Number(bet.dataset.betOption) })
    }), "投票成功");
  }
});

currentUser = await authApi.getCurrentUser().catch(() => null);
await renderActive();
