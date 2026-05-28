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
  if (!teams || !Array.isArray(teams)) {
    console.warn('getTeamByState: teams is invalid', teams);
    return null;
  }
  return teams.find(team => team.state === state);
}

export function getTeamName(teams, state) {
  const team = getTeamByState(teams, state);
  if (!team) {
    console.warn('getTeamName: team not found for state:', state, 'teams:', teams);
    return state || "undefined";
  }
  return team.name || state;
}

export function getMatchScoreText(match) {
  if (!match.score) return "未录入";
  const text = `${match.score.home} : ${match.score.away}`;
  if (match.status === "forfeit") return `${text}（弃赛）`;
  return text;
}

export function getMatchWinner(match) {
  if (!match.score) return null;
  if (match.score.home === match.score.away) return null;
  return match.score.home > match.score.away ? match.homeTeam : match.awayTeam;
}

export function getPointSettlement(match) {
  const empty = { home: 0, away: 0 };

  // 未录入赛果不展示积分（避免 null 或推算值）
  if (!match.score && match.status !== "forfeit") {
    return null;
  }

  if (match.homePoints != null && match.awayPoints != null) {
    return { home: match.homePoints, away: match.awayPoints };
  }

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

/** 按轮次倒序，同轮次按比赛时间倒序（空时间排后） */
export function sortMatchesByRoundDesc(matches) {
  return [...(matches || [])].sort((a, b) => {
    const ar = Number(a?.round || 0);
    const br = Number(b?.round || 0);
    if (ar !== br) return br - ar;
    const ad = a?.date ? Date.parse(String(a.date)) : NaN;
    const bd = b?.date ? Date.parse(String(b.date)) : NaN;
    if (Number.isFinite(ad) && Number.isFinite(bd) && ad !== bd) return bd - ad;
    if (Number.isFinite(ad) !== Number.isFinite(bd)) return Number.isFinite(bd) ? 1 : -1;
    return String(b?.id || "").localeCompare(String(a?.id || ""));
  });
}

export function groupMatchesByRound(matches) {
  const sorted = sortMatchesByRoundDesc(matches);
  return sorted.reduce((groups, match) => {
    const key = match.roundLabel || `第${match.round}轮`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(match);
    return groups;
  }, new Map());
}

export function getMatchTitle(teams, match) {
  return `${getTeamName(teams, match.homeTeam)} vs ${getTeamName(teams, match.awayTeam)}`;
}
