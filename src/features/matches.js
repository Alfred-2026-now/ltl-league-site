import { formatP } from "../services/leagueMetrics.js";
import {
  getMatchScoreText,
  getMatchTitle,
  getPointSettlement,
  getStatusMeta,
  getTeamName,
  groupMatchesByRound,
  sortMatchesByRoundDesc
} from "../services/matchMetrics.js";

let selectedMatchId = null;
const selectedGameByMatch = new Map();
const selectedGameViewByGame = new Map();

export function renderSchedule(matches, teams) {
  const list = document.getElementById("scheduleList");
  if (!list) return;

  const groups = groupMatchesByRound(matches);
  list.innerHTML = [...groups.entries()].map(([roundLabel, roundMatches]) => `
    <section class="schedule-round">
      <div class="round-heading">
        <strong>${roundLabel}</strong>
        <span>${roundMatches[0]?.format || ""} / ${roundMatches[0]?.date || "待定"}</span>
      </div>
      <div class="match-card-grid">
        ${roundMatches.map(match => renderMatchCard(match, teams)).join("")}
      </div>
    </section>
  `).join("");
}

export function setupMatchHistory(matches, teams) {
  const sortedMatches = sortMatchesByRoundDesc(matches);
  const routeMatch = new URLSearchParams(window.location.search).get("match");
  selectedMatchId = routeMatch || selectedMatchId || sortedMatches[0]?.id || null;
  renderMatchFilters(matches, teams);
  renderMatchHistoryList(matches, teams);
  renderMatchDetail(matches, teams, selectedMatchId);

  document.addEventListener("click", event => {
    const trigger = event.target.closest("[data-match-select]");
    if (!trigger) return;

    selectedMatchId = trigger.dataset.matchSelect;
    const params = new URLSearchParams(window.location.search);
    params.set("match", selectedMatchId);
    window.history.replaceState(null, "", `${window.location.pathname}?${params.toString()}`);
    renderMatchHistoryList(matches, teams);
    renderMatchDetail(matches, teams, selectedMatchId);
  });

  document.addEventListener("change", event => {
    if (event.target.id === "gameSelect") {
      selectedGameByMatch.set(selectedMatchId, Number(event.target.value));
      renderMatchDetail(matches, teams, selectedMatchId);
      return;
    }

    if (event.target.id === "gameViewMode") {
      const gameKey = event.target.dataset.gameKey;
      selectedGameViewByGame.set(gameKey, event.target.value);
      renderMatchDetail(matches, teams, selectedMatchId);
    }
  });

  ["matchRoundFilter", "matchTeamFilter", "matchStatusFilter"].forEach(id => {
    const control = document.getElementById(id);
    if (!control) return;
    control.addEventListener("change", () => {
      renderMatchHistoryList(matches, teams);
    });
  });
}

function renderMatchCard(match, teams) {
  const status = getStatusMeta(match.status);
  const points = getPointSettlement(match);
  const pointsHtml = points
    ? `<span>积分：${formatSigned(points.home)} / ${formatSigned(points.away)}</span>`
    : "";
  const live = match.live?.url
    ? `<a class="live-link" href="${match.live.url}" target="_blank" rel="noreferrer">${match.live.label || "直播间"}</a>`
    : "";

  return `
    <article class="match-card" data-status="${status.tone}">
      <div class="match-card-top">
        <span class="status-badge" data-tone="${status.tone}">${status.label}</span>
        <small>${match.date || "待定"} · ${match.format}</small>
      </div>
      <div class="match-versus">
        <strong>${getTeamName(teams, match.homeTeam)}</strong>
        <span>${getMatchScoreText(match)}</span>
        <strong>${getTeamName(teams, match.awayTeam)}</strong>
      </div>
      <div class="match-card-meta">
        ${pointsHtml}
        ${live}
      </div>
      <a class="btn ghost match-detail-link" href="match-history.html?match=${match.id}" data-match-select="${match.id}">查看详情</a>
    </article>
  `;
}

