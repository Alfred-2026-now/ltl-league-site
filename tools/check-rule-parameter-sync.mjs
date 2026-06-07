import assert from "node:assert/strict";
import {
  buildRuleParameterMap,
  calcLoanFee,
  calcLuxuryTax,
  getTaxLine
} from "../src/services/leagueMetrics.js";
import { applyRuleParametersToRules, formatTaxLineFormula } from "../src/services/ruleParameters.js";

const params = buildRuleParameterMap([
  { paramKey: "luxury.tax_line_factor", valueText: "0.8", isActive: 1 },
  { paramKey: "luxury.standard_roster_size", valueText: "4", isActive: 1 },
  { paramKey: "luxury.taxable_floor", valueText: "10", isActive: 1 },
  { paramKey: "luxury.roster_factor.le5", valueText: "1.2", isActive: 1 },
  { paramKey: "luxury.tier_width", valueText: "500", isActive: 1 },
  { paramKey: "luxury.bo2.rate.tier1", valueText: "2", isActive: 1 },
  { paramKey: "luxury.bo2.rate.tier2", valueText: "3", isActive: 1 },
  { paramKey: "loan.bo2.rate", valueText: "0.5", isActive: 1 },
  { paramKey: "loan.player_share", valueText: "0.3", isActive: 1 },
  { paramKey: "loan.original_team_share", valueText: "0.2", isActive: 1 },
  { paramKey: "loan.free_agent_league_share", valueText: "0.7", isActive: 1 }
]);

const teams = [
  { players: [["A", 1000], ["B", 3000]] },
  { players: [["C", 2000], ["D", 4000]] }
];

assert.equal(Math.round(getTaxLine(teams, params)), 8000);
assert.equal(formatTaxLineFormula(params), "工资帽线=所有在战队选手平均身价×4×80%");

const luxury = calcLuxuryTax(teams, 10000, 5, "BO2", params);
assert.equal(luxury.factor, 1.2);
assert.equal(luxury.taxLine, 8000);
assert.equal(luxury.taxable, 4000);
assert.equal(luxury.tax, 8700);

const loan = calcLoanFee(2000, "BO2", "team", params);
assert.equal(loan.fee, 1000);
assert.equal(loan.player, 300);
assert.equal(loan.sourceTeam, 200);
assert.equal(loan.league, 500);

const rendered = applyRuleParametersToRules([
  { title: "3. 奢侈税分段税率", content: "<p>old luxury</p>" },
  { title: "4. 租借规则", content: "<p>old loan</p>" },
  { title: "其他", content: "<p>keep</p>" }
], params);

assert.match(rendered[0].content, /0\.8R/);
assert.match(rendered[0].content, /0-500P/);
assert.match(rendered[0].content, /×2/);
assert.match(rendered[1].content, /周内BO2租借费为选手身价50%/);
assert.match(rendered[1].content, /30%进入选手个人账户/);
assert.equal(rendered[2].content, "<p>keep</p>");
