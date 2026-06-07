export const DEFAULT_RULE_PARAMETERS = {
  "luxury.tax_line_factor": 0.92,
  "luxury.standard_roster_size": 5,
  "luxury.taxable_floor": 0,
  "luxury.roster_factor.le5": 1,
  "luxury.roster_factor.eq6": 1.1,
  "luxury.roster_factor.eq7": 1.25,
  "luxury.roster_factor.eq8": 1.45,
  "luxury.roster_factor.eq9": 1.7,
  "luxury.roster_factor.ge10": 2,
  "luxury.tier_width": 1000,
  "luxury.bo2.rate.tier1": 0.8,
  "luxury.bo2.rate.tier2": 1.1,
  "luxury.bo2.rate.tier3": 1.4,
  "luxury.bo2.rate.tier4": 1.8,
  "luxury.bo2.rate.tier5": 2.3,
  "luxury.bo3.rate.tier1": 1,
  "luxury.bo3.rate.tier2": 1.3,
  "luxury.bo3.rate.tier3": 1.7,
  "luxury.bo3.rate.tier4": 2.2,
  "luxury.bo3.rate.tier5": 2.8,
  "loan.bo2.rate": 0.45,
  "loan.bo3.rate": 0.6,
  "loan.player_share": 0.4,
  "loan.original_team_share": 0.4,
  "loan.free_agent_source_share": 0,
  "loan.free_agent_league_share": 0.6
};

export const rosterFactors = [
  { max: 5, factor: 1 },
  { max: 6, factor: 1.1 },
  { max: 7, factor: 1.25 },
  { max: 8, factor: 1.45 },
  { max: 9, factor: 1.7 },
  { max: Infinity, factor: 2 }
];

export function buildRuleParameterMap(rows = []) {
  const params = { ...DEFAULT_RULE_PARAMETERS };
  (rows || []).forEach(row => {
    if (!row || row.isActive !== 1) {
      return;
    }
    const key = row.paramKey;
    const value = Number(row.valueText);
    if (key && Number.isFinite(value)) {
      params[key] = value;
    }
  });
  return params;
}

export function getRuleParameter(params, key) {
  const value = params?.[key];
  return Number.isFinite(value) ? value : DEFAULT_RULE_PARAMETERS[key] ?? 0;
}

export function formatP(value) {
  return `${Math.round(value)}P`;
}

export function getTeamTotal(team) {
  return team.players.reduce((sum, player) => sum + player[1], 0);
}

export function getRosterFactor(rosterSize, params = DEFAULT_RULE_PARAMETERS) {
  if (rosterSize <= 5) return getRuleParameter(params, "luxury.roster_factor.le5");
  if (rosterSize === 6) return getRuleParameter(params, "luxury.roster_factor.eq6");
  if (rosterSize === 7) return getRuleParameter(params, "luxury.roster_factor.eq7");
  if (rosterSize === 8) return getRuleParameter(params, "luxury.roster_factor.eq8");
  if (rosterSize === 9) return getRuleParameter(params, "luxury.roster_factor.eq9");
  return getRuleParameter(params, "luxury.roster_factor.ge10");
}

export function getLeagueStandardR(teams, params = DEFAULT_RULE_PARAMETERS) {
  const total = teams.reduce((sum, team) => sum + getTeamTotal(team), 0);
  const count = teams.reduce((sum, team) => sum + team.players.length, 0);
  return (total / count) * getRuleParameter(params, "luxury.standard_roster_size");
}

export function getTaxLine(teams, params = DEFAULT_RULE_PARAMETERS) {
  return getLeagueStandardR(teams, params) * getRuleParameter(params, "luxury.tax_line_factor");
}

export function calcProgressiveTax(taxable, format, params = DEFAULT_RULE_PARAMETERS) {
  const prefix = format === "BO3" ? "luxury.bo3.rate.tier" : "luxury.bo2.rate.tier";
  const rates = [1, 2, 3, 4, 5].map(tier => getRuleParameter(params, `${prefix}${tier}`));
  const width = getRuleParameter(params, "luxury.tier_width");
  const safeWidth = width > 0 ? width : 1000;
  const parts = [0, 1, 2, 3].map(index => Math.max(Math.min(taxable - safeWidth * index, safeWidth), 0));
  parts.push(Math.max(taxable - safeWidth * 4, 0));
  return parts.reduce((sum, part, index) => sum + part * rates[index], 0);
}

export function calcLuxuryTax(teams, lineValue, rosterSize, format, params = DEFAULT_RULE_PARAMETERS) {
  const factor = getRosterFactor(rosterSize, params);
  const adjustedLineValue = lineValue * factor;
  const taxLine = getTaxLine(teams, params);
  const taxable = Math.max(getRuleParameter(params, "luxury.taxable_floor"), adjustedLineValue - taxLine);
  const tax = calcProgressiveTax(taxable, format, params);
  return { factor, adjustedLineValue, taxLine, taxable, tax };
}

export function calcLoanFee(playerValue, format, playerType, params = DEFAULT_RULE_PARAMETERS) {
  const rate = format === "BO3" ? getRuleParameter(params, "loan.bo3.rate") : getRuleParameter(params, "loan.bo2.rate");
  const fee = playerValue * rate;
  const player = fee * getRuleParameter(params, "loan.player_share");
  const sourceTeam = playerType === "free"
    ? fee * getRuleParameter(params, "loan.free_agent_source_share")
    : fee * getRuleParameter(params, "loan.original_team_share");
  return {
    fee,
    player,
    sourceTeam,
    league: playerType === "free" ? fee * getRuleParameter(params, "loan.free_agent_league_share") : fee - player - sourceTeam
  };
}

export function calcExchangeUnits(pCoins) {
  return Math.floor(pCoins / 10000);
}
