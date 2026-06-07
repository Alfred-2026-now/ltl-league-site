import { calcExchangeUnits, calcLoanFee, calcLuxuryTax, formatP, getTaxLine } from "../services/leagueMetrics.js";
import { formatTaxLineFormula } from "../services/ruleParameters.js";

export function setupCalculators(teams, ruleParameters) {
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