function renderMatchFilters(matches, teams) {
  const roundFilter = document.getElementById("matchRoundFilter");
  const teamFilter = document.getElementById("matchTeamFilter");
  const statusFilter = document.getElementById("matchStatusFilter");
  if (!roundFilter || !teamFilter || !statusFilter) return;

  const rounds = [...new Map(matches.map(match => [match.round, match.roundLabel])).entries()]
    .sort((a, b) => Number(b[0]) - Number(a[0]));
  roundFilter.innerHTML = `<option value="">全部轮次</option>${rounds.map(([round, label]) => `
    <option value="${round}">${label}</option>
  `).join("")}`;

  teamFilter.innerHTML = `<option value="">全部队伍</option>${teams.map(team => `
    <option value="${team.state}">${team.name}</option>
  `).join("")}`;

  const statuses = [...new Set(matches.map(match => match.status))];
  statusFilter.innerHTML = `<option value="">全部状态</option>${statuses.map(status => `
    <option value="${status}">${getStatusMeta(status).label}</option>
  `).join("")}`;
}

function renderMatchHistoryList(matches, teams) {
  const list = document.getElementById("matchHistoryList");
  if (!list) return;

  const visibleMatches = sortMatchesByRoundDesc(getFilteredMatches(matches));
  if (!visibleMatches.length) {
    list.innerHTML = `<div class="empty-state">没有匹配的比赛。</div>`;
    return;
  }

  list.innerHTML = visibleMatches.map(match => {
    const status = getStatusMeta(match.status);
    const active = match.id === selectedMatchId ? " active" : "";
    return `
      <button class="history-match${active}" type="button" data-match-select="${match.id}">
        <span>${match.roundLabel} · ${match.format}</span>
        <strong>${getMatchTitle(teams, match)}</strong>
        <small>${match.date || "待定"} · ${status.label}</small>
      </button>
    `;
  }).join("");
}

function renderMatchDetail(matches, teams, matchId) {
  const detail = document.getElementById("matchDetail");
  if (!detail) return;

  const routeMatch = new URLSearchParams(window.location.search).get("match");
  const match = matches.find(item => item.id === (matchId || routeMatch)) || matches[0];
  selectedMatchId = match?.id || selectedMatchId;
  if (!match) {
    detail.innerHTML = `<div class="empty-state">暂无比赛数据。</div>`;
    return;
  }

  const status = getStatusMeta(match.status);
  const points = getPointSettlement(match);
  detail.innerHTML = `
    ${renderMatchHero(match, teams, status, points)}

    ${renderLiveBlock(match)}
    ${renderGames(match, teams)}
    <div class="settlement-grid">
      ${renderLedger(match, teams)}
      ${renderValuationChanges(match)}
      ${renderAttachments(match)}
      ${renderAudit(match)}
    </div>
  `;
}

function renderMatchHero(match, teams, status, points) {
  const pointsRow = points
    ? `<div><span>积分结算</span><strong>${formatSigned(points.home)} / ${formatSigned(points.away)}</strong></div>`
    : "";
  return `
    <section class="match-hero-panel">
      ${renderHeroTeam(match.homeTeam, teams, "home")}
      <div class="match-center">
        <p class="eyebrow">${match.season} / ${match.roundLabel} / ${match.format}</p>
        <div class="match-score-big">${getMatchScoreText(match)}</div>
        <span class="status-badge" data-tone="${status.tone}">${status.label}</span>
      </div>
      ${renderHeroTeam(match.awayTeam, teams, "away")}
      <div class="match-meta-strip">
        <div><span>比赛日期</span><strong>${match.date || "待定"}</strong></div>
        ${pointsRow}
        <div><span>数据来源</span><strong>${getSourceLabel(match.source)}</strong></div>
        <div><span>版本</span><strong>${match.version || "未发布"}</strong></div>
      </div>
    </section>
  `;
}

