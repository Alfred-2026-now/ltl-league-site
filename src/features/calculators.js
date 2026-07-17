import {
  calcExchangeUnits,
  calcLoanFee,
  calcLuxuryTax,
  formatP,
  getRuleParameter,
  getTaxLine
} from "../services/leagueMetrics.js";
import { formatTaxLineFormula } from "../services/ruleParameters.js";

function percentLabel(rate) {
  return `${Math.round(Number(rate || 0) * 100)}%`;
}

/** 根据规则参数生成计算器界面文案（便于单测） */
export function buildCalculatorLabels(ruleParameters) {
  const rosterSize = getRuleParameter(ruleParameters, "luxury.standard_roster_size");
  const bo2 = percentLabel(getRuleParameter(ruleParameters, "loan.bo2.rate"));
  const bo3 = percentLabel(getRuleParameter(ruleParameters, "loan.bo3.rate"));
  const playerShare = percentLabel(getRuleParameter(ruleParameters, "loan.player_share"));
  const teamShare = percentLabel(getRuleParameter(ruleParameters, "loan.original_team_share"));
  const freeLeagueShare = percentLabel(getRuleParameter(ruleParameters, "loan.free_agent_league_share"));
  return {
    luxuryLineLabel: `实际出场${rosterSize}人总身价L`,
    loanBo2Option: `BO2：${bo2}`,
    loanBo3Option: `BO3：${bo3}`,
    loanShareHint: `分成：选手 ${playerShare} / 原队 ${teamShare}；自由人联盟回收 ${freeLeagueShare}`
  };
}

export function setupCalculators(teams, ruleParameters) {
  syncCalculatorLabels(ruleParameters);

  const taxLineDisplay = document.getElementById("taxLineDisplay");
  if (taxLineDisplay) {
    taxLineDisplay.textContent = formatP(getTaxLine(teams, ruleParameters));
  }
  const taxLineFormula = document.getElementById("taxLineFormula");
  if (taxLineFormula) {
    taxLineFormula.textContent = formatTaxLineFormula(ruleParameters);
  }

  setupLuxuryTaxCalculator(teams, ruleParameters);
  setupLoanCalculator(ruleParameters);
  setupExchangeCalculator();
}

function syncCalculatorLabels(ruleParameters) {
  const labels = buildCalculatorLabels(ruleParameters);

  const luxuryLineLabelText = document.getElementById("luxuryLineLabelText");
  if (luxuryLineLabelText) {
    luxuryLineLabelText.textContent = labels.luxuryLineLabel;
  }

  const loanFormat = document.getElementById("loanFormat");
  if (loanFormat) {
    for (const option of loanFormat.options) {
      if (option.value === "BO2") option.textContent = labels.loanBo2Option;
      if (option.value === "BO3") option.textContent = labels.loanBo3Option;
    }
  }

  const loanShareHint = document.getElementById("loanShareHint");
  if (loanShareHint) {
    loanShareHint.textContent = labels.loanShareHint;
  }
}

function setupLuxuryTaxCalculator(teams, ruleParameters) {
  const button = document.getElementById("calcLuxury");
  if (!button) return;

  button.addEventListener("click", () => {
    const lineValue = Number(document.getElementById("luxuryL").value || 0);
    const rosterSize = Number(document.getElementById("rosterN").value || 5);
    const format = document.getElementById("format").value;
    const result = calcLuxuryTax(teams, lineValue, rosterSize, format, ruleParameters);

    document.getElementById("luxuryResult").innerHTML =
      `修正因子：×${result.factor.toFixed(2)}<br>` +
      `修正后L：${formatP(result.adjustedLineValue)}<br>` +
      `工资帽线：${formatP(result.taxLine)}<br>` +
      `应税部分X：${formatP(result.taxable)}<br>` +
      `<strong>${format}奢侈税：${formatP(result.tax)}</strong>`;
  });
}

function setupLoanCalculator(ruleParameters) {
  const button = document.getElementById("calcLoan");
  if (!button) return;

  button.addEventListener("click", () => {
    const value = Number(document.getElementById("loanValue").value || 0);
    const format = document.getElementById("loanFormat").value;
    const type = document.getElementById("loanType").value;
    const result = calcLoanFee(value, format, type, ruleParameters);

    document.getElementById("loanResult").innerHTML =
      `租借费：${formatP(result.fee)}<br>` +
      `选手个人账户：${formatP(result.player)}<br>` +
      `原队伍收益：${formatP(result.sourceTeam)}<br>` +
      `联盟回收：${formatP(result.league)}`;
  });
}

function setupExchangeCalculator() {
  const button = document.getElementById("calcExchange");
  if (!button) return;

  button.addEventListener("click", () => {
    const pCoins = Number(document.getElementById("pCoins").value || 0);
    const units = calcExchangeUnits(pCoins);

    document.getElementById("exchangeResult").innerHTML =
      `可兑换次数：${units}次<br>` +
      `可兑换点券：${units * 10000}英雄联盟点券<br>` +
      `或可兑换：¥${units * 100}`;
  });
}
