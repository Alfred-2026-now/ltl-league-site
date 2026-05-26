import { formatP, getTeamTotal } from "../services/leagueMetrics.js";

export function renderStandings(teams) {
  const table = document.getElementById("standingsTable");
  if (!table) return;

  const sorted = [...teams].sort((a, b) => {
    if (a.rank !== b.rank) return a.rank - b.rank;
    if (b.points !== a.points) return b.points - a.points;
    return b.p - a.p;
  });

  table.innerHTML = sorted.map(team => `
    <tr>
      <td>${team.rank}</td>
      <td>${team.name}</td>
      <td>${team.points}</td>
      <td>${formatP(team.p)}</td>
      <td>${formatP(getTeamTotal(team))}</td>
    </tr>
  `).join("");
}
