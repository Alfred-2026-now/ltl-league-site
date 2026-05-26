const TAX_LINE_FACTOR = 0.92;

const teams = [
  {
    state: "秦",
    name: "秦国队",
    p: 807,
    points: 9,
    rank: 1,
    players: [
      ["ZerstaN（自由人）", 1800],
      ["天下人", 3030],
      ["LOL历史总得分王", 1831],
      ["樱岛麻衣", 2926],
      ["BUAA2wh", 3466]
    ]
  },
  {
    state: "楚",
    name: "楚国队",
    p: 4389,
    points: 3,
    rank: 3,
    players: [
      ["大橙子", 3400],
      ["不早起的阿斗", 1800],
      ["cap999", 1812],
      ["是你的真如啊", 731],
      ["猫喜欢吉良吉影k", 783],
      ["广寒枝（替补）", 2569]
    ]
  },
  {
    state: "蜀",
    name: "蜀国队",
    p: 6639,
    points: 3,
    rank: 3,
    players: [
      ["BUAA5km", 3998],
      ["千山万水", 2684],
      ["Puler", 2939],
      ["Kuromi", 1950],
      ["脚踏实地", 1196]
    ]
  },
  {
    state: "吴",
    name: "吴国队",
    p: 4642,
    points: 7,
    rank: 2,
    players: [
      ["实践检验认识", 3513],
      ["黑巧终结者", 2944],
      ["theshy", 2811],
      ["莫以故事、诉于卿", 2115],
      ["小铭慕斯raga", 1859]
    ]
  },
  {
    state: "越",
    name: "越国队",
    p: 6005,
    points: 3,
    rank: 3,
    players: [
      ["T1banana", 2318],
      ["忧伤博弈", 2584],
      ["何必恨王昌", 2497],
      ["水龙吟苏幕遮", 1945],
      ["万泉诗人", 1759]
    ]
  },
  {
    state: "燕",
    name: "燕国队",
    p: 5000,
    points: 2,
    rank: 6,
    players: [
      ["想你时风起", 3279],
      ["凯隐不是该赢吗", 2402],
      ["不够活跃", 2190],
      ["明栗双收", 1268],
      ["坑货、别靠近我", 2044]
    ]
  }
];

const rosterFactor = [
  { max: 5, factor: 1 },
  { max: 6, factor: 1.1 },
  { max: 7, factor: 1.25 },
  { max: 8, factor: 1.45 },
  { max: 9, factor: 1.7 },
  { max: Infinity, factor: 2 }
];

function formatP(v) {
  return `${Math.round(v)}P`;
}

function getTeamTotal(team) {
  return team.players.reduce((sum, player) => sum + player[1], 0);
}

function getRosterFactor(n) {
  return (rosterFactor.find(r => n <= r.max) || rosterFactor.at(-1)).factor;
}

function getLeagueStandardR() {
  const total = teams.reduce((sum, t) => sum + t.players.reduce((s, p) => s + p[1], 0), 0);
  const count = teams.reduce((sum, t) => sum + t.players.length, 0);
  return (total / count) * 5;
}

function getTaxLine() {
  return getLeagueStandardR() * TAX_LINE_FACTOR;
}

function calcProgressiveTax(x, format) {
  const rates = format === "BO3" ? [1, 1.3, 1.7, 2.2, 2.8] : [0.8, 1.1, 1.4, 1.8, 2.3];
  const parts = [
    Math.min(x, 1000),
    Math.max(Math.min(x - 1000, 1000), 0),
    Math.max(Math.min(x - 2000, 1000), 0),
    Math.max(Math.min(x - 3000, 1000), 0),
    Math.max(x - 4000, 0)
  ];
  return parts.reduce((sum, part, i) => sum + part * rates[i], 0);
}

function calcLuxuryTax(l, n, format) {
  const factor = getRosterFactor(n);
  const adjustedL = l * factor;
  const taxLine = getTaxLine();
  const taxable = Math.max(0, adjustedL - taxLine);
  const tax = calcProgressiveTax(taxable, format);
  return { factor, adjustedL, taxLine, taxable, tax };
}

function renderStandings() {
  const table = document.getElementById("standingsTable");
  if (!table) return;

  const sorted = [...teams].sort((a, b) => {
    if (a.rank !== b.rank) return a.rank - b.rank;
    if (b.points !== a.points) return b.points - a.points;
    return b.p - a.p;
  });

  table.innerHTML = sorted.map(team => `
    <tr>
      <td>${team.rank}</td>
      <td>${team.name}</td>
      <td>${team.points}</td>
      <td>${formatP(team.p)}</td>
      <td>${formatP(getTeamTotal(team))}</td>
    </tr>
  `).join("");
}

