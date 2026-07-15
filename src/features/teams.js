import { formatP, getTeamTotal } from "../services/leagueMetrics.js";

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

/** 有 logoUrl 时覆盖 CSS 默认 --team-logo；无则沿用 data-state 静态图 */
function applyTeamLogos(grid, teams) {
  const cards = grid.querySelectorAll(".team-card");
  cards.forEach((card, index) => {
    const logoUrl = teams[index]?.logoUrl;
    if (!logoUrl) return;
    card.style.setProperty("--team-logo", `url("${String(logoUrl).replace(/"/g, "%22")}")`);
  });
}

function renderDescription(description) {
  const text = (description || "").trim();
  if (!text) {
    return `<p class="team-desc is-empty">暂无队伍简介</p>`;
  }
  return `<p class="team-desc">${escapeHtml(text)}</p>`;
}

export function renderTeams(teams, filter = "") {
  const grid = document.getElementById("teamGrid");
  if (!grid) return;

  const query = filter.trim().toLowerCase();
  const visibleTeams = teams.filter(team => {
    const searchable = `${team.name} ${team.state} ${team.description || ""} ${team.players.map(player => player[0]).join(" ")}`;
    return searchable.toLowerCase().includes(query);
  });

  grid.innerHTML = visibleTeams.map(team => `
    <article class="team-card" data-state="${team.state}">
      <div class="team-top">
        <div>
          <div class="team-name">${escapeHtml(team.name)}</div>
          <p class="eyebrow">LTL TEAM · ${escapeHtml(team.state)}</p>
        </div>
      </div>
      ${renderDescription(team.description)}
      <div class="team-meta">
        <div><span>在职总身价</span><strong>${formatP(getTeamTotal(team))}</strong></div>
        <div><span>队伍P币</span><strong>${formatP(team.p)}</strong></div>
        <div><span>在职人数</span><strong>${team.players.length}人</strong></div>
      </div>
      <ul class="roster">
        ${team.players.map(player => `<li><span>${escapeHtml(player[0])}</span><small>身价 ${formatP(player[1])} | 积分 ${formatP(player[2] || 0)}</small></li>`).join("")}
      </ul>
    </article>
  `).join("");

  applyTeamLogos(grid, visibleTeams);
}

export function setupTeamSearch(teams) {
  const search = document.getElementById("teamSearch");
  if (!search) return;

  search.addEventListener("input", event => renderTeams(teams, event.target.value));
}

const HOME_STATE_SLUG = {
  秦: "qin",
  楚: "chu",
  蜀: "shu",
  吴: "wu",
  越: "yue",
  燕: "yan"
};

/** 首页参赛战队卡片：队徽 + 名称 + 简介，一行两个 */
export function renderHomeTeams(teams) {
  const grid = document.getElementById("homeTeamGrid");
  if (!grid) return;

  const subtitle = document.querySelector(".home-teams-subtitle");
  if (subtitle) {
    subtitle.textContent = `${teams.length}支顶级战队集结，开启第二赛季的巅峰对决`;
  }

  grid.innerHTML = teams.map((team, i) => {
    const logoUrl = team.logoUrl || `/assets/thumbs/${HOME_STATE_SLUG[team.state] || "qin"}-160.png`;
    const desc = (team.description || "").trim();
    const playerCount = team.players?.length || 0;
    return `
      <a class="home-team-card" data-state="${escapeHtml(team.state)}" href="teams.html" style="animation-delay:${i * 0.08}s">
        <div class="home-team-logo">
          <img src="${escapeHtml(logoUrl)}" alt="${escapeHtml(team.name)}" loading="lazy" />
        </div>
        <div class="home-team-info">
          <div class="home-team-name">${escapeHtml(team.name)}</div>
          <div class="home-team-tag">LTL · ${escapeHtml(team.state)}</div>
          ${desc ? `<p class="home-team-desc">${escapeHtml(desc)}</p>` : ""}
          <div class="home-team-stats">
            <span>在职 <strong>${playerCount}人</strong></span>
            <span>P币 <strong>${formatP(team.p)}</strong></span>
          </div>
        </div>
      </a>
    `;
  }).join("");
}