function renderHeroTeam(state, teams, side) {
  return `
    <div class="hero-team ${side}">
      <img src="${getTeamLogo(state)}" alt="${getTeamName(teams, state)}队徽" />
      <span>${state}</span>
      <strong>${getTeamName(teams, state)}</strong>
    </div>
  `;
}

function getFilteredMatches(matches) {
  const round = document.getElementById("matchRoundFilter")?.value || "";
  const team = document.getElementById("matchTeamFilter")?.value || "";
  const status = document.getElementById("matchStatusFilter")?.value || "";

  return matches.filter(match => {
    const roundMatched = !round || String(match.round) === round;
    const teamMatched = !team || match.homeTeam === team || match.awayTeam === team;
    const statusMatched = !status || match.status === status;
    return roundMatched && teamMatched && statusMatched;
  });
}

function renderLiveBlock(match) {
  if (!match.live) return "";
  const link = match.live.url
    ? `<a href="${match.live.url}" target="_blank" rel="noreferrer">${match.live.label || "直播间"}</a>`
    : `<span>${match.live.label || "直播信息待公布"}</span>`;
  return `<section class="detail-block"><h4>直播信息</h4>${link}</section>`;
}

function renderGames(match, teams) {
  if (!match.games?.length) {
    return `<section class="detail-block"><h4>小局详情</h4><div class="empty-state">小局结果和出场阵容待录入。</div></section>`;
  }

  const selectedIndex = selectedGameByMatch.get(match.id) || match.games[0].index;
  const selectedGame = match.games.find(game => game.index === selectedIndex) || match.games[0];
  const modes = getAvailableGameViewModes(selectedGame);
  const gameKey = getGameKey(match, selectedGame);
  const selectedMode = getSelectedGameViewMode(gameKey, selectedGame);

  return `
    <section class="game-detail-panel">
      <div class="game-controlbar">
        <div>
          <p class="eyebrow">GAME DETAIL</p>
          <h4>小局详情</h4>
        </div>
        <div class="game-controls">
          <select id="gameSelect" aria-label="选择小局">
            ${match.games.map(game => `
              <option value="${game.index}"${game.index === selectedGame.index ? " selected" : ""}>第 ${game.index} 局</option>
            `).join("")}
          </select>
          ${renderGameViewModeSelect(modes, selectedMode, gameKey)}
        </div>
      </div>
      <div class="game-list">
        ${renderGameCard(selectedGame, teams, selectedMode)}
      </div>
    </section>
  `;
}

function renderGameCard(game, teams, mode) {
  return `
    <article class="game-card">
      <div class="game-card-top">
        <strong>第 ${game.index} 局</strong>
        <span>${formatDuration(game.durationSeconds)} · 胜方：${getTeamName(teams, game.winner)}</span>
      </div>
      ${renderGameContent(game, teams, mode)}
    </article>
  `;
}

function renderGameContent(game, teams, mode) {
  // 只要存在任何战绩截图（含占位截图），就强制渲染为截图视图，对齐 main 的默认体验。
  if (getScoreScreenshots(game).length) return renderScoreScreenshots(game);
  if (mode === "screenshot") return renderScoreScreenshots(game);
  if (mode === "charts") {
    return `
      ${renderGameSummary(game)}
      ${renderLineups(game, teams)}
      ${renderKeyEvents(game)}
    `;
  }
  return renderGameSummaryOnly(game, teams);
}

function renderGameViewModeSelect(modes, selectedMode, gameKey) {
  if (modes.length <= 1) return "";

  const labels = {
    screenshot: "战绩截图",
    charts: "详细图表",
    summary: "基础比分"
  };

  return `
    <select id="gameViewMode" data-game-key="${gameKey}" aria-label="选择战绩展示模式">
      ${modes.map(mode => `
        <option value="${mode}"${mode === selectedMode ? " selected" : ""}>${labels[mode]}</option>
      `).join("")}
    </select>
  `;
}

