import { DEFAULT_RULE_PARAMETERS, getRuleParameter } from "./leagueMetrics.js";

function percent(value) {
  return `${Math.round(Number(value || 0) * 100)}%`;
}

function factor(value) {
  const n = Number(value || 0);
  return Number.isInteger(n) ? String(n) : String(Number(n.toFixed(2)));
}

export function formatTaxLineFormula(params) {
  return `工资帽线=所有在战队选手平均身价×${getRuleParameter(params, "luxury.standard_roster_size")}×${percent(getRuleParameter(params, "luxury.tax_line_factor"))}`;
}

function luxuryRateRows(format, params) {
  const prefix = format === "BO3" ? "luxury.bo3.rate.tier" : "luxury.bo2.rate.tier";
  const width = getRuleParameter(params, "luxury.tier_width");
  const safeWidth = width > 0 ? width : DEFAULT_RULE_PARAMETERS["luxury.tier_width"];
  return [0, 1, 2, 3, 4].map(index => {
    const start = index * safeWidth;
    const end = (index + 1) * safeWidth;
    const range = index === 4 ? `${start}P以上` : `${start}-${end}P`;
    return `<tr><td>${range}</td><td>×${factor(getRuleParameter(params, `${prefix}${index + 1}`))}</td></tr>`;
  }).join("");
}

function renderLuxuryRosterRule(params) {
  return `
    <p>队伍可以拥有超过${getRuleParameter(params, "luxury.standard_roster_size")}名在职人数（包括教练），但队伍在职人数越多，最终奢侈税越高。本场基础奢侈税仍按照实际出场名单的总身价L计算；若队伍在职人数超过标准人数，则用于奢侈税计算的总身价L乘以修正因子。</p>
    <table class="rule-table">
      <thead><tr><th>在职人数</th><th>修正因子</th></tr></thead>
      <tbody>
        <tr><td>≤5人</td><td>×${factor(getRuleParameter(params, "luxury.roster_factor.le5"))}</td></tr>
        <tr><td>6人</td><td>×${factor(getRuleParameter(params, "luxury.roster_factor.eq6"))}</td></tr>
        <tr><td>7人</td><td>×${factor(getRuleParameter(params, "luxury.roster_factor.eq7"))}</td></tr>
        <tr><td>8人</td><td>×${factor(getRuleParameter(params, "luxury.roster_factor.eq8"))}</td></tr>
        <tr><td>9人</td><td>×${factor(getRuleParameter(params, "luxury.roster_factor.eq9"))}</td></tr>
        <tr><td>10人及以上</td><td>×${factor(getRuleParameter(params, "luxury.roster_factor.ge10"))}</td></tr>
      </tbody>
    </table>
    <p class="note">租借选手临时出场不计入队伍在职人数；若正式登记进入队伍名单，则计入在职人数。</p>
  `;
}

function renderLuxuryTaxRule(params) {
  const rosterSize = getRuleParameter(params, "luxury.standard_roster_size");
  const taxLineFactor = getRuleParameter(params, "luxury.tax_line_factor");
  const taxableFloor = getRuleParameter(params, "luxury.taxable_floor");
  return `
    <p>应税部分X=max(${taxableFloor}P，修正后的L−${factor(taxLineFactor)}R)，其中R=所有在战队选手的身价平均值×${rosterSize}。</p>
    <div class="two-col">
      <table class="rule-table"><thead><tr><th colspan="2">周内BO2</th></tr></thead><tbody>${luxuryRateRows("BO2", params)}</tbody></table>
      <table class="rule-table"><thead><tr><th colspan="2">周末BO3</th></tr></thead><tbody>${luxuryRateRows("BO3", params)}</tbody></table>
    </div>
  `;
}

function renderLoanRule(params) {
  return `
    <ul class="rule-list">
      <li>租借必须由租借队伍、原队伍和选手本人三方同意。</li>
      <li>周内BO2租借费为选手身价${percent(getRuleParameter(params, "loan.bo2.rate"))}；周末BO3租借费为选手身价${percent(getRuleParameter(params, "loan.bo3.rate"))}。</li>
      <li>若租借选手出任不同位置，由双方队长议定等效身价，并报联盟批准。</li>
      <li>非自由人租借费分配：${percent(getRuleParameter(params, "loan.player_share"))}进入选手个人账户，${percent(getRuleParameter(params, "loan.original_team_share"))}归原队伍，剩余部分由联盟回收；自由人租借时${percent(getRuleParameter(params, "loan.free_agent_league_share"))}由联盟回收。</li>
      <li>租借选手出场时，其身价计入租借队伍本场出场总身价L，并参与奢侈税计算。</li>
      <li>租借结束后，选手自动回归原队。</li>
      <li>若因选手日程冲突导致缺人且租借队伍P币不足，可申请救急租借，先支付部分租借费，剩余费用从赛后奖金或下周补贴中扣除，并附带利息。</li>
    </ul>
  `;
}

export function applyRuleParametersToRules(rules, params) {
  return (rules || []).map(rule => {
    if (rule.title?.includes("多人名单奢侈税")) {
      return { ...rule, content: renderLuxuryRosterRule(params) };
    }
    if (rule.title?.includes("奢侈税分段税率")) {
      return { ...rule, content: renderLuxuryTaxRule(params) };
    }
    if (rule.title?.includes("租借规则")) {
      return { ...rule, content: renderLoanRule(params) };
    }
    return rule;
  });
}