function renderTeams(filter = "") {
  const grid = document.getElementById("teamGrid");
  const q = filter.trim().toLowerCase();
  const visible = teams.filter(t => `${t.name} ${t.state} ${t.players.map(p => p[0]).join(" ")}`.toLowerCase().includes(q));

  grid.innerHTML = visible.map(t => `
    <article class="team-card" data-state="${t.state}">
      <div class="team-top">
        <div>
          <div class="team-name">${t.name}</div>
          <p class="eyebrow">LTL TEAM</p>
        </div>
      </div>
      <div class="team-meta">
        <div><span>在职总身价</span><strong>${formatP(getTeamTotal(t))}</strong></div>
        <div><span>队伍P币</span><strong>${formatP(t.p)}</strong></div>
        <div><span>在职人数</span><strong>${t.players.length}人</strong></div>
      </div>
      <ul class="roster">
        ${t.players.map(p => `<li><span>${p[0]}</span><small>${formatP(p[1])}</small></li>`).join("")}
      </ul>
    </article>
  `).join("");
}

function setupAccordion() {
  document.querySelectorAll(".accordion-title").forEach(btn =>
    btn.addEventListener("click", () => btn.closest(".accordion-item").classList.toggle("open"))
  );
}

function setupNav() {
  const toggle = document.getElementById("navToggle");
  const links = document.getElementById("navLinks");
  toggle.addEventListener("click", () => links.classList.toggle("show"));
  document.querySelectorAll(".nav-links a").forEach(a => a.addEventListener("click", () => links.classList.remove("show")));
}

function setupSearch() {
  document.getElementById("teamSearch").addEventListener("input", e => renderTeams(e.target.value));
}

function setupCalculators() {
  document.getElementById("taxLineDisplay").textContent = formatP(getTaxLine());

  document.getElementById("calcLuxury").addEventListener("click", () => {
    const l = Number(document.getElementById("luxuryL").value || 0);
    const n = Number(document.getElementById("rosterN").value || 5);
    const format = document.getElementById("format").value;
    const r = calcLuxuryTax(l, n, format);
    document.getElementById("luxuryResult").innerHTML =
      `修正因子：×${r.factor.toFixed(2)}<br>` +
      `修正后L：${formatP(r.adjustedL)}<br>` +
      `工资帽线：${formatP(r.taxLine)}<br>` +
      `应税部分X：${formatP(r.taxable)}<br>` +
      `<strong>${format}奢侈税：${formatP(r.tax)}</strong>`;
  });

  document.getElementById("calcLoan").addEventListener("click", () => {
    const value = Number(document.getElementById("loanValue").value || 0);
    const format = document.getElementById("loanFormat").value;
    const type = document.getElementById("loanType").value;
    const rate = format === "BO3" ? 0.6 : 0.45;
    const fee = value * rate;
    const player = fee * 0.4;
    const source = type === "free" ? 0 : fee * 0.4;
    const league = type === "free" ? fee * 0.6 : fee * 0.2;
    document.getElementById("loanResult").innerHTML =
      `租借费：${formatP(fee)}<br>` +
      `选手个人账户：${formatP(player)}<br>` +
      `原队伍收益：${formatP(source)}<br>` +
      `联盟回收：${formatP(league)}`;
  });

  document.getElementById("calcExchange").addEventListener("click", () => {
    const p = Number(document.getElementById("pCoins").value || 0);
    const units = Math.floor(p / 10000);
    document.getElementById("exchangeResult").innerHTML =
      `可兑换次数：${units}次<br>` +
      `可兑换点券：${units * 10000}英雄联盟点券<br>` +
      `或可兑换：¥${units * 100}`;
  });
}

function setupActiveNav() {
  const links = [...document.querySelectorAll(".nav-links a")];
  const sections = links.map(a => document.querySelector(a.getAttribute("href"))).filter(Boolean);
  window.addEventListener("scroll", () => {
    let current = "";
    sections.forEach(s => {
      if (window.scrollY >= s.offsetTop - 120) current = `#${s.id}`;
    });
    links.forEach(a => a.classList.toggle("active", a.getAttribute("href") === current));
  });
}

document.addEventListener("DOMContentLoaded", () => {
  renderStandings();
  renderTeams();
  setupAccordion();
  setupNav();
  setupSearch();
  setupCalculators();
  setupActiveNav();
});
