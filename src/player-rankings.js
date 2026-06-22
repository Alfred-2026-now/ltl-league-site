import { getApiBase } from "./config/api.js";

const API_BASE_URL = getApiBase();
const POSITION_LABELS = {
  TOP: "上单",
  JUG: "打野",
  MID: "中单",
  BOT: "射手",
  SUP: "辅助"
};

async function request(endpoint, options = {}) {
  const url = `${API_BASE_URL}${endpoint}`;
  const response = await fetch(url, {
    credentials: "include",
    ...options,
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(options.headers || {})
    }
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }
  const data = await response.json();
  if (data.code !== 200) {
    throw new Error(data.message || "请求失败");
  }
  return data.data;
}

async function getPlayers() {
  return request("/players");
}

async function getTeams() {
  return request("/teams");
}

async function getReviewPlayers(sort) {
  return request(`/player-reviews/players?sort=${encodeURIComponent(sort)}`);
}

async function getPlayerReviewDetail(playerId) {
  return request(`/player-reviews/players/${playerId}`);
}

async function createPlayerReview(playerId, payload) {
  return request(`/player-reviews/players/${playerId}/reviews`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

async function rateReview(reviewId, score) {
  return request(`/player-reviews/reviews/${reviewId}/ratings`, {
    method: "POST",
    body: JSON.stringify({ score })
  });
}

async function tipReview(reviewId, amount) {
  return request(`/player-reviews/reviews/${reviewId}/tips`, {
    method: "POST",
    body: JSON.stringify({ amount })
  });
}

async function updateReview(reviewId, payload) {
  return request(`/player-reviews/reviews/${reviewId}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

async function deleteReview(reviewId) {
  return request(`/player-reviews/reviews/${reviewId}`, {
    method: "DELETE"
  });
}

let currentMode = "value";
let reviewSort = "popularity";
let players = [];
let teams = [];
let reviewPlayers = [];
let selectedReviewPlayerId = null;
let reviewFormPlayerId = null;
let reviewDetail = null;
const els = {};

function bindEls() {
  els.showValueRankings = document.getElementById("showValueRankings");
  els.showDepositRankings = document.getElementById("showDepositRankings");
  els.showReviewRankings = document.getElementById("showReviewRankings");
  els.reviewSortControls = document.getElementById("reviewSortControls");
  els.rankingTitle = document.getElementById("rankingTitle");
  els.valueHeader = document.getElementById("valueHeader");
  els.depositHeader = document.getElementById("depositHeader");
  els.reviewExtraHeader = document.getElementById("reviewExtraHeader");
  els.rankingBody = document.getElementById("rankingBody");
  els.rankingCards = document.getElementById("rankingCards");
  els.reviewDetail = document.getElementById("reviewDetail");
}

function setMode(mode) {
  currentMode = mode;
  els.showValueRankings.classList.toggle("primary", mode === "value");
  els.showDepositRankings.classList.toggle("primary", mode === "deposit");
  els.showReviewRankings.classList.toggle("primary", mode === "reviews");
  els.reviewSortControls.style.display = mode === "reviews" ? "flex" : "none";
  els.reviewDetail.style.display = mode === "reviews" ? "block" : "none";
}

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function rankDisplay(rank) {
  if (rank === 1) return "🥇";
  if (rank === 2) return "🥈";
  if (rank === 3) return "🥉";
  return rank;
}

function rankStyle(rank) {
  if (rank === 1) return "color:#ffd700;font-size:1.2rem;";
  if (rank === 2) return "color:#c0c0c0;font-size:1.1rem;";
  if (rank === 3) return "color:#cd7f32;font-size:1.1rem;";
  return "color:#f3f8ff;";
}

function teamText(team) {
  return team ? `${team.state} · ${team.name}` : "自由人";
}

function renderRankings() {
  if (currentMode === "reviews") {
    renderReviewRankings();
    return;
  }

  els.reviewDetail.innerHTML = "";
  if (!players.length) {
    els.rankingBody.innerHTML = `<tr><td colspan="5" style="padding:1rem;" class="muted">暂无选手数据。</td></tr>`;
    els.rankingCards.innerHTML = "";
    return;
  }

  const teamMap = new Map(teams.map(team => [team.id, team]));
  const rankedPlayers = [...players].sort((a, b) => {
    if (currentMode === "value") {
      return (b.value || 0) - (a.value || 0);
    }
    return (b.deposit || 0) - (a.deposit || 0);
  });

  els.rankingTitle.textContent = currentMode === "value" ? "选手身价排行榜" : "选手积分排行榜";
  els.valueHeader.textContent = currentMode === "value" ? "身价(P)" : "身价(P)";
  els.depositHeader.textContent = currentMode === "value" ? "积分(P)" : "积分(P)";
  els.valueHeader.style.display = currentMode === "value" ? "" : "none";
  els.depositHeader.style.display = currentMode === "deposit" ? "" : "none";
  els.reviewExtraHeader.style.display = "none";

  els.rankingBody.innerHTML = rankedPlayers.map((player, index) => {
    const rank = index + 1;
    const team = teamMap.get(player.teamId);
    const isFreeAgent = player.status === 3;
    const statusText = isFreeAgent ? ' <span class="ranking-badge danger">自由人</span>' : "";
    const substituteText = player.isSubstitute ? ' <span class="ranking-badge">替补</span>' : "";
    const metric = currentMode === "value" ? player.value : player.deposit;
    const metricColor = currentMode === "value" ? "#7cffb2" : "#ffd700";
    return `
      <tr class="ranking-row">
        <td style="padding:.75rem 1rem;font-weight:bold;${rankStyle(rank)}">${rankDisplay(rank)}</td>
        <td style="padding:.75rem 1rem;color:#f3f8ff;">${escapeHtml(player.name || "-")}${statusText}${substituteText}</td>
        <td style="padding:.75rem 1rem;color:#a8b6d6;">${escapeHtml(teamText(team))}</td>
        <td style="padding:.75rem 1rem;${currentMode === "value" ? "" : "display:none;"}color:${metricColor};font-weight:600;">${metric || 0}P</td>
        <td style="padding:.75rem 1rem;${currentMode === "deposit" ? "" : "display:none;"}color:${metricColor};font-weight:600;">${metric || 0}P</td>
      </tr>
    `;
  }).join("");

  els.rankingCards.innerHTML = rankedPlayers.map((player, index) => {
    const rank = index + 1;
    const team = teamMap.get(player.teamId);
    const metricLabel = currentMode === "value" ? "身价" : "积分";
    const metricValue = currentMode === "value" ? player.value : player.deposit;
    return `
      <button class="ranking-card" type="button">
        <span class="ranking-card-rank" style="${rankStyle(rank)}">${rankDisplay(rank)}</span>
        <span class="ranking-card-main">
          <strong>${escapeHtml(player.name || "-")}</strong>
          <small>${escapeHtml(teamText(team))}</small>
        </span>
        <span class="ranking-card-metric">${metricLabel}<strong>${metricValue || 0}P</strong></span>
      </button>
    `;
  }).join("");
}

function renderReviewRankings() {
  els.rankingTitle.textContent = "选手评价";
  els.valueHeader.textContent = "身价(P)";
  els.depositHeader.textContent = "点评数";
  els.reviewExtraHeader.textContent = "总人气";
  els.valueHeader.style.display = "";
  els.depositHeader.style.display = "";
  els.reviewExtraHeader.style.display = "";
  [...els.reviewSortControls.querySelectorAll("[data-review-sort]")].forEach(button => {
    button.classList.toggle("primary", button.dataset.reviewSort === reviewSort);
  });

  if (!reviewPlayers.length) {
    els.rankingBody.innerHTML = `<tr><td colspan="6" style="padding:1rem;" class="muted">暂无选手评价数据。</td></tr>`;
    els.rankingCards.innerHTML = "";
    els.reviewDetail.innerHTML = "";
    return;
  }

  els.reviewDetail.innerHTML = "";
  els.rankingBody.innerHTML = reviewPlayers.map((player, index) => {
    const rank = index + 1;
    const selectedClass = selectedReviewPlayerId === player.playerId ? " selected" : "";
    return `
      <tr class="ranking-row review-player-row${selectedClass}" data-review-player-id="${player.playerId}">
        <td style="padding:.75rem 1rem;font-weight:bold;${rankStyle(rank)}">${rankDisplay(rank)}</td>
        <td style="padding:.75rem 1rem;color:#f3f8ff;">${escapeHtml(player.playerName || "-")}</td>
        <td style="padding:.75rem 1rem;color:#a8b6d6;">${escapeHtml(player.teamName ? `${player.teamState} · ${player.teamName}` : "自由人")}</td>
        <td style="padding:.75rem 1rem;color:#7cffb2;font-weight:700;">${player.value || 0}P</td>
        <td style="padding:.75rem 1rem;color:#7cffb2;font-weight:700;">${player.reviewCount || 0}</td>
        <td style="padding:.75rem 1rem;color:#ffd700;font-weight:700;">
          <div class="review-player-actions">
            <span>${formatNumber(player.totalPopularity ?? player.topPopularity)}</span>
            <button class="btn review-write-btn" type="button" data-open-review-form="${player.playerId}">点评</button>
          </div>
        </td>
      </tr>
      ${selectedReviewPlayerId === player.playerId && reviewDetail ? `
        <tr class="review-inline-row">
          <td colspan="6">${renderReviewDetailContent()}</td>
        </tr>
      ` : ""}
    `;
  }).join("");

  els.rankingCards.innerHTML = reviewPlayers.map((player, index) => `
    <div class="ranking-card review-card-trigger${selectedReviewPlayerId === player.playerId ? " selected" : ""}" data-review-player-id="${player.playerId}" role="button" tabindex="0">
      <span class="ranking-card-rank" style="${rankStyle(index + 1)}">${rankDisplay(index + 1)}</span>
      <span class="ranking-card-main">
        <strong>${escapeHtml(player.playerName || "-")}</strong>
        <small>${escapeHtml(player.teamName ? `${player.teamState} · ${player.teamName}` : "自由人")}</small>
      </span>
      <span class="ranking-card-metric">点评<strong>${player.reviewCount || 0}</strong></span>
      <span class="ranking-card-action">
        <span>身价 ${player.value || 0}P</span>
        <span>总人气 ${formatNumber(player.totalPopularity ?? player.topPopularity)}</span>
        <button class="btn review-write-btn" type="button" data-open-review-form="${player.playerId}">点评</button>
      </span>
    </div>
    ${selectedReviewPlayerId === player.playerId && reviewDetail ? `
      <div class="review-inline-card">${renderReviewDetailContent()}</div>
    ` : ""}
  `).join("");
}

function renderReviewDetailContent() {
  const player = reviewDetail.player || {};
  const loggedIn = Boolean(reviewDetail.currentUserId);
  const showForm = reviewFormPlayerId === selectedReviewPlayerId;
  return `
    <div class="review-detail-panel">
      <div class="review-detail-header">
        <div>
          <h2>${escapeHtml(player.playerName || "选手评价")}</h2>
          <p class="muted">${escapeHtml(player.teamName ? `${player.teamState} · ${player.teamName}` : "自由人")} · ${player.reviewCount || 0} 条点评</p>
        </div>
        <div class="review-detail-stats">
          <span>总人气 <strong>${formatNumber(player.totalPopularity ?? player.topPopularity)}</strong></span>
          <span>最高单评 <strong>${formatNumber(player.topPopularity)}</strong></span>
        </div>
      </div>

      ${showForm ? (loggedIn ? renderReviewForm(player.playerId) : `<div class="review-login-hint">登录后可以发表点评。</div>`) : ""}

      <div class="review-list">
        ${reviewDetail.reviews?.length ? reviewDetail.reviews.map(renderReviewItem).join("") : `<div class="review-empty">还没有点评，来当第一个开麦的人。</div>`}
      </div>
    </div>
  `;
}

function renderReviewForm(playerId) {
  return `
    <form class="review-form" data-player-id="${playerId}">
      <div class="review-field-label">擅长位置：</div>
      <div class="review-position-grid">
        ${Object.entries(POSITION_LABELS).map(([code, label]) => `
          <label><input type="checkbox" name="positions" value="${code}"> ${label}</label>
        `).join("")}
      </div>
      <textarea name="content" rows="4" maxlength="800" placeholder="写下你对这位选手的位置、风格、强弱项或比赛影响力的判断"></textarea>
      <div class="review-form-actions">
        <label class="review-anonymous"><input type="checkbox" name="anonymous"> 匿名发表</label>
        <button class="btn primary" type="submit">发表点评</button>
      </div>
    </form>
  `;
}

function renderReviewItem(review) {
  const confidence = review.ratingCount > 0 ? formatNumber(review.confidenceScore) : "暂无评分";
  const currentRating = review.currentUserRating;
  const positions = (review.positions || []).map(code => `<em>${escapeHtml(POSITION_LABELS[code] || code)}</em>`).join("");
  return `
    <article class="review-item" data-review-id="${review.id}">
      <div class="review-item-head">
        <div class="review-position-tags">
          <span class="review-position-label">擅长位置：</span>
          ${positions || "<em>未标注位置</em>"}
        </div>
        <div class="review-meta">
          <span>${escapeHtml(review.authorDisplayName || "匿名")}</span>
          <span>${escapeHtml(review.createdAt || "")}</span>
        </div>
      </div>
      <p>${escapeHtml(review.content)}</p>
      <div class="review-metrics">
        <span>置信度 <strong>${confidence}</strong></span>
        <span>评分人数 <strong>${review.ratingCount || 0}</strong></span>
        <span>打赏 <strong>${review.tipTotal || 0}P</strong></span>
        <span>人气 <strong>${formatNumber(review.popularityScore)}</strong></span>
      </div>
      <div class="review-actions">
        <div class="review-rating-stars" aria-label="可信度">
          <span>${currentRating == null ? "可信度" : `可信度 ${currentRating} 分`}</span>
          ${[1, 2, 3, 4, 5].map(score => `
            <button
              class="review-star${currentRating != null && score <= currentRating ? " active" : ""}"
              type="button"
              data-rate-review="${review.id}"
              data-rate-score="${score}"
              aria-label="${score}分"
              title="${score}分"
            >★</button>
          `).join("")}
        </div>
        <div class="review-tip-actions">
          ${[10, 50, 100].map(amount => `<button class="btn" type="button" data-tip-review="${review.id}" data-tip-amount="${amount}">${amount}P</button>`).join("")}
        </div>
        ${reviewDetail.admin ? `
          <div class="review-admin-actions">
            <button class="btn" type="button" data-edit-review="${review.id}">编辑</button>
            <button class="btn danger" type="button" data-delete-review="${review.id}">删除</button>
          </div>
        ` : ""}
      </div>
    </article>
  `;
}

function formatNumber(value) {
  const number = Number(value || 0);
  if (Number.isInteger(number)) {
    return String(number);
  }
  return number.toFixed(2).replace(/0+$/, "").replace(/\.$/, "");
}

async function loadReviewPlayers() {
  const data = await getReviewPlayers(reviewSort);
  reviewPlayers = data.players || [];
  if (selectedReviewPlayerId && !reviewPlayers.some(player => player.playerId === selectedReviewPlayerId)) {
    selectedReviewPlayerId = null;
    reviewFormPlayerId = null;
    reviewDetail = null;
  }
}

async function selectReviewPlayer(playerId, options = {}) {
  const nextPlayerId = Number(playerId);
  const samePlayer = selectedReviewPlayerId === nextPlayerId;
  if (samePlayer && !options.showForm) {
    selectedReviewPlayerId = null;
    reviewFormPlayerId = null;
    reviewDetail = null;
    renderReviewRankings();
    return;
  }
  selectedReviewPlayerId = nextPlayerId;
  reviewFormPlayerId = options.showForm ? nextPlayerId : null;
  reviewDetail = await getPlayerReviewDetail(selectedReviewPlayerId);
  renderReviewRankings();
}

async function refreshSelectedReviewPlayer() {
  await loadReviewPlayers();
  if (selectedReviewPlayerId) {
    reviewDetail = await getPlayerReviewDetail(selectedReviewPlayerId);
  }
  renderReviewRankings();
}

async function switchToReviewMode() {
  setMode("reviews");
  els.rankingBody.innerHTML = `<tr><td colspan="5" style="padding:1rem;" class="muted">正在加载选手评价...</td></tr>`;
  await loadReviewPlayers();
  renderReviewRankings();
}

function bindEvents() {
  els.showValueRankings.addEventListener("click", () => {
    setMode("value");
    renderRankings();
  });

  els.showDepositRankings.addEventListener("click", () => {
    setMode("deposit");
    renderRankings();
  });

  els.showReviewRankings.addEventListener("click", async () => {
    try {
      await switchToReviewMode();
    } catch (error) {
      showRankingError(error);
    }
  });

  els.reviewSortControls.addEventListener("click", async event => {
    const button = event.target.closest("[data-review-sort]");
    if (!button) return;
    reviewSort = button.dataset.reviewSort;
    try {
      await switchToReviewMode();
    } catch (error) {
      showRankingError(error);
    }
  });

  els.rankingBody.addEventListener("click", async event => {
    const writeButton = event.target.closest("[data-open-review-form]");
    if (writeButton && currentMode === "reviews") {
      try {
        await selectReviewPlayer(writeButton.dataset.openReviewForm, { showForm: true });
      } catch (error) {
        alert(error.message);
      }
      return;
    }
    const row = event.target.closest("[data-review-player-id]");
    if (!row || currentMode !== "reviews") return;
    try {
      await selectReviewPlayer(row.dataset.reviewPlayerId);
    } catch (error) {
      alert(error.message);
    }
  });

  els.rankingCards.addEventListener("click", async event => {
    const writeButton = event.target.closest("[data-open-review-form]");
    if (writeButton && currentMode === "reviews") {
      try {
        await selectReviewPlayer(writeButton.dataset.openReviewForm, { showForm: true });
      } catch (error) {
        alert(error.message);
      }
      return;
    }
    const card = event.target.closest("[data-review-player-id]");
    if (!card || currentMode !== "reviews") return;
    try {
      await selectReviewPlayer(card.dataset.reviewPlayerId);
    } catch (error) {
      alert(error.message);
    }
  });

  document.addEventListener("submit", async event => {
    if (!event.target.matches(".review-form")) return;
    event.preventDefault();
    const formData = new FormData(event.target);
    const positions = formData.getAll("positions");
    const content = String(formData.get("content") || "").trim();
    const anonymous = formData.get("anonymous") === "on";
    if (!positions.length) {
      alert("请选择至少一个擅长位置");
      return;
    }
    if (!content) {
      alert("请填写点评内容");
      return;
    }
    try {
      await createPlayerReview(selectedReviewPlayerId, { positions, content, anonymous });
      event.target.reset();
      reviewFormPlayerId = null;
      await refreshSelectedReviewPlayer();
    } catch (error) {
      alert(error.message);
    }
  });

  document.addEventListener("click", async event => {
    const starButton = event.target.closest("[data-rate-score]");
    const tipButton = event.target.closest("[data-tip-review]");
    const editButton = event.target.closest("[data-edit-review]");
    const deleteButton = event.target.closest("[data-delete-review]");
    try {
      if (starButton) {
        await rateReview(Number(starButton.dataset.rateReview), Number(starButton.dataset.rateScore));
        await refreshSelectedReviewPlayer();
      } else if (tipButton) {
        const amount = Number(tipButton.dataset.tipAmount);
        if (!window.confirm(`确认打赏 ${amount}P？其中一半会转给点评作者，并增加该点评的人气值。`)) {
          return;
        }
        await tipReview(Number(tipButton.dataset.tipReview), amount);
        await refreshSelectedReviewPlayer();
      } else if (editButton) {
        await handleEditReview(Number(editButton.dataset.editReview));
      } else if (deleteButton) {
        await handleDeleteReview(Number(deleteButton.dataset.deleteReview));
      }
    } catch (error) {
      alert(error.message);
    }
  });
}

async function handleEditReview(reviewId) {
  const review = reviewDetail.reviews.find(item => item.id === reviewId);
  if (!review) return;
  const content = window.prompt("编辑点评内容", review.content || "");
  if (content == null) return;
  const positions = window.prompt("编辑位置代码，用逗号分隔：TOP,JUG,MID,BOT,SUP", (review.positions || []).join(","));
  if (positions == null) return;
  const anonymous = window.confirm("是否匿名展示？点击确定为匿名，取消为实名。");
  await updateReview(reviewId, {
    content,
    positions: positions.split(",").map(item => item.trim()).filter(Boolean),
    anonymous
  });
  await refreshSelectedReviewPlayer();
}

async function handleDeleteReview(reviewId) {
  if (!window.confirm("确认删除这条点评？")) return;
  await deleteReview(reviewId);
  await refreshSelectedReviewPlayer();
}

function showRankingError(error) {
  els.rankingBody.innerHTML = `<tr><td colspan="5" style="padding:1rem;color:#ff9f9f;">加载失败：${escapeHtml(error.message)}</td></tr>`;
  els.rankingCards.innerHTML = "";
}

async function init() {
  bindEls();
  setMode("value");
  try {
    [players, teams] = await Promise.all([getPlayers(), getTeams()]);
    renderRankings();
    bindEvents();
  } catch (error) {
    showRankingError(error);
  }
}

document.addEventListener("DOMContentLoaded", init);
