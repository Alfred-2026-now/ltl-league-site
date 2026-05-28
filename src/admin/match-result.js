import {
  createResultDraft,
  deleteAttachment,
  getAdminMatch,
  getMatchResult,
  getTeams,
  publishResult,
  updateResultDraft,
  uploadGameScreenshot,
  withdrawResult
} from "./api.js";

const params = new URLSearchParams(window.location.search);
const matchId = params.get("matchId");

let els = {};

function bindEls() {
  els = {
    pageTitle: document.getElementById("pageTitle"),
    matchSummary: document.getElementById("matchSummary"),
    statusHint: document.getElementById("statusHint"),
    resultType: document.getElementById("resultType"),
    homeScore: document.getElementById("homeScore"),
    awayScore: document.getElementById("awayScore"),
    winnerTeamId: document.getElementById("winnerTeamId"),
    homePoints: document.getElementById("homePoints"),
    awayPoints: document.getElementById("awayPoints"),
    resultNotes: document.getElementById("resultNotes"),
    tieHint: document.getElementById("tieHint"),
    gamesContainer: document.getElementById("gamesContainer"),
    saveDraftBtn: document.getElementById("saveDraftBtn"),
    publishBtn: document.getElementById("publishBtn"),
    withdrawBtn: document.getElementById("withdrawBtn"),
    resultFormPanel: document.getElementById("resultFormPanel"),
    withdrawDialog: document.getElementById("withdrawDialog"),
    withdrawReason: document.getElementById("withdrawReason"),
    cancelWithdrawBtn: document.getElementById("cancelWithdrawBtn"),
    confirmWithdrawBtn: document.getElementById("confirmWithdrawBtn")
  };
}

function setVal(el, value) {
  if (!el) return;
  el.value = value ?? "";
}

let match = null;
let teams = [];
let resultCtx = null;
let readOnly = false;

function maxGamesForFormat(format) {
  const f = (format || "").toUpperCase();
  if (f === "BO1") return 1;
  if (f === "BO2") return 2;
  if (f === "BO3") return 3;
  if (f === "BO5") return 5;
  return 3;
}

/** 根据已填总比分计算实际需要录入的小局数（BO3 2:0 只需 2 局） */
function requiredGameSlots() {
  const fmt = (match?.format || "").toUpperCase();
  const max = maxGamesForFormat(fmt);
  const hs = els.homeScore?.value;
  const as = els.awayScore?.value;
  if (hs === "" || hs == null || as === "" || as == null) return max;
  const home = Number(hs);
  const away = Number(as);
  if (!Number.isFinite(home) || !Number.isFinite(away) || home < 0 || away < 0) return max;
  const total = home + away;
  if (total <= 0) return max;
  if (fmt === "BO2" && home === 1 && away === 1) return 2;
  return Math.min(max, total);
}

function teamOptionsHtml() {
  if (!match) return "";
  const home = teams.find(t => String(t.id) === String(match.homeTeamId));
  const away = teams.find(t => String(t.id) === String(match.awayTeamId));
  const opts = ['<option value="">（平局无胜方）</option>'];
  if (home) opts.push(`<option value="${home.id}">${home.state} · ${home.name}（主）</option>`);
  if (away) opts.push(`<option value="${away.id}">${away.state} · ${away.name}（客）</option>`);
  return opts.join("");
}

function stateOptions() {
  const home = teams.find(t => String(t.id) === String(match.homeTeamId));
  const away = teams.find(t => String(t.id) === String(match.awayTeamId));
  return [home?.state, away?.state].filter(Boolean);
}

function isDraftEditable() {
  return resultCtx?.status === "draft" && resultCtx?.readOnly !== true;
}

