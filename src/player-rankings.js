const API_BASE_URL = "http://123.57.19.160/api";

async function request(endpoint, options = {}) {
  const url = `${API_BASE_URL}${endpoint}`;
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }
  const data = await response.json();
  if (data.code !== 200) {
    throw new Error(data.message || "请求失败");
  }
  return data.data;
}

async function getPlayers() {
  return request("/players");
}

async function getTeams() {
  return request("/teams");
}

let currentMode = "value";
let players = [];
let teams = [];
const els = {};

function bindEls() {
  els.showValueRankings = document.getElementById("showValueRankings");
  els.showDepositRankings = document.getElementById("showDepositRankings");
  els.rankingTitle = document.getElementById("rankingTitle");
  els.valueHeader = document.getElementById("valueHeader");
  els.depositHeader = document.getElementById("depositHeader");
  els.rankingBody = document.getElementById("rankingBody");
  els.rankingCards = document.getElementById("rankingCards");
}

function renderRankings() {
  if (!players.length) {
    els.rankingBody.innerHTML = `<tr><td colspan="4" style="padding:1rem;" class="muted">暂无选手数据。</td></tr>`;
    return;
  }

  const teamMap = new Map(teams.map(t => [t.id, t]));

  let rankedPlayers;
  if (currentMode === "value") {
    rankedPlayers = [...players].sort((a, b) => (b.value || 0) - (a.value || 0));
    els.rankingTitle.textContent = "选手身价排行榜";
    els.valueHeader.style.display = "";
    els.depositHeader.style.display = "none";
  } else {
    rankedPlayers = [...players].sort((a, b) => (b.deposit || 0) - (a.deposit || 0));
    els.rankingTitle.textContent = "选手存款排行榜";
    els.valueHeader.style.display = "none";
    els.depositHeader.style.display = "";
  }

  // 桌面端表格渲染
  els.rankingBody.innerHTML = rankedPlayers.map((player, index) => {
    const team = teamMap.get(player.teamId);
    const rank = index + 1;
    let rankDisplay = rank;
    let rankStyle = "color:#f3f8ff;";
    let rowStyle = "border-bottom:1px solid rgba(121, 231, 255, 0.15);transition:all 0.3s ease;";
    let rowBg = "rgba(255, 255, 255, 0.02)";

    if (rank === 1) {
      rankDisplay = "🥇";
      rankStyle = "color:#ffd700;font-size:1.2rem;";
      rowBg = "linear-gradient(90deg, rgba(255, 215, 0, 0.1), transparent)";
    } else if (rank === 2) {
      rankDisplay = "🥈";
      rankStyle = "color:#c0c0c0;font-size:1.1rem;";
      rowBg = "linear-gradient(90deg, rgba(192, 192, 192, 0.08), transparent)";
    } else if (rank === 3) {
      rankDisplay = "🥉";
      rankStyle = "color:#cd7f32;font-size:1.1rem;";
      rowBg = "linear-gradient(90deg, rgba(205, 127, 50, 0.08), transparent)";
    }

    const isFreeAgent = player.status === 3;
    const statusText = isFreeAgent ? ' <span style="background:linear-gradient(135deg, #ff6b6b, #ee5a5a);color:white;padding:2px 8px;border-radius:4px;font-size:0.75rem;margin-left:4px;">自由人</span>' : '';
    const substituteText = player.isSubstitute ? ' <span style="background:rgba(121, 231, 255, 0.2);color:#7cffb2;padding:2px 6px;border-radius:4px;font-size:0.75rem;margin-left:4px;">替补</span>' : '';

    let valueCell = "";
    let depositCell = "";
    let valueStyle = "";
    let depositStyle = "";

    if (currentMode === "value") {
      valueStyle = "color:#7cffb2;font-weight:600;";
      valueCell = `<td style="padding:.75rem 1rem;${valueStyle}">${player.value || 0}P</td>`;
      depositCell = `<td style="padding:.75rem 1rem;display:none;">${player.deposit || 0}P</td>`;
    } else {
      depositStyle = "color:#ffd700;font-weight:600;";
      valueCell = `<td style="padding:.75rem 1rem;display:none;color:#a8b6d6;">${player.value || 0}P</td>`;
      depositCell = `<td style="padding:.75rem 1rem;${depositStyle}">${player.deposit || 0}P</td>`;
    }

    return `
      <tr style="${rowStyle}background:${rowBg};">
        <td style="padding:.75rem 1rem;font-weight:bold;${rankStyle}">${rankDisplay}</td>
        <td style="padding:.75rem 1rem;color:#f3f8ff;">${player.name || "-"}${statusText}${substituteText}</td>
        <td style="padding:.75rem 1rem;color:#a8b6d6;">${team ? `${team.state} · ${team.name}` : "自由人"}</td>
        ${valueCell}
        ${depositCell}
      </tr>
    `;
  }).join("");

  // 移动端卡片渲染
  if (els.rankingCards) {
    els.rankingCards.innerHTML = rankedPlayers.map((player, index) => {
    const team = teamMap.get(player.teamId);
    const rank = index + 1;
    let rankDisplay = rank;
    let rankStyle = "color:#f3f8ff;";
    let cardBg = "rgba(10, 15, 35, 0.6)";

    if (rank === 1) {
      rankDisplay = "🥇";
      rankStyle = "color:#ffd700;font-size:1.8rem;";
      cardBg = "linear-gradient(135deg, rgba(255, 215, 0, 0.15), rgba(10, 15, 35, 0.8))";
    } else if (rank === 2) {
      rankDisplay = "🥈";
      rankStyle = "color:#c0c0c0;font-size:1.6rem;";
      cardBg = "linear-gradient(135deg, rgba(192, 192, 192, 0.12), rgba(10, 15, 35, 0.8))";
    } else if (rank === 3) {
      rankDisplay = "🥉";
      rankStyle = "color:#cd7f32;font-size:1.6rem;";
      cardBg = "linear-gradient(135deg, rgba(205, 127, 50, 0.12), rgba(10, 15, 35, 0.8))";
    }

    const isFreeAgent = player.status === 3;
    const statusBadge = isFreeAgent ? '<span style="background:linear-gradient(135deg, #ff6b6b, #ee5a5a);color:white;padding:2px 6px;border-radius:4px;font-size:0.7rem;">自由人</span>' : '';
    const substituteBadge = player.isSubstitute ? '<span style="background:rgba(121, 231, 255, 0.2);color:#7cffb2;padding:2px 6px;border-radius:4px;font-size:0.7rem;">替补</span>' : '';

    let valueStyle = "#7cffb2";
    let depositStyle = "#ffd700";
    let valueDisplay = player.value || 0;
    let depositDisplay = player.deposit || 0;

    if (currentMode === "value") {
      return `
        <div class="ranking-card" style="background:${cardBg};border:1px solid ${rank === 1 ? 'rgba(255, 215, 0, 0.3)' : rank === 2 ? 'rgba(192, 192, 192, 0.3)' : rank === 3 ? 'rgba(205, 127, 50, 0.3)' : 'rgba(121, 231, 255, 0.2)'};">
          <div class="ranking-card-header">
            <div class="ranking-card-rank" style="${rankStyle}">${rankDisplay}</div>
            <div class="ranking-card-name">${player.name || "-"}</div>
            <div class="ranking-card-tags">
              ${statusBadge}
              ${substituteBadge}
            </div>
          </div>
          <div class="ranking-card-info">
            <div class="ranking-card-stat">
              <div class="ranking-card-stat-label">身价</div>
              <div class="ranking-card-stat-value" style="color:${valueStyle};">${valueDisplay}P</div>
            </div>
            <div class="ranking-card-stat">
              <div class="ranking-card-stat-label">存款</div>
              <div class="ranking-card-stat-value" style="color:#a8b6d6;font-size:0.9rem;">${depositDisplay}P</div>
            </div>
            <div class="ranking-card-team">${team ? `${team.state} · ${team.name}` : "自由人"}</div>
          </div>
        </div>
      `;
    } else {
      return `
        <div class="ranking-card" style="background:${cardBg};border:1px solid ${rank === 1 ? 'rgba(255, 215, 0, 0.3)' : rank === 2 ? 'rgba(192, 192, 192, 0.3)' : rank === 3 ? 'rgba(205, 127, 50, 0.3)' : 'rgba(121, 231, 255, 0.2)'};">
          <div class="ranking-card-header">
            <div class="ranking-card-rank" style="${rankStyle}">${rankDisplay}</div>
            <div class="ranking-card-name">${player.name || "-"}</div>
            <div class="ranking-card-tags">
              ${statusBadge}
              ${substituteBadge}
            </div>
          </div>
          <div class="ranking-card-info">
            <div class="ranking-card-stat">
              <div class="ranking-card-stat-label">身价</div>
              <div class="ranking-card-stat-value" style="color:#a8b6d6;font-size:0.9rem;">${valueDisplay}P</div>
            </div>
            <div class="ranking-card-stat">
              <div class="ranking-card-stat-label">存款</div>
              <div class="ranking-card-stat-value" style="color:${depositStyle};">${depositDisplay}P</div>
            </div>
            <div class="ranking-card-team">${team ? `${team.state} · ${team.name}` : "自由人"}</div>
          </div>
        </div>
      `;
    }
  }).join("");
  }
}

async function init() {
  bindEls();
  try {
    players = await getPlayers();
    teams = await getTeams();
    renderRankings();

    els.showValueRankings.addEventListener("click", () => {
      currentMode = "value";
      els.showValueRankings.classList.add("primary");
      els.showDepositRankings.classList.remove("primary");
      renderRankings();
    });

    els.showDepositRankings.addEventListener("click", () => {
      currentMode = "deposit";
      els.showDepositRankings.classList.add("primary");
      els.showValueRankings.classList.remove("primary");
      renderRankings();
    });
  } catch (e) {
    els.rankingBody.innerHTML = `<tr><td colspan="5" style="padding:1rem;color:#ff9f9f;">加载失败：${e.message}</td></tr>`;
  }
}

document.addEventListener("DOMContentLoaded", init);
