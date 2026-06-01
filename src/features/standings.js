import { formatP, getTeamTotal } from "../services/leagueMetrics.js";

export function renderStandings(teams) {
  const table = document.getElementById("standingsTable");
  if (!table) return;

  // 根据积分动态计算排名，而不是使用硬编码的rank字段
  const sorted = [...teams].sort((a, b) => {
    // 首先按积分排序（积分高的在前）
    if (b.points !== a.points) return b.points - a.points;
    // 积分相同则按P币排序（P币多的在前）
    return b.p - a.p;
  });

  // 为排序后的队伍分配动态排名，同分的队伍显示相同排名
  let currentRank = 1;
  const rankedTeams = sorted.map((team, index) => {
    // 如果不是第一个队伍，且当前队伍积分与前一个队伍不同，则更新排名
    if (index > 0 && sorted[index - 1].points !== team.points) {
      currentRank = index + 1;
    }
    return {
      ...team,
      calculatedRank: currentRank
    };
  });

  table.innerHTML = rankedTeams.map(team => `
    <tr>
      <td>${team.calculatedRank}</td>
      <td>${team.name}</td>
      <td>${team.points}</td>
      <td>${formatP(team.p)}</td>
      <td>${formatP(getTeamTotal(team))}</td>
    </tr>
  `).join("");
}