function renderScoreScreenshots(game) {
  const screenshots = getScoreScreenshots(game);
  if (!screenshots.length) return renderGameSummaryOnly(game);

  return `
    <div class="screenshot-grid">
      ${screenshots.map(item => `
        <figure class="score-screenshot">
          <img src="${item.url}" alt="${item.label || `第${game.index}局战绩截图`}" />
          <figcaption>
            <strong>${item.label || `第${game.index}局战绩截图`}</strong>
            <span>${item.note || "管理员上传的战绩截图。"}</span>
          </figcaption>
        </figure>
      `).join("")}
    </div>
  `;
}

function renderGameSummaryOnly(game, teams = []) {
  return `
    <div class="basic-game-summary">
      <div><span>胜方</span><strong>${getTeamName(teams, game.winner)}</strong></div>
      <div><span>蓝方</span><strong>${getTeamName(teams, game.blueTeam)}</strong></div>
      <div><span>红方</span><strong>${getTeamName(teams, game.redTeam)}</strong></div>
      <div><span>时长</span><strong>${formatDuration(game.durationSeconds)}</strong></div>
    </div>
    <div class="empty-state">详细战绩待录入。上传战绩截图或结构化数据后会在这里展示。</div>
  `;
}

function renderLineups(game, teams) {
  if (!game.lineups) return `<div class="empty-state">本局阵容待确认。</div>`;
  const homePlayers = game.lineups.home || [];
  const awayPlayers = game.lineups.away || [];
  return `
    ${renderCompactLineupTable(game, teams, homePlayers, awayPlayers)}
    <div class="lineup-grid">
      ${["home", "away"].map(side => `
        <div>
          <strong>${getTeamName(teams, game[`${side}Team`])}</strong>
          <div class="participant-list">
            ${(game.lineups[side] || []).map(player => `
              ${renderParticipant(player, teams)}
            `).join("")}
          </div>
        </div>
      `).join("")}
    </div>
  `;
}

function renderCompactLineupTable(game, teams, homePlayers, awayPlayers) {
  const rows = ["TOP", "JUG", "MID", "BOT", "SUP"].map(position => {
    const home = homePlayers.find(player => player.position === position);
    const away = awayPlayers.find(player => player.position === position);
    return `
      <tr>
        <th>${position}</th>
        ${renderCompactParticipant(home, teams)}
        ${renderCompactParticipant(away, teams)}
      </tr>
    `;
  }).join("");

  return `
    <table class="lineup-table scoreboard-table">
      <thead>
        <tr>
          <th>位置</th>
          <th>${getTeamName(teams, game.homeTeam)}</th>
          <th>英雄</th>
          <th>KDA</th>
          <th>输出</th>
          <th>经济</th>
          <th>${getTeamName(teams, game.awayTeam)}</th>
          <th>英雄</th>
          <th>KDA</th>
          <th>输出</th>
          <th>经济</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>
  `;
}

function renderCompactParticipant(player, teams) {
  if (!player) {
    return `
      <td class="empty-cell">待确认</td>
      <td class="empty-cell">-</td>
      <td class="empty-cell">-</td>
      <td class="empty-cell">-</td>
      <td class="empty-cell">-</td>
    `;
  }

  const combat = player.combatStats || {};
  const economy = player.economyStats || {};
  const vision = player.visionStats || {};
  const derived = player.derivedStats || {};
  const loadout = player.loadout || {};
  const roster = player.rosterContext || {};
  const playerName = player.mappedPlayer?.playerName || player.account?.summonerName || "待指认";
  const loanText = roster.isLoan ? ` · 租借自${getTeamName(teams, roster.sourceTeam)}` : "";

  return `
    <td class="player-name-cell">
      <div class="compact-player">
        <strong>${playerName}</strong>
        <span>CS ${economy.totalMinionsKilled || 0} · 视野 ${vision.visionScore || 0} · 参团 ${formatPercent(derived.killParticipation)}${loanText}</span>
      </div>
    </td>
    <td class="champion-cell">${loadout.champion?.name || "待导入"}</td>
    <td class="kda-cell">${combat.kills || 0}/${combat.deaths || 0}/${combat.assists || 0}</td>
    <td>${formatNumber(combat.damageDealtToChampions)}</td>
    <td>${formatP(economy.goldEarned || 0)}</td>
  `;
}

