export function totalGamesFromScores(homeScore, awayScore) {
  if (homeScore === "" || homeScore == null || awayScore === "" || awayScore == null) {
    return 0;
  }
  const home = Number(homeScore);
  const away = Number(awayScore);
  if (!Number.isFinite(home) || !Number.isFinite(away) || home < 0 || away < 0) {
    return 0;
  }
  return Math.trunc(home) + Math.trunc(away);
}

export function calculateWeightedLineValue(appearances, totalGames, advantageGameLimit = totalGames) {
  const games = Number(totalGames);
  const advantageGames = Number(advantageGameLimit);
  if (!Number.isFinite(games) || games <= 0 || !Number.isFinite(advantageGames) || advantageGames <= 0) {
    return 0;
  }
  const weighted = (appearances || []).reduce((sum, appearance) => {
    const playerValue = Number(appearance?.playerValue);
    const gamesPlayed = Number(appearance?.gamesPlayed);
    if (!Number.isFinite(playerValue) || playerValue < 0 || !Number.isFinite(gamesPlayed) || gamesPlayed <= 0) {
      return sum;
    }
    const advantageTotal = (appearance?.advantageTiers || []).reduce((tierSum, tier) => {
      const value = Number(tier);
      return tierSum + (Number.isFinite(value) ? value : 0);
    }, 0);
    const foldedPlayerValue = playerValue - advantageTotal / advantageGames;
    return sum + foldedPlayerValue * gamesPlayed;
  }, 0);
  return Math.round((weighted / games) * 100) / 100;
}

export function appearanceSlotTotal(appearances) {
  return (appearances || []).reduce((sum, appearance) => {
    const gamesPlayed = Number(appearance?.gamesPlayed);
    return sum + (Number.isFinite(gamesPlayed) && gamesPlayed > 0 ? gamesPlayed : 0);
  }, 0);
}

export function formatLineValue(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return "";
  }
  return number.toFixed(2).replace(/\.?0+$/, "");
}
