import {
  createResultDraft,
  deleteAttachment,
  getAdminMatch,
  getAllTeams,
  getCurrentTeams,
  getMatchResult,
  getPlayers,
  listPLedgers,
  listValuationChanges,
  previewResultSettlement,
  publishResult,
  updateResultDraft,
  uploadGameScreenshot,
  withdrawResult
} from "./api.js";
import {
  appearanceSlotTotal,
  calculateWeightedLineValue,
  formatLineValue,
  totalGamesFromScores
} from "./lineup-value.js";

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
    taxExempt: document.getElementById("taxExempt"),
    homeLineValue: document.getElementById("homeLineValue"),
    awayLineValue: document.getElementById("awayLineValue"),
    homeLineupTitle: document.getElementById("homeLineupTitle"),
    awayLineupTitle: document.getElementById("awayLineupTitle"),
    homeRosterAppearances: document.getElementById("homeRosterAppearances"),
    awayRosterAppearances: document.getElementById("awayRosterAppearances"),
    homeLoanAppearances: document.getElementById("homeLoanAppearances"),
    awayLoanAppearances: document.getElementById("awayLoanAppearances"),
    addHomeLoanAppearanceBtn: document.getElementById("addHomeLoanAppearanceBtn"),
    addAwayLoanAppearanceBtn: document.getElementById("addAwayLoanAppearanceBtn"),
    loanInputs: document.getElementById("loanInputs"),
    valuationInputs: document.getElementById("valuationInputs"),
    addLoanBtn: document.getElementById("addLoanBtn"),
    addValuationBtn: document.getElementById("addValuationBtn"),
    previewSettlementBtn: document.getElementById("previewSettlementBtn"),
    settlementPreview: document.getElementById("settlementPreview"),
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
let currentTeams = [];
let players = [];
let resultCtx = null;
let readOnly = false;
let previewTimer = null;
const ADVANTAGE_TIERS = [1000, 1500, 2000, 2500, 3000];

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

function canEditResult() {
  return !readOnly && resultCtx?.readOnly !== true && resultCtx?.status !== "published";
}

