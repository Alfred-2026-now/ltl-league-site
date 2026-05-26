export const TAX_LINE_FACTOR = 0.92;

export const rosterFactors = [
  { max: 5, factor: 1 },
  { max: 6, factor: 1.1 },
  { max: 7, factor: 1.25 },
  { max: 8, factor: 1.45 },
  { max: 9, factor: 1.7 },
  { max: Infinity, factor: 2 }
];

export function formatP(value) {
  return `${Math.round(value)}P`;
}

export function getTeamTotal(team) {
  return team.players.reduce((sum, player) => sum + player[1], 0);
}

export function getRosterFactor(rosterSize) {
  return (rosterFactors.find(item => rosterSize <= item.max) || rosterFactors.at(-1)).factor;
}

export function getLeagueStandardR(teams) {
  const total = teams.reduce((sum, team) => sum + getTeamTotal(team), 0);
  const count = teams.reduce((sum, team) => sum + team.players.length, 0);
  return (total / count) * 5;
}

export function getTaxLine(teams) {
  return getLeagueStandardR(teams) * TAX_LINE_FACTOR;
}

export function calcProgressiveTax(taxable, format) {
  const rates = format === "BO3" ? [1, 1.3, 1.7, 2.2, 2.8] : [0.8, 1.1, 1.4, 1.8, 2.3];
  const parts = [
    Math.min(taxable, 1000),
    Math.max(Math.min(taxable - 1000, 1000), 0),
    Math.max(Math.min(taxable - 2000, 1000), 0),
    Math.max(Math.min(taxable - 3000, 1000), 0),
    Math.max(taxable - 4000, 0)
  ];
  return parts.reduce((sum, part, index) => sum + part * rates[index], 0);
}

export function calcLuxuryTax(teams, lineValue, rosterSize, format) {
  const factor = getRosterFactor(rosterSize);
  const adjustedLineValue = lineValue * factor;
  const taxLine = getTaxLine(teams);
  const taxable = Math.max(0, adjustedLineValue - taxLine);
  const tax = calcProgressiveTax(taxable, format);
  return { factor, adjustedLineValue, taxLine, taxable, tax };
}

export function calcLoanFee(playerValue, format, playerType) {
  const rate = format === "BO3" ? 0.6 : 0.45;
  const fee = playerValue * rate;
  return {
    fee,
    player: fee * 0.4,
    sourceTeam: playerType === "free" ? 0 : fee * 0.4,
    league: playerType === "free" ? fee * 0.6 : fee * 0.2
  };
}