function renderGameSummary(game) {
  if (!game.teamStats) return "";

  return `
    <div class="game-summary">
      ${["blue", "red"].map(side => {
        const stats = game.teamStats[side] || {};
        const teamState = side === "blue" ? game.blueTeam : game.redTeam;
        return `
          <div>
            <span>${side === "blue" ? "蓝方" : "红方"} · ${teamState}</span>
            <strong>${stats.kills || 0} 击杀 / ${formatP(stats.gold || 0)}</strong>
            <small>塔 ${stats.towers || 0} · 龙 ${stats.dragons || 0} · 男爵 ${stats.barons || 0}</small>
          </div>
        `;
      }).join("")}
    </div>
  `;
}

function renderParticipant(player, teams) {
  const combat = player.combatStats || {};
  const economy = player.economyStats || {};
  const vision = player.visionStats || {};
  const derived = player.derivedStats || {};
  const loadout = player.loadout || {};
  const roster = player.rosterContext || {};
  const playerName = player.mappedPlayer?.playerName || player.playerName || player.account?.summonerName || "待指认";
  const champion = loadout.champion?.name || "英雄待导入";
  const loanText = roster.isLoan ? `租借自${getTeamName(teams, roster.sourceTeam)}` : "本队";

  return `
    <article class="participant-card">
      <div class="participant-main">
        <div>
          <strong>${player.position || "位置待定"} · ${playerName}</strong>
          <span>${champion} · ${loanText}</span>
        </div>
        <b>${combat.kills || 0}/${combat.deaths || 0}/${combat.assists || 0}</b>
      </div>
      <div class="participant-stats">
        <span>补刀 <strong>${economy.totalMinionsKilled || 0}</strong></span>
        <span>经济 <strong>${formatP(economy.goldEarned || 0)}</strong></span>
        <span>输出 <strong>${formatNumber(combat.damageDealtToChampions)}</strong></span>
        <span>承伤 <strong>${formatNumber(combat.totalDamageTaken)}</strong></span>
        <span>视野 <strong>${vision.visionScore || 0}</strong></span>
        <span>参团 <strong>${formatPercent(derived.killParticipation)}</strong></span>
      </div>
      <div class="participant-extra">
        <span>账号：${player.account?.gameName || player.account?.summonerName || "待导入"}</span>
      </div>
    </article>
  `;
}

function renderKeyEvents(game) {
  const events = game.timeline?.keyEvents || [];
  if (!events.length) return "";

  return `
    <div class="key-events">
      ${events.map(event => `<span>${event.time} · ${event.label}</span>`).join("")}
    </div>
  `;
}

function renderLedger(match, teams) {
  if (!match.pLedger?.length) {
    return `<section class="detail-block"><h4>P币流水</h4><div class="empty-state">暂无已发布流水。</div></section>`;
  }

  return `
    <section class="detail-block">
      <h4>P币流水</h4>
      <table class="compact-table">
        <thead><tr><th>队伍</th><th>类型</th><th>金额</th><th>原因</th></tr></thead>
        <tbody>${match.pLedger.map(item => `
          <tr>
            <td>${getTeamName(teams, item.team)}</td>
            <td>${item.type}</td>
            <td>${formatSignedP(item.amount)}</td>
            <td>${item.reason || ""}</td>
          </tr>
        `).join("")}</tbody>
      </table>
    </section>
  `;
}