function canUploadScreenshots() {
  return canEditResult() || resultCtx?.status === "published";
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
  const canUpload = canUploadScreenshots();
  const emptyScreenshotHint = resultCtx?.status === "published"
    ? "暂无截图，上传后会追加到已发布赛果，其他已录入数据不会被修改。"
    : "暂无截图，上传后保存草稿并发布赛果，前台战绩详情会展示图片。";

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
          <div style="display:flex;align-items:center;justify-content:space-between;gap:.75rem;flex-wrap:wrap;margin-bottom:.5rem;">
            <strong>战绩截图</strong>
            ${canUpload ? `
              <label class="btn ghost" style="cursor:pointer;">
                上传第 ${idx} 局截图
                <input type="file" accept="image/*" class="game-upload" data-game-index="${idx}" style="position:absolute;width:1px;height:1px;opacity:0;pointer-events:none;" />
              </label>
            ` : ""}
          </div>
          ${shots.map(s => `
            <div style="display:flex;align-items:center;gap:.5rem;margin:.25rem 0;">
              <a href="${s.url}" target="_blank" rel="noreferrer">${s.label || "截图"}</a>
              ${isDraftEditable() ? `<button type="button" class="btn ghost" data-del-att="${s.id}">删除</button>` : ""}
            </div>
          `).join("")}
          ${!shots.length ? `<p class="muted" style="margin:.25rem 0 0;">${emptyScreenshotHint}</p>` : ""}
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

function teamSelectOptions(selectedId = "", onlyMatchTeams = false) {
  const source = onlyMatchTeams && match
    ? teams.filter(t => String(t.id) === String(match.homeTeamId) || String(t.id) === String(match.awayTeamId))
    : currentTeams;
  return [`<option value="">请选择</option>`, ...source.map(t => `
    <option value="${t.id}" ${String(t.id) === String(selectedId) ? "selected" : ""}>${t.state} · ${t.name}</option>
  `)].join("");
}

function playerSelectOptions(selectedId = "") {
  return [`<option value="">请选择选手</option>`, ...players.map(p => `
    <option value="${p.id}" ${String(p.id) === String(selectedId) ? "selected" : ""}>${p.name} · ${p.value ?? 0}P</option>
  `)].join("");
}

function activeTeamPlayers(teamId) {
  return players.filter(p => String(p.teamId) === String(teamId) && p.status === 1 && !p.isLoan);
}

function replacedPlayerSelectOptions(teamId, selectedId = "", excludedPlayerId = "") {
  const teamPlayers = teamId
    ? activeTeamPlayers(teamId).filter(p => String(p.id) !== String(excludedPlayerId || ""))
    : [];
  if (!teamPlayers.length) {
    return `<option value="">请先选择使用队伍</option>`;
  }
  return [`<option value="">选择被替换选手</option>`, ...teamPlayers.map(p => `
    <option value="${p.id}" ${String(p.id) === String(selectedId) ? "selected" : ""}>${p.name} · ${p.value ?? 0}P</option>
  `)].join("");
}

function numberOrNull(value) {
  return value !== "" && value != null ? Number(value) : null;
}

function matchTeamIds() {
  if (!match) return [];
  return [match.homeTeamId, match.awayTeamId].filter(id => id != null).map(id => String(id));
}

function inferPayingTeamIdForLoanPlayer(player) {
  if (!player || player.teamId == null || !match) return null;
  const ids = matchTeamIds();
  const playerTeamId = String(player.teamId);
  if (!ids.includes(playerTeamId)) return null;
  return ids.find(id => id !== playerTeamId) || null;
}

function refreshReplacedPlayerOptions(row) {
  const payingTeamId = row?.querySelector(".loan-paying-team")?.value || "";
  const loanPlayerId = row?.querySelector(".loan-player")?.value || "";
  const replacedSelect = row?.querySelector(".loan-replaced-player");
  if (!replacedSelect) return;
  const previousValue = replacedSelect.value;
  replacedSelect.innerHTML = replacedPlayerSelectOptions(payingTeamId, previousValue, loanPlayerId);
  const stillAvailable = Array.from(replacedSelect.options).some(option => option.value === previousValue);
  if (!stillAvailable) {
    replacedSelect.value = "";
  }
}

function syncLoanPlayerFields(row) {
  const playerSelect = row?.querySelector(".loan-player");
  if (!playerSelect) return;
  const player = players.find(p => String(p.id) === String(playerSelect.value));
  if (!player) {
    refreshReplacedPlayerOptions(row);
    return;
  }

  const valueInput = row.querySelector(".loan-value");
  if (valueInput && !valueInput.value) {
    valueInput.value = player.value ?? 0;
  }

  const sourceTypeSelect = row.querySelector(".loan-source-type");
  const sourceTeamSelect = row.querySelector(".loan-source-team");
  if (sourceTypeSelect) {
    sourceTypeSelect.value = player.teamId == null ? "free_agent" : "original_team";
  }
  if (sourceTeamSelect) {
    sourceTeamSelect.value = player.teamId == null ? "" : String(player.teamId);
  }

  const payingTeamSelect = row.querySelector(".loan-paying-team");
  if (payingTeamSelect && !payingTeamSelect.value) {
    const inferredPayingTeamId = inferPayingTeamIdForLoanPlayer(player);
    if (inferredPayingTeamId) {
      payingTeamSelect.value = inferredPayingTeamId;
    }
  }

  refreshReplacedPlayerOptions(row);
}

function currentTotalGames() {
  return totalGamesFromScores(els.homeScore?.value, els.awayScore?.value);
}

function currentAdvantageGameLimit() {
  return maxGamesForFormat(match?.format);
}

function sideConfig(side) {
  const home = side === "home";
  return {
    teamId: home ? match?.homeTeamId : match?.awayTeamId,
    lineValue: home ? els.homeLineValue : els.awayLineValue,
    title: home ? els.homeLineupTitle : els.awayLineupTitle,
    rosterContainer: home ? els.homeRosterAppearances : els.awayRosterAppearances,
    loanContainer: home ? els.homeLoanAppearances : els.awayLoanAppearances,
    section: document.querySelector(`[data-lineup-side="${side}"]`)
  };
}

function teamDisplayName(teamId) {
  const team = teams.find(row => String(row.id) === String(teamId));
  return team ? `${team.state} · ${team.name}` : "队伍";
}

function appearancePlayerOptions(teamId, selectedId = "") {
  const candidates = players.filter(player => {
    const ownRoster = String(player.teamId) === String(teamId)
      && player.status === 1
      && !player.isLoan;
    return player.status === 1 && !ownRoster;
  });
  return [
    `<option value="">请选择租借选手</option>`,
    ...candidates.map(player => {
      const sourceTeam = teams.find(team => String(team.id) === String(player.teamId));
      const source = sourceTeam ? ` · ${sourceTeam.state}` : " · 自由人";
      return `<option value="${player.id}" ${String(player.id) === String(selectedId) ? "selected" : ""}>${player.name}${source} · ${player.value ?? 0}P</option>`;
    })
  ].join("");
}

function advantageTierControls(selectedTiers = []) {
  const selected = new Set((selectedTiers || []).map(Number));
  return `
    <fieldset class="advantage-tier-fieldset" style="border:0;padding:0;margin:.5rem 0 0;">
      <legend class="field-label">是否使用身价差优势规则</legend>
      <div style="display:flex;flex-wrap:wrap;gap:.35rem .65rem;margin-top:.3rem;">
        ${ADVANTAGE_TIERS.map(tier => `
          <label style="display:flex;align-items:center;gap:.25rem;">
            <input class="advantage-tier" type="checkbox" value="${tier}" ${selected.has(tier) ? "checked" : ""} />
            <span>${tier}</span>
          </label>
        `).join("")}
      </div>
      <p class="muted advantage-tier-summary" style="margin:.35rem 0 0;"></p>
    </fieldset>
  `;
}

function selectedAdvantageTiers(row) {
  return Array.from(row?.querySelectorAll(".advantage-tier:checked") || []).map(input => Number(input.value));
}

function syncAdvantageTierState(row) {
  if (!row) return;
  const advantageGameLimit = currentAdvantageGameLimit();
  const selected = selectedAdvantageTiers(row);
  const rosterRow = row.closest("[data-roster-appearance]");
  const loanRow = row.closest("[data-loan-appearance]");
  const isActive = rosterRow
    ? rosterRow.querySelector(".appearance-enabled")?.checked === true
    : Boolean(loanRow?.querySelector(".appearance-player")?.value);
  row.querySelectorAll(".advantage-tier").forEach(input => {
    input.disabled = readOnly
      || els.taxExempt?.checked === true
      || !isActive
      || (!input.checked && selected.length >= advantageGameLimit);
  });
  const summary = row.querySelector(".advantage-tier-summary");
  if (!summary) return;
  const rawValue = Number(row.dataset.playerValue || 0);
  const foldedValue = rawValue - selected.reduce((sum, tier) => sum + tier, 0) / advantageGameLimit;
  summary.textContent = `已选 ${selected.length} / ${advantageGameLimit} 项 · 本场折算身价 ${formatLineValue(foldedValue)}P`;
  summary.style.color = selected.length > advantageGameLimit || foldedValue < 0 ? "#ff9f9f" : "";
}

function syncAllAdvantageTierStates() {
  document.querySelectorAll("[data-roster-appearance], [data-loan-appearance]").forEach(syncAdvantageTierState);
}

function renderRosterAppearances(side, savedRows = []) {
  const config = sideConfig(side);
  if (!config.rosterContainer) return;
  const savedByPlayer = new Map(savedRows
    .filter(row => row.playerType !== "loan")
    .map(row => [String(row.playerId), row]));
  const roster = activeTeamPlayers(config.teamId);
  const totalGames = currentTotalGames();
  if (!roster.length) {
    config.rosterContainer.innerHTML = `<p class="muted">当前没有可选择的本队在职选手。</p>`;
    return;
  }
  config.rosterContainer.innerHTML = `
    <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:.45rem;">
      ${roster.map(player => {
        const saved = savedByPlayer.get(String(player.id));
        const checked = Boolean(saved);
        return `
          <div class="panel" data-roster-appearance data-player-id="${player.id}" data-player-value="${player.value ?? 0}" style="padding:.5rem;">
            <label style="display:flex;align-items:center;gap:.45rem;">
              <input class="appearance-enabled" type="checkbox" ${checked ? "checked" : ""} />
              <span>${player.name} · ${player.value ?? 0}P</span>
            </label>
            <label class="field" style="margin-top:.4rem;">
              <span class="field-label">出场局数</span>
              <input class="input appearance-games" type="number" min="1" max="${totalGames || 1}"
                value="${saved?.gamesPlayed ?? ""}" ${checked ? "" : "disabled"} />
            </label>
            ${advantageTierControls(saved?.advantageTiers || [])}
          </div>
        `;
      }).join("")}
    </div>
    <p class="muted lineup-slot-summary" style="margin:.5rem 0 0;"></p>
  `;
}

function renderLoanAppearances(side, savedRows = []) {
  const config = sideConfig(side);
  if (!config.loanContainer) return;
  const rows = savedRows.filter(row => row.playerType === "loan");
  const totalGames = currentTotalGames();
  config.loanContainer.innerHTML = rows.length ? rows.map((row, index) => `
    <div class="panel" data-loan-appearance data-player-value="${players.find(player => String(player.id) === String(row.playerId))?.value ?? row.playerValue ?? 0}" style="padding:.5rem;margin-top:.45rem;">
      <div style="display:grid;grid-template-columns:minmax(0,1fr) 120px auto;gap:.45rem;align-items:end;">
        <label class="field"><span class="field-label">租借选手</span>
          <select class="input appearance-player">${appearancePlayerOptions(config.teamId, row.playerId)}</select>
        </label>
        <label class="field"><span class="field-label">出场局数</span>
          <input class="input appearance-games" type="number" min="1" max="${totalGames || 1}" value="${row.gamesPlayed ?? ""}" />
        </label>
        <button class="btn ghost remove-loan-appearance" type="button" data-index="${index}">删除</button>
      </div>
      ${advantageTierControls(row.advantageTiers || [])}
    </div>
  `).join("") : `<p class="muted" style="margin:.5rem 0 0;">本场暂无租借选手出场。租借选手还须在下方“租借费”中登记。</p>`;
}

function collectSideAppearances(side) {
  const config = sideConfig(side);
  const appearances = [];
  config.rosterContainer?.querySelectorAll("[data-roster-appearance]").forEach(row => {
    if (!row.querySelector(".appearance-enabled")?.checked) return;
    appearances.push({
      playerId: Number(row.dataset.playerId),
      playerType: "roster",
      gamesPlayed: numberOrNull(row.querySelector(".appearance-games")?.value),
      playerValue: Number(row.dataset.playerValue || 0),
      advantageTiers: selectedAdvantageTiers(row)
    });
  });
  config.loanContainer?.querySelectorAll("[data-loan-appearance]").forEach(row => {
    const playerId = numberOrNull(row.querySelector(".appearance-player")?.value);
    if (!playerId) return;
    const player = players.find(candidate => String(candidate.id) === String(playerId));
    appearances.push({
      playerId,
      playerType: "loan",
      gamesPlayed: numberOrNull(row.querySelector(".appearance-games")?.value),
      playerValue: Number(player?.value || 0),
      advantageTiers: selectedAdvantageTiers(row)
    });
  });
  return appearances;
}

function syncLineupValue(side, fallbackValue = null) {
  const config = sideConfig(side);
  const appearances = collectSideAppearances(side);
  const totalGames = currentTotalGames();
  const value = appearances.length
    ? calculateWeightedLineValue(appearances, totalGames, currentAdvantageGameLimit())
    : fallbackValue;
  setVal(config.lineValue, value == null ? "" : formatLineValue(value));
  const slots = appearanceSlotTotal(appearances);
  const required = totalGames > 0 ? totalGames * 5 : 0;
  const summary = config.rosterContainer?.querySelector(".lineup-slot-summary");
  if (summary) {
    summary.textContent = totalGames > 0
      ? `出场局数合计：${slots} / ${required}`
      : "请先填写主客队比分，系统才能确定本场总局数。";
    summary.style.color = required > 0 && slots !== required ? "#ffc857" : "";
  }
}

function syncAllLineupValues(fallback = {}) {
  syncLineupValue("home", fallback.homeLineValue ?? null);
  syncLineupValue("away", fallback.awayLineValue ?? null);
}

function renderLineups(settlement = {}) {
  const homeTeamId = match?.homeTeamId;
  const awayTeamId = match?.awayTeamId;
  if (els.homeLineupTitle) els.homeLineupTitle.textContent = `主队 · ${teamDisplayName(homeTeamId)} 出场选手`;
  if (els.awayLineupTitle) els.awayLineupTitle.textContent = `客队 · ${teamDisplayName(awayTeamId)} 出场选手`;
  renderRosterAppearances("home", settlement.homeAppearances || []);
  renderLoanAppearances("home", settlement.homeAppearances || []);
  renderRosterAppearances("away", settlement.awayAppearances || []);
  renderLoanAppearances("away", settlement.awayAppearances || []);
  syncAllLineupValues(settlement);
  syncAllAdvantageTierStates();
}

function updateAppearanceGameLimits() {
  const totalGames = currentTotalGames();
  document.querySelectorAll("[data-lineup-side] .appearance-games").forEach(input => {
    input.max = String(totalGames || 1);
    if (totalGames > 0 && Number(input.value) > totalGames) {
      input.value = String(totalGames);
    }
  });
  syncAllAdvantageTierStates();
  syncAllLineupValues();
}

function renderSettlement(settlement = {}) {
  if (els.taxExempt) els.taxExempt.checked = settlement.taxExempt === true;
  renderLoanInputs(settlement.loanFees || []);
  renderLineups(settlement);
  renderValuationInputs(settlement.valuationChanges || []);
  updateTaxInputsState();
}

function renderLoanInputs(rows = []) {
  if (!els.loanInputs) return;
  if (!rows.length) {
    els.loanInputs.innerHTML = `<p class="muted">本场暂无租借费。</p>`;
    return;
  }
  els.loanInputs.innerHTML = rows.map((row, index) => `
    <div class="panel" style="padding:.6rem;margin-top:.5rem;" data-loan-row>
      <div style="display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:.5rem;align-items:end;">
        <label class="field"><span class="field-label">使用队伍</span><select class="input loan-paying-team">${teamSelectOptions(row.payingTeamId, true)}</select></label>
        <label class="field"><span class="field-label">选手</span><select class="input loan-player">${playerSelectOptions(row.playerId)}</select></label>
        <label class="field"><span class="field-label">替换选手</span><select class="input loan-replaced-player">${replacedPlayerSelectOptions(row.payingTeamId, row.replacedPlayerId, row.playerId)}</select></label>
        <label class="field"><span class="field-label">结算身价</span><input class="input loan-value" type="number" min="0" value="${row.playerValue ?? ""}" /></label>
        <label class="field"><span class="field-label">来源</span><select class="input loan-source-type">
          <option value="original_team" ${row.sourceType !== "free_agent" ? "selected" : ""}>原队伍</option>
          <option value="free_agent" ${row.sourceType === "free_agent" ? "selected" : ""}>自由人</option>
        </select></label>
        <label class="field"><span class="field-label">原队伍</span><select class="input loan-source-team">${teamSelectOptions(row.sourceTeamId)}</select></label>
        <button class="btn ghost remove-loan" type="button" data-index="${index}">删除</button>
      </div>
      <label class="field" style="display:block;margin-top:.5rem;"><span class="field-label">原因</span><input class="input loan-reason" value="${row.reason || ""}" /></label>
    </div>
  `).join("");
}

function renderValuationInputs(rows = []) {
  if (!els.valuationInputs) return;
  if (!rows.length) {
    els.valuationInputs.innerHTML = `<p class="muted">本场暂无身价变化。</p>`;
    return;
  }
  els.valuationInputs.innerHTML = rows.map((row, index) => `
    <div class="panel" style="padding:.6rem;margin-top:.5rem;" data-valuation-row>
      <div style="display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:.5rem;align-items:end;">
        <label class="field"><span class="field-label">选手</span><select class="input valuation-player">${playerSelectOptions(row.playerId)}</select></label>
        <label class="field"><span class="field-label">客观变化</span><input class="input valuation-objective" type="number" value="${row.objectiveDelta ?? 0}" /></label>
        <label class="field"><span class="field-label">主观变化</span><input class="input valuation-subjective" type="number" value="${row.subjectiveDelta ?? 0}" /></label>
        <label class="field"><span class="field-label">原因</span><input class="input valuation-reason" value="${row.subjectiveReason || ""}" /></label>
        <button class="btn ghost remove-valuation" type="button" data-index="${index}">删除</button>
      </div>
    </div>
  `).join("");
}

function collectLoanInputs() {
  if (!els.loanInputs) return [];
  return Array.from(els.loanInputs.querySelectorAll("[data-loan-row]")).map(row => ({
    payingTeamId: numberOrNull(row.querySelector(".loan-paying-team")?.value),
    playerId: numberOrNull(row.querySelector(".loan-player")?.value),
    replacedPlayerId: numberOrNull(row.querySelector(".loan-replaced-player")?.value),
    playerValue: numberOrNull(row.querySelector(".loan-value")?.value),
    sourceType: row.querySelector(".loan-source-type")?.value || "original_team",
    sourceTeamId: numberOrNull(row.querySelector(".loan-source-team")?.value),
    reason: row.querySelector(".loan-reason")?.value || null
  })).filter(row => row.playerId || row.payingTeamId || row.replacedPlayerId || row.playerValue);
}

function collectValuationInputs() {
  if (!els.valuationInputs) return [];
  return Array.from(els.valuationInputs.querySelectorAll("[data-valuation-row]")).map(row => ({
    playerId: numberOrNull(row.querySelector(".valuation-player")?.value),
    objectiveDelta: Number(row.querySelector(".valuation-objective")?.value || 0),
    subjectiveDelta: Number(row.querySelector(".valuation-subjective")?.value || 0),
    subjectiveReason: row.querySelector(".valuation-reason")?.value || null
  })).filter(row => row.playerId || row.objectiveDelta || row.subjectiveDelta || row.subjectiveReason);
}

function collectSettlement() {
  return {
    taxExempt: els.taxExempt?.checked === true,
    homeLineValue: numberOrNull(els.homeLineValue?.value),
    awayLineValue: numberOrNull(els.awayLineValue?.value),
    homeAppearances: collectSideAppearances("home"),
    awayAppearances: collectSideAppearances("away"),
    loanFees: collectLoanInputs(),
    valuationChanges: collectValuationInputs()
  };
}

function updateTaxInputsState() {
  const disabled = els.taxExempt?.checked === true;
  document.querySelectorAll("[data-lineup-side] input, [data-lineup-side] select, [data-lineup-side] button").forEach(el => {
    if (el.classList.contains("appearance-games")) {
      const rosterRow = el.closest("[data-roster-appearance]");
      const selected = !rosterRow || rosterRow.querySelector(".appearance-enabled")?.checked;
      el.disabled = readOnly || disabled || !selected;
      return;
    }
    el.disabled = readOnly || disabled;
  });
  [els.homeLineValue, els.awayLineValue].forEach(el => {
    if (el) el.disabled = readOnly || disabled;
  });
  syncAllAdvantageTierStates();
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
    settlement: collectSettlement(),
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
  renderSettlement(ctx.settlement || {});
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
  if (els.addLoanBtn) els.addLoanBtn.style.display = ro ? "none" : "";
  if (els.addValuationBtn) els.addValuationBtn.style.display = ro ? "none" : "";
  updateTaxInputsState();
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

async function getScreenshotUploadTarget(gameIndex) {
  if (resultCtx?.status === "published") {
    return resultCtx;
  }
  return persistDraftForUpload(gameIndex);
}

function getScreenshotUploadSuccessMessage() {
  return resultCtx?.status === "published"
    ? "截图已上传，已发布赛果的其他数据未修改。"
    : "截图已上传。";
}

function renderSettlementPreview(preview) {
  if (!els.settlementPreview) return;
  const errors = preview.errors || [];
  const warnings = preview.warnings || [];
  const ledgers = preview.pledgers || preview.pLedgers || [];
  const valuations = preview.valuationChanges || [];
  const luxuryTaxes = preview.luxuryTaxes || [];
  const loanFees = preview.loanFees || [];
  const teamBalance = {};
  ledgers.forEach(row => {
    if (row.teamId == null || row.balanceBefore == null || row.balanceAfter == null) return;
    if (!teamBalance[row.teamId]) {
      teamBalance[row.teamId] = { state: row.teamState || row.teamName, before: row.balanceBefore, after: row.balanceAfter };
    } else {
      teamBalance[row.teamId].after = row.balanceAfter;
    }
  });
  const balanceSummary = Object.values(teamBalance).map(b =>
    `${b.state}：${b.before}P → <span style="color:${b.after < 0 ? "#ff9f9f" : "#7CFFB2"}">${b.after}P</span>`
  ).join("&emsp;");
  els.settlementPreview.innerHTML = `
    ${errors.length ? `<div class="empty-state" style="color:#ff9f9f;margin-bottom:.5rem;">${errors.join("<br>")}</div>` : ""}
    ${warnings.length ? `<div class="empty-state" style="color:#ffc857;margin-bottom:.5rem;">${warnings.join("<br>")}</div>` : ""}
    ${balanceSummary ? `<div style="padding:.5rem .75rem;margin-bottom:.5rem;border-radius:8px;background:rgba(255,255,255,.05);"><strong>队伍P币余额</strong><div style="margin-top:.25rem;">${balanceSummary}</div></div>` : ""}
    <div style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.75rem;">
      <div><strong>P币流水</strong>${renderPreviewList(ledgers.map(row => `${row.teamState || row.teamName} · ${row.type} · ${formatSigned(row.amount)}（${row.balanceBefore ?? "-"} → ${row.balanceAfter ?? "-"}）`))}</div>
      <div><strong>身价变化</strong>${renderPreviewList(valuations.map(row => `${row.playerName} · ${row.beforeValue} → ${row.afterValue}（${formatSigned((row.objectiveDelta || 0) + (row.subjectiveDelta || 0))}）`))}</div>
      <div><strong>奢侈税</strong>${renderPreviewList(luxuryTaxes.map(row => `${row.teamState}${row.lineValue != null ? ` · 加权L ${formatLineValue(row.lineValue)}P` : ""} · 超税线 ${row.taxable}P · 税费 ${row.tax}P`))}</div>
      <div><strong>租借费</strong>${renderPreviewList(loanFees.map(row => `${row.playerName} · ${row.payingTeamState} 支付 ${row.fee}P · 原队 ${row.sourceTeamIncome}P`))}</div>
    </div>
  `;
}

function renderPreviewList(items) {
  if (!items.length) return `<p class="muted">无</p>`;
  return `<ul style="margin:.5rem 0 0;padding-left:1.1rem;">${items.map(item => `<li>${item}</li>`).join("")}</ul>`;
}

function formatSigned(value) {
  const n = Number(value || 0);
  return `${n > 0 ? "+" : ""}${n}P`;
}

async function refreshSettlementPreview() {
  const preview = await previewResultSettlement(matchId, collectPayload());
  renderSettlementPreview(preview);
  return preview;
}

async function loadPublishedSettlementData() {
  try {
    const ledgers = await listPLedgers({ matchId, isVoided: 0, source: "match_result" });
    const valuations = await listValuationChanges({ matchId, isVoided: 0, source: "match_result" });

    // 构建类似预览的数据结构
    const preview = {
      pledgers: ledgers.map(row => ({
        teamId: row.teamId,
        teamState: row.teamState,
        teamName: row.teamName,
        type: row.type,
        amount: row.amount,
        balanceBefore: row.balanceBefore,
        balanceAfter: row.balanceAfter,
        reason: row.reason
      })),
      valuationChanges: valuations.map(row => ({
        playerName: row.playerName,
        beforeValue: row.beforeValue,
        afterValue: row.afterValue,
        objectiveDelta: row.objectiveDelta,
        subjectiveDelta: row.subjectiveDelta
      })),
      luxuryTaxes: [],
      loanFees: [],
      errors: [],
      warnings: []
    };

    // 从P币流水中提取奢侈税和租借费
    const processedLoanPlayers = new Set();
    ledgers.forEach(row => {
      if (row.type === "luxury_tax") {
        const reason = row.reason || "";
        // 奢侈税原因格式：奢侈税：加权L=xxx，税线=xxx，应税=xxx
        const lineValueMatch = reason.match(/(?:加权)?L[=＝：:]([\d.]+)/);
        const taxableMatch = reason.match(/应税[=＝：:](\d+)/);
        const taxable = taxableMatch ? parseInt(taxableMatch[1]) : 0;
        preview.luxuryTaxes.push({
          teamState: row.teamState,
          lineValue: lineValueMatch ? Number(lineValueMatch[1]) : null,
          taxable: taxable,
          tax: -row.amount
        });
      }
      if (row.type === "loan_fee") {
        // 租借费有两条记录：支付队伍（负）和原队（正），只处理原队的收入记录
        if (row.amount <= 0) return; // 跳过支付记录

        const reason = row.reason || "";
        // 租借费原因格式：租借 xxx，费用=xxx，选手=xxx，原队=xxx，联盟=xxx
        const playerMatch = reason.match(/租借\s+([^，,]+)[，,]/);
        const playerName = playerMatch ? playerMatch[1].trim() : "未知选手";

        // 避免重复显示同一选手
        if (processedLoanPlayers.has(playerName)) return;
        processedLoanPlayers.add(playerName);

        const feeMatch = reason.match(/费用[=＝：:](\d+)/);
        const sourceTeamMatch = reason.match(/原队[=＝：:](-?\d+)/);
        const payingTeamState = row.teamState || "";

        preview.loanFees.push({
          playerName,
          payingTeamState,
          fee: feeMatch ? parseInt(feeMatch[1]) : 0,
          sourceTeamIncome: sourceTeamMatch ? parseInt(sourceTeamMatch[1]) : row.amount
        });
      }
    });

    renderSettlementPreview(preview);
  } catch (e) {
    console.error("加载结算数据失败:", e);
  }
}

function schedulePreviewRefresh() {
  if (readOnly) return;
  clearTimeout(previewTimer);
  previewTimer = setTimeout(async () => {
    try {
      await refreshSettlementPreview();
    } catch (_) { /* 静默，用户点发布时再报 */ }
  }, 600);
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
    resultCtx = await ensureDraft();
    const preview = await refreshSettlementPreview();
    if (preview.errors?.length) {
      alert(`结算预览存在错误：${preview.errors.join("；")}`);
      return;
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
  updateAppearanceGameLimits();
  schedulePreviewRefresh();
}

function addLoanAppearance(side) {
  const config = sideConfig(side);
  const existing = collectSideAppearances(side).filter(row => row.playerType === "loan");
  existing.push({ playerType: "loan", gamesPlayed: currentTotalGames() || 1 });
  renderLoanAppearances(side, existing);
  updateTaxInputsState();
  syncLineupValue(side);
}

function wireLineupEvents(side) {
  const config = sideConfig(side);
  config.section?.addEventListener("click", event => {
    const remove = event.target.closest(".remove-loan-appearance");
    if (!remove) return;
    const index = Number(remove.dataset.index);
    const remaining = collectSideAppearances(side)
      .filter(row => row.playerType === "loan")
      .filter((_, rowIndex) => rowIndex !== index);
    renderLoanAppearances(side, remaining);
    updateTaxInputsState();
    syncLineupValue(side);
    schedulePreviewRefresh();
  });
  config.section?.addEventListener("change", event => {
    const enabled = event.target.closest(".appearance-enabled");
    if (enabled) {
      const row = enabled.closest("[data-roster-appearance]");
      const games = row?.querySelector(".appearance-games");
      if (games) {
        games.disabled = !enabled.checked || readOnly || els.taxExempt?.checked === true;
        if (enabled.checked && !games.value) {
          games.value = String(currentTotalGames() || 1);
        }
      }
    }
    const playerSelect = event.target.closest(".appearance-player");
    if (playerSelect) {
      const row = playerSelect.closest("[data-loan-appearance]");
      const player = players.find(candidate => String(candidate.id) === String(playerSelect.value));
      if (row) row.dataset.playerValue = String(player?.value ?? 0);
    }
    syncAdvantageTierState(event.target.closest("[data-roster-appearance], [data-loan-appearance]"));
    syncLineupValue(side);
    schedulePreviewRefresh();
  });
  config.section?.addEventListener("input", event => {
    if (!event.target.closest(".appearance-games")) return;
    syncLineupValue(side);
    schedulePreviewRefresh();
  });
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
  els.taxExempt?.addEventListener("change", () => { updateTaxInputsState(); schedulePreviewRefresh(); });
  els.addHomeLoanAppearanceBtn?.addEventListener("click", () => addLoanAppearance("home"));
  els.addAwayLoanAppearanceBtn?.addEventListener("click", () => addLoanAppearance("away"));
  wireLineupEvents("home");
  wireLineupEvents("away");
  els.addLoanBtn?.addEventListener("click", () => {
    const rows = collectLoanInputs();
    rows.push({ sourceType: "original_team" });
    renderLoanInputs(rows);
  });
  els.addValuationBtn?.addEventListener("click", () => {
    const rows = collectValuationInputs();
    rows.push({ objectiveDelta: 0, subjectiveDelta: 0 });
    renderValuationInputs(rows);
  });
  els.loanInputs?.addEventListener("click", e => {
    if (!e.target.closest(".remove-loan")) return;
    const index = Number(e.target.closest(".remove-loan").dataset.index);
    renderLoanInputs(collectLoanInputs().filter((_, i) => i !== index));
    schedulePreviewRefresh();
  });
  els.loanInputs?.addEventListener("change", e => {
    const payingTeamSelect = e.target.closest(".loan-paying-team");
    if (payingTeamSelect) {
      const row = payingTeamSelect.closest("[data-loan-row]");
      refreshReplacedPlayerOptions(row);
      schedulePreviewRefresh();
      return;
    }

    const playerSelect = e.target.closest(".loan-player");
    if (playerSelect) {
      const row = playerSelect.closest("[data-loan-row]");
      syncLoanPlayerFields(row);
      schedulePreviewRefresh();
      return;
    }

    if (e.target.closest(".loan-replaced-player")) {
      schedulePreviewRefresh();
      return;
    }

    schedulePreviewRefresh();
  });
  els.loanInputs?.addEventListener("input", e => {
    schedulePreviewRefresh();
  });
  els.valuationInputs?.addEventListener("click", e => {
    if (!e.target.closest(".remove-valuation")) return;
    const index = Number(e.target.closest(".remove-valuation").dataset.index);
    renderValuationInputs(collectValuationInputs().filter((_, i) => i !== index));
    schedulePreviewRefresh();
  });
  els.valuationInputs?.addEventListener("input", schedulePreviewRefresh);

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
      const uploadTarget = await getScreenshotUploadTarget(gameIndex);
      await uploadGameScreenshot(matchId, uploadTarget.id, gameIndex, input.files[0]);
      input.value = "";
      resultCtx = await getMatchResult(matchId);
      applyStatusUi();
      fillForm(resultCtx);
      alert(getScreenshotUploadSuccessMessage());
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
    [teams, currentTeams] = await Promise.all([getAllTeams(), getCurrentTeams()]);
    players = await getPlayers();
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
      renderSettlement({});
    }
    wireEvents();
    if (readOnly && resultCtx?.status === "published") {
      loadPublishedSettlementData();
    } else if (!readOnly) {
      schedulePreviewRefresh();
    }
  } catch (e) {
    console.error(e);
    alert(`加载失败：${e.message}`);
  }
}

document.addEventListener("DOMContentLoaded", init);
