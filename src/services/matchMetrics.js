export const matchStatuses = {
  scheduled: { label: "已排期", tone: "neutral" },
  upcoming: { label: "即将进行", tone: "info" },
  live: { label: "正在直播", tone: "live" },
  finished: { label: "已完结", tone: "success" },
  forfeit: { label: "弃赛", tone: "danger" },
  postponed: { label: "延期", tone: "warning" },
  cancelled: { label: "取消", tone: "muted" }
};

export function getStatusMeta(status) {
  return matchStatuses[status] || matchStatuses.scheduled;
}

export function getTeamByState(teams, state) {
  return teams.find(team => team.state === state);
}

export function getTeamName(teams, state) {
  return getTeamByState(teams, state)?.name || state;
}

export function getMatchScoreText(match) {
  if (!match.score) return "未录入";
  return `${match.score.home} : ${match.score.away}`;
}

export function getMatchWinner(match) {
  if (!match.score) return null;
  if (match.score.home === match.score.away) return null;
  return match.score.home > match.score.away ? match.homeTeam : match.awayTeam;
}

export function getPointSettlement(match) {
  const empty = { home: 0, away: 0 };

  if (match.status === "forfeit") {
    if (!match.forfeitTeam) return empty;
    const winnerPoints = match.format === "BO1" ? 1 : 3;
    return match.forfeitTeam === match.homeTeam
      ? { home: -3, away: winnerPoints }
      : { home: winnerPoints, away: -3 };
  }

  if (match.status !== "finished" || !match.score) return empty;

  const { home, away } = match.score;
  if (match.format === "BO1") {
    return home > away ? { home: 1, away: 0 } : { home: 0, away: 1 };
  }

  if (match.format === "BO2") {
    if (home === away) return { home: 1, away: 1 };
    return home > away ? { home: 3, away: 0 } : { home: 0, away: 3 };
  }

  if (home === 2 && away === 0) return { home: 3, away: 0 };
  if (home === 0 && away === 2) return { home: 0, away: 3 };
  if (home > away) return { home: 2, away: 1 };
  return { home: 1, away: 2 };
}

export function groupMatchesByRound(matches) {
  return matches.reduce((groups, match) => {
    const key = match.roundLabel || `第${match.round}轮`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(match);
    return groups;
  }, new Map());
}

export function getMatchTitle(teams, match) {
  return `${getTeamName(teams, match.homeTeam)} vs ${getTeamName(teams, match.awayTeam)}`;
}