function renderValuationChanges(match) {
  if (!match.valuationChanges?.length) {
    return `<section class="detail-block"><h4>身价变化</h4><div class="empty-state">暂无已发布身价变化。</div></section>`;
  }

  return `
    <section class="detail-block">
      <h4>身价变化</h4>
      <table class="compact-table">
        <thead><tr><th>选手</th><th>赛前</th><th>客观</th><th>主观</th><th>赛后</th><th>原因</th></tr></thead>
        <tbody>${match.valuationChanges.map(item => `
          <tr>
            <td>${item.playerName}</td>
            <td>${formatP(item.before)}</td>
            <td>${formatSignedP(item.objective)}</td>
            <td>${formatSignedP(item.subjective)}</td>
            <td>${formatP(item.after)}</td>
            <td>${item.reason || ""}</td>
          </tr>
        `).join("")}</tbody>
      </table>
    </section>
  `;
}

function renderAttachments(match) {
  if (!match.attachments?.length) {
    return `<section class="detail-block"><h4>附件与回放</h4><div class="empty-state">暂无附件或回放文件。</div></section>`;
  }

  return `
    <section class="detail-block">
      <h4>附件与回放</h4>
      <div class="attachment-list">
        ${match.attachments.map(item => `
          <a href="${item.url}" target="_blank" rel="noreferrer">${item.label}</a>
        `).join("")}
      </div>
    </section>
  `;
}

function renderAudit(match) {
  const version = match.version || "v0";
  return `
    <section class="detail-block">
      <h4>发布记录</h4>
      <div class="audit-row">
        <span>当前版本：${version}</span>
        <span>${match.notes || "暂无备注。"}</span>
      </div>
    </section>
  `;
}

function getGameKey(match, game) {
  return `${match.id}:${game.index}`;
}

function getScoreScreenshots(game) {
  return Array.isArray(game.scoreScreenshots) ? game.scoreScreenshots.filter(item => item?.url) : [];
}

function hasScoreScreenshots(game) {
  return getScoreScreenshots(game).length > 0;
}

function hasStructuredStats(game) {
  return Boolean(
    game.teamStats ||
    game.lineups ||
    game.timeline?.keyEvents?.length
  );
}

function getAvailableGameViewModes(game) {
  const modes = [];
  if (hasScoreScreenshots(game)) modes.push("screenshot");
  if (hasStructuredStats(game)) modes.push("charts");
  if (!modes.length) modes.push("summary");
  return modes;
}

function getDefaultGameViewMode(game) {
  if (hasScoreScreenshots(game)) return "screenshot";
  if (hasStructuredStats(game)) return "charts";
  return "summary";
}

function getSelectedGameViewMode(gameKey, game) {
  // 对齐 main 的直觉：只要存在战绩截图，就默认优先展示截图（而不是结构化表格）。
  if (hasScoreScreenshots(game)) return "screenshot";
  const modes = getAvailableGameViewModes(game);
  const selected = selectedGameViewByGame.get(gameKey);
  return modes.includes(selected) ? selected : getDefaultGameViewMode(game);
}

function getSourceLabel(source) {
  const labels = {
    lcu_import: "LCU导入",
    manual_entry: "人工录入",
    mixed: "导入后人工补充"
  };
  return labels[source] || "未录入";
}

function formatSigned(value) {
  return `${value >= 0 ? "+" : ""}${value}`;
}

function formatSignedP(value) {
  return `${value >= 0 ? "+" : ""}${formatP(value)}`;
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString("zh-CN");
}

function formatPercent(value) {
  if (value === null || value === undefined) return "-";
  return `${Math.round(value * 100)}%`;
}

function formatDuration(seconds) {
  if (!seconds) return "时长待导入";
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${minutes}:${String(rest).padStart(2, "0")}`;
}

function getTeamLogo(state) {
  const logos = {
    秦: "assets/thumbs/qin-160.png",
    楚: "assets/thumbs/chu-160.png",
    蜀: "assets/thumbs/shu-160.png",
    吴: "assets/thumbs/wu-160.png",
    越: "assets/thumbs/yue-160.png",
    燕: "assets/thumbs/yan-160.png"
  };
  return logos[state] || "assets/thumbs/qin-160.png";
}
