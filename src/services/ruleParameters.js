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
    <p>奢侈税不再按队伍在职人数乘修正因子，而是按本轮每名实际出场选手的出场局数计算。</p>
    <p>若一名选手在本轮共 n 局比赛中出场 m 局，则该选手计入出场总身价 L 的金额为：选手身价 × m ÷ n。主队、客队的本队选手和租借选手均按此公式累计。</p>
    <p>若某选手使用身价差优势规则，可从 1000、1500、2000、2500、3000 档位中勾选，勾选项数不得超过赛制总局数（BO2=2、BO3=3、BO5=5）。该选手本场折算身价 = 原始身价 − 勾选档位之和 ÷ 赛制总局数，再按实际出场局数计入 L。</p>
    <p class="note">租借选手的加权身价计入使用队伍，同时仍需按租借规则登记租借费。</p>
  `;
}

function renderLuxuryTaxRule(params) {
  const rosterSize = getRuleParameter(params, "luxury.standard_roster_size");
  const taxLineFactor = getRuleParameter(params, "luxury.tax_line_factor");
  const taxableFloor = getRuleParameter(params, "luxury.taxable_floor");
  return `
    <p>应税部分X=max(${taxableFloor}P，按出场局数加权的L−${factor(taxLineFactor)}R)，其中R=所有在战队选手的身价平均值×${rosterSize}。</p>
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
