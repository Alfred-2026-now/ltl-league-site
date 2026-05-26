import { calcLoanFee, calcLuxuryTax, formatP, getTaxLine } from "../services/leagueMetrics.js";

export function setupCalculators(teams) {
  const taxLineDisplay = document.getElementById("taxLineDisplay");
  if (taxLineDisplay) {
    taxLineDisplay.textContent = formatP(getTaxLine(teams));
  }

  setupLuxuryTaxCalculator(teams);
  setupLoanCalculator();
}

function setupLuxuryTaxCalculator(teams) {
  const button = document.getElementById("calcLuxury");
  if (!button) return;

  button.addEventListener("click", () => {
    const lineValue = Number(document.getElementById("luxuryL").value || 0);
    const rosterSize = Number(document.getElementById("rosterN").value || 5);
    const format = document.getElementById("format").value;
    const result = calcLuxuryTax(teams, lineValue, rosterSize, format);

    document.getElementById("luxuryResult").innerHTML =
      `修正因子：×${result.factor.toFixed(2)}<br>` +
      `修正后L：${formatP(result.adjustedLineValue)}<br>` +
      `工资帽线：${formatP(result.taxLine)}<br>` +
      `应税部分X：${formatP(result.taxable)}<br>` +
      `<strong>${format}奢侈税：${formatP(result.tax)}</strong>`;
  });
}

function setupLoanCalculator() {
  const button = document.getElementById("calcLoan");
  if (!button) return;

  button.addEventListener("click", () => {
    const value = Number(document.getElementById("loanValue").value || 0);
    const format = document.getElementById("loanFormat").value;
    const type = document.getElementById("loanType").value;
    const result = calcLoanFee(value, format, type);

    document.getElementById("loanResult").innerHTML =
      `租借费：${formatP(result.fee)}<br>` +
      `选手个人账户：${formatP(result.player)}<br>` +
      `原队伍收益：${formatP(result.sourceTeam)}<br>` +
      `联盟回收：${formatP(result.league)}`;
  });
}
