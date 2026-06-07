import { getAssetOverview, listLeagueAssetLedgers, manualAdjustLeagueAsset } from "./api.js";
import { renderChangeChart, renderChangeChartLegend } from "./assets-chart.js";

const els = {};
let latestChanges = [];
let latestAssets = {};

function bindEls() {
  els.summaryGrid = document.getElementById("summaryGrid");
  els.changesChart = document.getElementById("changesChart");
  els.chartLegend = document.getElementById("chartLegend");
  els.changesBody = document.getElementById("changesBody");
  els.ledgerBody = document.getElementById("ledgerBody");
  els.daysInput = document.getElementById("daysInput");
  els.refreshBtn = document.getElementById("refreshBtn");
  els.adjustAmount = document.getElementById("adjustAmount");
  els.adjustReason = document.getElementById("adjustReason");
  els.adjustBtn = document.getElementById("adjustBtn");
}

function formatP(value) {
  return `${Number(value || 0).toLocaleString("zh-CN")}P`;
}

function formatDelta(value) {
  const n = Number(value || 0);
  const color = n >= 0 ? "#7CFFB2" : "#ff9f9f";
  return `<span style="color:${color};">${n > 0 ? "+" : ""}${formatP(n)}</span>`;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}

function renderSummary(data) {
  const cards = [
    ["队伍总资产", data.teamAssets, "所有队伍当前 P 币余额合计"],
    ["联盟总资产", data.leagueAssets, "损耗上交与福利支出的余额"],
    ["个人资产总和", data.playerAssets, "所有选手个人积分合计"],
    ["总资产", data.totalAssets, "队伍、联盟、个人三项合计"]
  ];
  els.summaryGrid.innerHTML = cards.map(([title, value, desc]) => `
    <div class="panel" style="padding:1rem;min-height:120px;">
      <div class="muted" style="font-size:.85rem;">${title}</div>
      <div style="font-size:1.8rem;font-weight:800;margin:.35rem 0;">${formatP(value)}</div>
      <div class="muted" style="font-size:.85rem;">${desc}</div>
    </div>
  `).join("");
}

function renderChanges(rows) {
  latestChanges = rows;
  renderChangeChart(els.changesChart, latestChanges, latestAssets);
  if (!rows.length) {
    els.changesBody.innerHTML = `<tr><td colspan="5" style="padding:1rem;" class="muted">暂无变化。</td></tr>`;
    return;
  }
  els.changesBody.innerHTML = rows.map(row => `
    <tr>
      <td style="padding:.75rem 1rem;">${escapeHtml(row.date)}</td>
      <td style="padding:.75rem 1rem;">${formatDelta(row.teamDelta)}</td>
      <td style="padding:.75rem 1rem;">${formatDelta(row.leagueDelta)}</td>
      <td style="padding:.75rem 1rem;">${formatDelta(row.playerDelta)}</td>
      <td style="padding:.75rem 1rem;">${formatDelta(row.totalDelta)}</td>
    </tr>
  `).join("");
}

function typeText(type) {
  const map = {
    history_seed: "历史初始化",
    luxury_tax: "奢侈税上交",
    loan_fee: "租借联盟分成",
    transfer_fee: "转赠手续费",
    prize_exchange: "奖品兑换",
    prize_exchange_refund: "兑换退款",
    manual_income: "手动增加",
    welfare_expense: "福利支出",
    team_salary_deduct: "队伍工资扣除",
    manual_team_loss: "手动队伍扣除",
    player_sign_loss: "买入损耗",
    player_release_loss: "解约损耗",
    team_loss_reversal: "队伍扣款回滚",
    match_result_reversal: "赛果回滚"
  };
  return map[type] || type;
}

function renderLedger(rows) {
  if (!rows.length) {
    els.ledgerBody.innerHTML = `<tr><td colspan="6" style="padding:1rem;" class="muted">暂无联盟资产流水。</td></tr>`;
    return;
  }
  els.ledgerBody.innerHTML = rows.map(row => `
    <tr>
      <td style="padding:.75rem 1rem;">${escapeHtml(row.createdAt || "-")}</td>
      <td style="padding:.75rem 1rem;">${escapeHtml(typeText(row.type))}</td>
      <td style="padding:.75rem 1rem;">${formatDelta(row.amount)}</td>
      <td style="padding:.75rem 1rem;">${formatP(row.balanceBefore)} → ${formatP(row.balanceAfter)}</td>
      <td style="padding:.75rem 1rem;">${escapeHtml(row.source || "-")}</td>
      <td style="padding:.75rem 1rem;">${escapeHtml(row.reason || "-")}</td>
    </tr>
  `).join("");
}

async function refresh() {
  const days = Number(els.daysInput.value || 14);
  els.summaryGrid.innerHTML = `<div class="panel" style="padding:1rem;grid-column:1 / -1;">加载中...</div>`;
  els.changesBody.innerHTML = `<tr><td colspan="5" style="padding:1rem;" class="muted">加载中...</td></tr>`;
  els.ledgerBody.innerHTML = `<tr><td colspan="6" style="padding:1rem;" class="muted">加载中...</td></tr>`;
  try {
    const [overview, ledgers] = await Promise.all([
      getAssetOverview(days),
      listLeagueAssetLedgers(100)
    ]);
    latestAssets = {
      teamAssets: overview.teamAssets,
      leagueAssets: overview.leagueAssets,
      playerAssets: overview.playerAssets
    };
    renderSummary(overview);
    renderChanges(overview.changes || []);
    renderLedger(ledgers);
  } catch (error) {
    els.summaryGrid.innerHTML = `<div class="panel" style="padding:1rem;color:#ff9f9f;grid-column:1 / -1;">加载失败：${error.message}</div>`;
    els.changesBody.innerHTML = "";
    els.ledgerBody.innerHTML = "";
  }
}

async function submitAdjust() {
  const amount = Number(els.adjustAmount.value);
  const reason = els.adjustReason.value.trim();
  if (!amount) {
    alert("请填写非 0 金额");
    return;
  }
  if (!reason) {
    alert("请填写原因");
    return;
  }
  if (amount < 0 && !confirm(`确认从联盟总资产扣除 ${Math.abs(amount)}P？`)) {
    return;
  }
  try {
    await manualAdjustLeagueAsset({ amount, reason });
    els.adjustAmount.value = "";
    els.adjustReason.value = "";
    await refresh();
  } catch (error) {
    alert(`调整失败：${error.message}`);
  }
}

async function init() {
  bindEls();
  renderChangeChartLegend(els.chartLegend);
  els.refreshBtn.addEventListener("click", refresh);
  els.adjustBtn.addEventListener("click", submitAdjust);
  window.addEventListener("resize", () => renderChangeChart(els.changesChart, latestChanges, latestAssets));
  await refresh();
}

document.addEventListener("DOMContentLoaded", init);