/** 秒 → 分:秒（如 1935 → "32:15"） */
function formatDurationInput(seconds) {
  if (seconds == null || seconds === "") return "";
  const total = Number(seconds);
  if (!Number.isFinite(total) || total < 0) return "";
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

/** 分:秒 → 秒；支持纯数字秒 */
function parseDurationInput(value) {
  const raw = String(value ?? "").trim();
  if (!raw) return null;
  if (/^\d+$/.test(raw)) return Number(raw);
  const m = raw.match(/^(\d+):(\d{1,2})$/);
  if (!m) return null;
  const minutes = Number(m[1]);
  const secs = Number(m[2]);
  if (!Number.isFinite(minutes) || !Number.isFinite(secs) || secs >= 60) return null;
  return minutes * 60 + secs;
}

function renderGames(games = []) {
  if (!els.gamesContainer) return;
  const slots = requiredGameSlots();
  const states = stateOptions();
  const byIndex = new Map((games || []).map(g => [g.gameIndex, g]));

  els.gamesContainer.innerHTML = Array.from({ length: slots }, (_, i) => {
    const idx = i + 1;
    const g = byIndex.get(idx) || {};
    const shots = g.screenshots || [];
    const stateOpts = states.map(s => `<option value="${s}">${s}</option>`).join("");
    return `
      <div class="panel" style="padding:.75rem; margin-bottom:.5rem;" data-game-index="${idx}">
        <strong>第 ${idx} 局</strong>
        <div style="display:grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap:.5rem; margin-top:.5rem;">
          <label class="field"><span class="field-label">胜方</span>
            <select class="input game-winner">${stateOpts}</select></label>
          <label class="field"><span class="field-label">蓝方</span>
            <select class="input game-blue">${stateOpts}</select></label>
          <label class="field"><span class="field-label">红方</span>
            <select class="input game-red">${stateOpts}</select></label>
          <label class="field"><span class="field-label">时长(分:秒)</span>
            <input class="input game-duration" type="text" inputmode="numeric" placeholder="32:15" value="${formatDurationInput(g.durationSeconds)}" /></label>
        </div>
        <div class="game-screenshots" style="margin-top:.5rem;">
          ${shots.map(s => `
            <div style="display:flex;align-items:center;gap:.5rem;margin:.25rem 0;">
              <a href="${s.url}" target="_blank" rel="noreferrer">${s.label || "截图"}</a>
              ${isDraftEditable() ? `<button type="button" class="btn ghost" data-del-att="${s.id}">删除</button>` : ""}
            </div>
          `).join("")}
          ${isDraftEditable() ? `<input type="file" accept="image/*" class="game-upload" data-game-index="${idx}" />` : ""}
        </div>
      </div>
    `;
  }).join("");

  els.gamesContainer.querySelectorAll("[data-game-index]").forEach(panel => {
    const idx = Number(panel.dataset.gameIndex);
    const g = byIndex.get(idx);
    if (!g) return;
    setVal(panel.querySelector(".game-winner"), g.winner);
    setVal(panel.querySelector(".game-blue"), g.blueTeam);
    setVal(panel.querySelector(".game-red"), g.redTeam);
    setVal(panel.querySelector(".game-duration"), formatDurationInput(g.durationSeconds));
  });
}

function updateTieUi() {
  if (!els.homeScore || !els.awayScore || !els.winnerTeamId) return;
  const isBo2 = (match?.format || "").toUpperCase() === "BO2";
  const hs = Number(els.homeScore.value);
  const as = Number(els.awayScore.value);
  const isTie = isBo2 && hs === 1 && as === 1;
  if (els.tieHint) els.tieHint.style.display = isTie ? "block" : "none";
  if (isTie) {
    setVal(els.winnerTeamId, "");
    els.winnerTeamId.disabled = true;
  } else if (!readOnly) {
    els.winnerTeamId.disabled = false;
  }
}

function collectGames() {
  const games = [];
  if (!els.gamesContainer) return games;
  els.gamesContainer.querySelectorAll("[data-game-index]").forEach(panel => {
    const winner = panel.querySelector(".game-winner")?.value;
    const blue = panel.querySelector(".game-blue")?.value;
    const red = panel.querySelector(".game-red")?.value;
    if (!winner && !blue && !red) return;
    games.push({
      gameIndex: Number(panel.dataset.gameIndex),
      winner,
      blueTeam: blue,
      redTeam: red,
      durationSeconds: parseDurationInput(panel.querySelector(".game-duration")?.value)
    });
  });
  return games;
}

function collectPayload() {
  return {
    resultType: els.resultType?.value || "normal",
    homeScore: els.homeScore?.value !== "" ? Number(els.homeScore.value) : null,
    awayScore: els.awayScore?.value !== "" ? Number(els.awayScore.value) : null,
    winnerTeamId: els.winnerTeamId?.value ? Number(els.winnerTeamId.value) : null,
    homePoints: els.homePoints?.value !== "" ? Number(els.homePoints.value) : null,
    awayPoints: els.awayPoints?.value !== "" ? Number(els.awayPoints.value) : null,
    notes: els.resultNotes?.value || null,
    games: collectGames()
  };
}

function fillForm(ctx) {
  if (!ctx) return;
  setVal(els.resultType, ctx.resultType || "normal");
  setVal(els.homeScore, ctx.homeScore ?? "");
  setVal(els.awayScore, ctx.awayScore ?? "");
  if (els.winnerTeamId) {
    els.winnerTeamId.innerHTML = teamOptionsHtml();
    setVal(els.winnerTeamId, ctx.winnerTeamId ? String(ctx.winnerTeamId) : "");
  }
  setVal(els.homePoints, ctx.homePoints ?? "");
  setVal(els.awayPoints, ctx.awayPoints ?? "");
  setVal(els.resultNotes, ctx.notes ?? "");
  renderGames(ctx.games || []);
  updateTieUi();
}

function setReadOnly(ro) {
  readOnly = ro;
  if (!els.resultFormPanel) return;
  els.resultFormPanel.querySelectorAll("input, select, textarea").forEach(el => {
    if (el.classList.contains("game-upload")) return;
    el.disabled = ro;
  });
  if (els.saveDraftBtn) els.saveDraftBtn.style.display = ro ? "none" : "";
  if (els.publishBtn) els.publishBtn.style.display = ro ? "none" : "";
  if (els.withdrawBtn) {
    els.withdrawBtn.style.display = ro && resultCtx?.status === "published" ? "" : "none";
  }
}

function applyStatusUi() {
  const st = resultCtx?.status || "none";
  const isPublished = st === "published" && resultCtx?.readOnly !== false;
  if (isPublished) {
    if (els.statusHint) {
      els.statusHint.textContent = `已发布赛果 v${resultCtx.versionNo}。撤回后将自动保留为可编辑草稿。`;
    }
    setReadOnly(true);
  } else if (st === "draft") {
    if (els.statusHint) {
      els.statusHint.textContent = `草稿 v${resultCtx.versionNo}，前台不可见。`;
    }
    setReadOnly(false);
  } else {
    if (els.statusHint) els.statusHint.textContent = "尚无赛果，请填写后保存草稿。";
    setReadOnly(false);
  }
}

async function ensureDraft() {
  if (resultCtx?.id && resultCtx.status === "draft") {
    return updateResultDraft(matchId, resultCtx.id, collectPayload());
  }
  return createResultDraft(matchId, collectPayload());
}

async function persistDraftForUpload(gameIndex) {
  const panel = els.gamesContainer?.querySelector(`[data-game-index="${gameIndex}"]`);
  const winner = panel?.querySelector(".game-winner")?.value;
  const blue = panel?.querySelector(".game-blue")?.value;
  const red = panel?.querySelector(".game-red")?.value;
  if (!winner || !blue || !red) {
    throw new Error(`请先填写第 ${gameIndex} 局的胜方、蓝方、红方`);
  }
  const hasGameRow = resultCtx?.status === "draft"
    && resultCtx?.games?.some(g => g.gameIndex === gameIndex && g.id);
  if (hasGameRow) {
    return resultCtx;
  }
  return ensureDraft();
}

async function saveDraft() {
  try {
    resultCtx = await ensureDraft();
    applyStatusUi();
    renderGames(resultCtx.games || []);
    alert("草稿已保存");
  } catch (e) {
    alert(`保存失败：${e.message}`);
  }
}

async function publish() {
  if (!confirm("确认发布赛果？发布后前台将展示比分与小局。")) return;
  try {
    if (!resultCtx?.id || resultCtx.status !== "draft") {
      resultCtx = await ensureDraft();
    }
    await publishResult(matchId, resultCtx.id);
    resultCtx = await getMatchResult(matchId);
    applyStatusUi();
    fillForm(resultCtx);
    alert("赛果已发布");
  } catch (e) {
    console.error(e);
    alert(`发布失败：${e.message}`);
  }
}

async function doWithdraw() {
  const reason = els.withdrawReason.value.trim();
  if (!reason) {
    alert("请填写撤回原因");
    return;
  }
  try {
    resultCtx = await withdrawResult(matchId, resultCtx.id, reason);
    els.withdrawDialog.close();
    setVal(els.withdrawReason, "");
    if (els.winnerTeamId) els.winnerTeamId.innerHTML = teamOptionsHtml();
    applyStatusUi();
    fillForm(resultCtx);
    alert("赛果已撤回，内容已保留为草稿，修改后可直接发布。");
  } catch (e) {
    alert(`撤回失败：${e.message}`);
  }
}

function onScoreInput() {
  updateTieUi();
  if (readOnly) return;
  const preserved = collectGames();
  renderGames(preserved);
}

function wireEvents() {
  els.homeScore?.addEventListener("input", onScoreInput);
  els.awayScore?.addEventListener("input", onScoreInput);
  els.resultType?.addEventListener("change", () => {
    if (els.resultType.value === "forfeit" && !readOnly) {
      const home = teams.find(t => String(t.id) === String(match.homeTeamId));
      if (home) els.winnerTeamId.value = String(home.id);
    }
  });

  els.saveDraftBtn?.addEventListener("click", saveDraft);
  els.publishBtn?.addEventListener("click", publish);
  els.withdrawBtn?.addEventListener("click", () => els.withdrawDialog.showModal());
  els.cancelWithdrawBtn?.addEventListener("click", () => els.withdrawDialog.close());
  els.confirmWithdrawBtn?.addEventListener("click", doWithdraw);

  els.gamesContainer?.addEventListener("click", async e => {
    const del = e.target.closest("[data-del-att]");
    if (del) {
      if (!confirm("删除该截图？")) return;
      try {
        await deleteAttachment(del.dataset.delAtt);
        resultCtx = await getMatchResult(matchId);
        applyStatusUi();
        fillForm(resultCtx);
      } catch (err) {
        alert(err.message);
      }
    }
  });

  els.gamesContainer?.addEventListener("change", async e => {
    const input = e.target.closest(".game-upload");
    if (!input?.files?.[0]) return;
    const gameIndex = Number(input.dataset.gameIndex);
    try {
      resultCtx = await persistDraftForUpload(gameIndex);
      await uploadGameScreenshot(matchId, resultCtx.id, gameIndex, input.files[0]);
      input.value = "";
      resultCtx = await getMatchResult(matchId);
      applyStatusUi();
      fillForm(resultCtx);
    } catch (err) {
      alert(`上传失败：${err.message}`);
    }
  });
}

async function init() {
  bindEls();
  if (!matchId) {
    alert("缺少 matchId 参数");
    window.location.href = "admin-matches.html";
    return;
  }
  try {
    teams = await getTeams();
    match = await getAdminMatch(matchId);
    const home = teams.find(t => String(t.id) === String(match.homeTeamId));
    const away = teams.find(t => String(t.id) === String(match.awayTeamId));
    els.pageTitle.textContent = `赛果录入 · ${match.roundLabel || "第" + match.round + "轮"}`;
    els.matchSummary.textContent = `${home?.state || ""} vs ${away?.state || ""} · ${match.format} · 赛季 ${match.season}`;

    resultCtx = await getMatchResult(matchId);
    if (els.winnerTeamId) els.winnerTeamId.innerHTML = teamOptionsHtml();
    applyStatusUi();
    if (resultCtx?.id && resultCtx.status !== "none") {
      fillForm(resultCtx);
    } else {
      renderGames([]);
    }
    wireEvents();
  } catch (e) {
    console.error(e);
    alert(`加载失败：${e.message}`);
  }
}

document.addEventListener("DOMContentLoaded", init);
