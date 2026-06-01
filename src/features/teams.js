import { formatP, getTeamTotal } from "../services/leagueMetrics.js";

export function renderTeams(teams, filter = "") {
  const grid = document.getElementById("teamGrid");
  if (!grid) return;

  const query = filter.trim().toLowerCase();
  const visibleTeams = teams.filter(team => {
    const searchable = `${team.name} ${team.state} ${team.players.map(player => player[0]).join(" ")}`;
    return searchable.toLowerCase().includes(query);
  });

  grid.innerHTML = visibleTeams.map(team => `
    <article class="team-card" data-state="${team.state}">
      <div class="team-top">
        <div>
          <div class="team-name">${team.name}</div>
          <p class="eyebrow">LTL TEAM</p>
        </div>
      </div>
      <div class="team-meta">
        <div><span>在职总身价</span><strong>${formatP(getTeamTotal(team))}</strong></div>
        <div><span>队伍P币</span><strong>${formatP(team.p)}</strong></div>
        <div><span>在职人数</span><strong>${team.players.length}人</strong></div>
      </div>
      <ul class="roster">
        ${team.players.map(player => `<li><span>${player[0]}</span><small>身价 ${formatP(player[1])} | 积分 ${formatP(player[2] || 0)}</small></li>`).join("")}
      </ul>
    </article>
  `).join("");
}

export function setupTeamSearch(teams) {
  const search = document.getElementById("teamSearch");
  if (!search) return;

  search.addEventListener("input", event => renderTeams(teams, event.target.value));
}
