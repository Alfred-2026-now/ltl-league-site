import { getTeamManagerContext, simulateTeamManagerPlan, streamChatWithTeamManager } from "../services/api.js";

const SIMULATION_PROGRESS_STEPS = [
  "读取双方出场和规则参数",
  "计算基础收益和税线",
  "预筛三档租借候选",
  "精算候选阵容税费",
  "正在生成推荐结果..."
];
const CHAT_STATUS_STEPS = [
  "整理上下文中",
  "查询规则和选手中",
  "等待智能P哥回复中"
];

const state = {
  loading: true,
  error: "",
  context: null,
  input: {
    opponentTeamId: "",
    format: "BO2",
    lineupPlayerIds: [],
    opponentLineupPlayerIds: [],
    recommendationBatches: {}
  },
  selectedLoanIds: [],
  simulation: null,
  messages: [],
  simulating: false,
  simulationProgressIndex: 0,
  chatting: false
};

let root = null;
let simulationProgressTimer = null;
let chatStatusTimer = null;

export function setupTeamManagerAgent() {
  root = document.getElementById("teamManagerWorkbench");
  if (!root) return;
  renderLoading();
  loadContext();
}

async function loadContext() {
  try {
    state.loading = true;
    state.error = "";
    render();
    state.context = await getTeamManagerContext();
    state.loading = false;
    state.input.opponentTeamId = defaultOpponentId();
    state.input.lineupPlayerIds = currentTeamPlayers().slice(0, 5).map(player => player.id);
    state.input.opponentLineupPlayerIds = defaultOpponentLineupIds();
    render();
  } catch (error) {
    state.loading = false;
    state.error = error.message || "智能P哥上下文加载失败";
    render();
  }
}

function renderLoading() {
  root.innerHTML = `<div class="team-manager-empty">智能P哥正在读取规则和队伍信息...</div>`;
}

function render() {
  if (!root) return;
  if (state.loading) {
    renderLoading();
    return;
  }
  if (state.error) {
    root.innerHTML = `
      <div class="team-manager-empty">
        <strong>智能P哥暂不可用</strong>
        <p>${escapeHtml(state.error)}</p>
        <a class="btn primary" href="login.html?redirect=tools.html">去登录</a>
      </div>`;
    return;
  }

  root.innerHTML = `
    <article class="team-manager-panel">
      <div class="team-manager-head">
        <div>
          <p class="eyebrow">AGENT WORKBENCH</p>
          <h2>智能P哥</h2>
        </div>
        <div class="team-manager-current">
          <span>我的队伍</span>
          <strong>${escapeHtml(currentTeamLabel())}</strong>
        </div>
      </div>
      <div class="team-manager-layout">
        ${renderInputPane()}
        <div class="team-manager-main">
          ${renderSimulationPane()}
          ${renderChatPane()}
        </div>
      </div>
    </article>`;
  bindEvents();
  if (state.chatting) {
    scrollMessagesToBottomSoon();
  }
}

function renderInputPane() {
  return `
    <section class="manager-pane">
      <h3>本场输入</h3>
      <label>对手
        <select id="managerOpponent">
          ${opponentOptions()}
        </select>
      </label>
      <label>赛制
        <select id="managerFormat">
          <option value="BO2" ${state.input.format === "BO2" ? "selected" : ""}>BO2</option>
          <option value="BO3" ${state.input.format === "BO3" ? "selected" : ""}>BO3</option>
        </select>
      </label>
      <div class="lineup-box">
        <div class="lineup-title"><span>我方出场</span><b>${state.input.lineupPlayerIds.length}/5</b></div>
        ${lineupOptions()}
      </div>
      <div class="lineup-box">
        <div class="lineup-title"><span>敌方出场</span><b>${state.input.opponentLineupPlayerIds.length}/5</b></div>
        ${opponentLineupOptions()}
      </div>
      <button class="btn primary" id="managerSimulate" type="button" ${state.simulating ? "disabled" : ""}>
        ${state.simulating ? "模拟中..." : "开始模拟"}
      </button>
    </section>`;
}

function renderSimulationPane() {
  if (state.simulating) {
    return `
      <section class="manager-pane simulation-pane">
        <h3>模拟结果</h3>
        <div class="team-manager-empty compact thinking-box">
          <strong>思考中</strong>
          <span>${escapeHtml(currentSimulationProgress())}</span>
        </div>
      </section>`;
  }
  if (!state.simulation) {
    return `
      <section class="manager-pane simulation-pane">
        <h3>模拟结果</h3>
        <div class="team-manager-empty compact">选择本场条件后开始模拟。</div>
      </section>`;
  }
  return `
    <section class="manager-pane simulation-pane">
      <div class="pane-title-row">
        <h3>模拟结果</h3>
        <span>${escapeHtml(state.simulation.format)} · 敌方 ${state.simulation.opponentLineValue || 0}P</span>
      </div>
      ${renderScenarioGrid()}
      <h4>租借推荐</h4>
      <div class="loan-recommendations">
        ${renderLoanRecommendations()}
      </div>
    </section>`;
}

function renderScenarioGrid() {
  const scenarios = state.simulation?.scenarios || [];
  if (!scenarios.length) {
    return `<div class="team-manager-empty compact">我方出场未满 5 人，先从下方租借推荐补齐阵容。补齐后再计算本场输赢收益。</div>`;
  }
  return `
    <div class="scenario-grid">
      ${scenarios.map(renderScenario).join("")}
    </div>`;
}

function renderScenario(row) {
  return `
    <div class="scenario-card">
      <div class="scenario-top">
        <strong>${escapeHtml(row.scorePattern)}</strong>
        <span class="${row.netPChange >= 0 ? "positive" : "negative"}">${formatSigned(row.netPChange)}P</span>
      </div>
      <p>奖励 ${row.matchReward}P · 税 ${row.luxuryTax}P · 租借 ${row.loanFeePaid}P</p>
      <small>赛后余额 ${row.balanceAfter}P</small>
    </div>`;
}

function renderLoanRecommendations() {
  const groups = state.simulation.strategyRecommendations || [];
  if (!groups.length) {
    return `<div class="team-manager-empty compact">暂无可用租借或替换推荐。</div>`;
  }
  return groups.map(group => `
    <div class="loan-strategy-group">
      <div class="loan-strategy-head">
        <strong>${escapeHtml(group.strategy)}</strong>
        <button class="btn ghost refresh-strategy" type="button" data-strategy="${escapeHtml(group.strategy)}">换一批</button>
      </div>
      ${(group.recommendations || []).length
        ? group.recommendations.map(renderLoanRecommendation).join("")
        : `<div class="team-manager-empty compact">这一批暂无可用推荐，可以换一批看看。</div>`}
    </div>
  `).join("");
}

function renderLoanRecommendation(row) {
  return `
    <button class="loan-row" type="button" data-loan-id="${row.playerId}" data-lineup-after="${(row.lineupPlayerIdsAfterLoan || []).join(",")}">
      <span>
        <strong>${escapeHtml(row.playerName)}</strong>
        <small>${escapeHtml(row.sourceTeamState || "自由人")} · ${row.playerValue}P · 租借费 ${row.loanFee}P${row.replacedPlayerName ? ` · 替换 ${escapeHtml(row.replacedPlayerName)}` : ""}</small>
      </span>
      <b>${formatSigned(row.worstNetPChange)} ~ ${formatSigned(row.bestNetPChange)}P</b>
    </button>
  `;
}

function renderChatPane() {
  return `
    <section class="manager-pane chat-pane">
      <div class="pane-title-row">
        <h3>聊天</h3>
        <span>${state.simulation ? "上下文已同步" : "等待模拟"}</span>
      </div>
      <div class="quick-prompts">
        ${["有什么选手可以推荐", "怎样避免队伍破产", "帮我组建银河战舰", "解释这批推荐"].map(text => `<button class="btn ghost quick-prompt" type="button" data-prompt="${text}">${text}</button>`).join("")}
      </div>
      <div class="chat-messages" id="managerMessages">
        ${renderMessages()}
      </div>
      <form class="chat-form" id="managerChatForm">
        <input id="managerChatInput" placeholder="问 P 哥：为什么推荐这个方案？" ${state.chatting ? "disabled" : ""} />
        <button class="btn primary" type="submit" ${state.chatting ? "disabled" : ""}>${state.chatting ? "发送中" : "发送"}</button>
      </form>
    </section>`;
}

function renderMessages() {
  if (!state.messages.length) {
    return `<div class="chat-message assistant">我会基于当前规则、你的队伍、本场输入和模拟结果回答。先完成一次模拟，我就能给出更具体的推荐。</div>`;
  }
  return state.messages.map(message => `
    <div class="chat-message ${message.role === "assistant" ? "assistant" : "user"}${message.streaming ? " streaming" : ""}">
      ${message.status ? `<div class="chat-status">${escapeHtml(message.status)}</div>` : ""}
      ${message.content ? renderMarkdown(message.content) : ""}
    </div>
  `).join("");
}

function bindEvents() {
  document.getElementById("managerOpponent")?.addEventListener("change", event => {
    state.input.opponentTeamId = event.target.value;
    state.input.opponentLineupPlayerIds = defaultOpponentLineupIds();
    state.simulation = null;
    state.input.recommendationBatches = {};
    render();
  });
  document.getElementById("managerFormat")?.addEventListener("change", event => {
    state.input.format = event.target.value;
    state.simulation = null;
    state.input.recommendationBatches = {};
    render();
  });
  root.querySelectorAll("[data-lineup-id]").forEach(input => {
    input.addEventListener("change", event => toggleLineup(Number(event.target.dataset.lineupId), event.target.checked));
  });
  root.querySelectorAll("[data-opponent-lineup-id]").forEach(input => {
    input.addEventListener("change", event => toggleOpponentLineup(Number(event.target.dataset.opponentLineupId), event.target.checked));
  });
  document.getElementById("managerSimulate")?.addEventListener("click", simulate);
  root.querySelectorAll("[data-loan-id]").forEach(button => {
    button.addEventListener("click", event => applyLoanRecommendation(event.currentTarget));
  });
  root.querySelectorAll(".refresh-strategy").forEach(button => {
    button.addEventListener("click", event => refreshStrategyBatch(event.currentTarget.dataset.strategy));
  });
  root.querySelectorAll(".quick-prompt").forEach(button => {
    button.addEventListener("click", event => sendMessage(event.currentTarget.dataset.prompt));
  });
  document.getElementById("managerChatForm")?.addEventListener("submit", event => {
    event.preventDefault();
    const input = document.getElementById("managerChatInput");
    sendMessage(input?.value || "");
  });
}

function toggleLineup(playerId, checked) {
  const next = new Set(state.input.lineupPlayerIds);
  if (checked) {
    next.add(playerId);
  } else {
    next.delete(playerId);
  }
  state.input.lineupPlayerIds = Array.from(next);
  state.simulation = null;
  state.input.recommendationBatches = {};
  render();
}

function toggleOpponentLineup(playerId, checked) {
  const next = new Set(state.input.opponentLineupPlayerIds);
  if (checked) {
    next.add(playerId);
  } else {
    next.delete(playerId);
  }
  state.input.opponentLineupPlayerIds = Array.from(next);
  state.simulation = null;
  state.input.recommendationBatches = {};
  render();
}

function applyLoanRecommendation(button) {
  const lineupAfter = String(button.dataset.lineupAfter || "")
    .split(",")
    .map(value => Number(value))
    .filter(Boolean);
  if (lineupAfter.length) {
    state.input.lineupPlayerIds = lineupAfter;
  } else {
    const playerId = Number(button.dataset.loanId);
    if (!state.input.lineupPlayerIds.includes(playerId)) {
      state.input.lineupPlayerIds = [...state.input.lineupPlayerIds, playerId];
    }
  }
  state.simulation = null;
  state.input.recommendationBatches = {};
  render();
}

function refreshStrategyBatch(strategy) {
  if (!strategy || state.simulating) return;
  state.input.recommendationBatches = {
    ...(state.input.recommendationBatches || {}),
    [strategy]: Number(state.input.recommendationBatches?.[strategy] || 0) + 1
  };
  simulate();
}

async function simulate() {
  const validation = validateInput();
  if (validation) {
    state.messages.push({ role: "assistant", content: validation });
    render();
    return;
  }
  try {
    state.simulating = true;
    state.simulationProgressIndex = 0;
    startSimulationProgress();
    render();
    state.simulation = await simulateTeamManagerPlan(normalizedInput());
  } catch (error) {
    state.messages.push({ role: "assistant", content: error.message || "模拟失败" });
  } finally {
    stopSimulationProgress();
    state.simulating = false;
    render();
  }
}

function startSimulationProgress() {
  stopSimulationProgress();
  simulationProgressTimer = window.setInterval(() => {
    if (!state.simulating) return;
    state.simulationProgressIndex = Math.min(
      state.simulationProgressIndex + 1,
      SIMULATION_PROGRESS_STEPS.length - 1
    );
    render();
  }, 850);
}

function stopSimulationProgress() {
  if (simulationProgressTimer) {
    window.clearInterval(simulationProgressTimer);
    simulationProgressTimer = null;
  }
}

function currentSimulationProgress() {
  return SIMULATION_PROGRESS_STEPS[state.simulationProgressIndex] || SIMULATION_PROGRESS_STEPS[0];
}

async function sendMessage(text) {
  const content = String(text || "").trim();
  if (!content) return;
  state.messages.push({ role: "user", content });
  const assistantIndex = state.messages.push({
    role: "assistant",
    content: "",
    streaming: true,
    status: CHAT_STATUS_STEPS[0],
    statusIndex: 0
  }) - 1;
  try {
    state.chatting = true;
    startChatStatusProgress(assistantIndex);
    render();
    scrollMessagesToBottomSoon();
    await streamChatWithTeamManager({
      input: normalizedInput(),
      simulation: state.simulation,
      messages: state.messages.filter((_, index) => index !== assistantIndex)
    }, delta => {
      const current = state.messages[assistantIndex];
      current.status = "正在生成回答";
      current.content += delta;
      render();
      scrollMessagesToBottomSoon();
    });
    if (!state.messages[assistantIndex].content) {
      state.messages[assistantIndex].content = "我没有拿到有效回复。";
    }
  } catch (error) {
    state.messages[assistantIndex].status = "";
    state.messages[assistantIndex].content = error.message || "智能P哥暂时无法回复";
  } finally {
    stopChatStatusProgress();
    state.messages[assistantIndex].status = "";
    state.messages[assistantIndex].streaming = false;
    state.chatting = false;
    render();
    scrollMessagesToBottomSoon();
  }
}

function startChatStatusProgress(assistantIndex) {
  stopChatStatusProgress();
  chatStatusTimer = window.setInterval(() => {
    const current = state.messages[assistantIndex];
    if (!current || !current.streaming || current.content) return;
    current.statusIndex = Math.min(
      Number(current.statusIndex || 0) + 1,
      CHAT_STATUS_STEPS.length - 1
    );
    current.status = CHAT_STATUS_STEPS[current.statusIndex];
    render();
    scrollMessagesToBottomSoon();
  }, 1100);
}

function stopChatStatusProgress() {
  if (chatStatusTimer) {
    window.clearInterval(chatStatusTimer);
    chatStatusTimer = null;
  }
}

function scrollMessagesToBottomSoon() {
  requestAnimationFrame(scrollMessagesToBottom);
}

function scrollMessagesToBottom() {
  const box = document.getElementById("managerMessages");
  if (box) {
    box.scrollTop = box.scrollHeight;
  }
}

function validateInput() {
  if (!state.input.opponentTeamId) return "请先选择对手。";
  if (!state.input.format) return "请先选择赛制。";
  if (!state.input.lineupPlayerIds.length) return "请至少选择一名本场出场选手。";
  if (!state.input.opponentLineupPlayerIds.length) return "请至少选择一名敌方本场出场选手。";
  return "";
}

function normalizedInput() {
  return {
    opponentTeamId: Number(state.input.opponentTeamId),
    format: state.input.format,
    lineupPlayerIds: state.input.lineupPlayerIds.map(Number),
    opponentLineupPlayerIds: state.input.opponentLineupPlayerIds.map(Number),
    recommendationBatches: state.input.recommendationBatches || {}
  };
}

function opponentOptions() {
  const currentId = state.context?.currentTeam?.id;
  return (state.context?.teams || [])
    .filter(team => team.id !== currentId)
    .map(team => `<option value="${team.id}" ${String(team.id) === String(state.input.opponentTeamId) ? "selected" : ""}>${escapeHtml(team.state)} · ${escapeHtml(team.name)}</option>`)
    .join("");
}

function lineupOptions() {
  const selected = new Set(state.input.lineupPlayerIds.map(Number));
  const rows = [...currentTeamPlayers(), ...selectedLoanPlayers()];
  if (!rows.length) {
    return `<div class="team-manager-empty compact">当前队伍暂无可选选手。</div>`;
  }
  return rows.map(player => `
    <label class="lineup-option">
      <input type="checkbox" data-lineup-id="${player.id}" ${selected.has(player.id) ? "checked" : ""} />
      <span>${escapeHtml(player.name)}</span>
      <small>${player.value || 0}P${player.teamId === state.context?.currentTeam?.id ? "" : " · 租借"}</small>
    </label>
  `).join("");
}

function opponentLineupOptions() {
  const selected = new Set(state.input.opponentLineupPlayerIds.map(Number));
  const rows = opponentTeamPlayers();
  if (!state.input.opponentTeamId) {
    return `<div class="team-manager-empty compact">请先选择对手。</div>`;
  }
  if (!rows.length) {
    return `<div class="team-manager-empty compact">该对手暂无可选选手。</div>`;
  }
  return rows.map(player => `
    <label class="lineup-option">
      <input type="checkbox" data-opponent-lineup-id="${player.id}" ${selected.has(player.id) ? "checked" : ""} />
      <span>${escapeHtml(player.name)}</span>
      <small>${player.value || 0}P${player.teamId === Number(state.input.opponentTeamId) ? "" : " · 租借"}</small>
    </label>
  `).join("");
}

function currentTeamPlayers() {
  const currentId = state.context?.currentTeam?.id;
  return (state.context?.players || [])
    .filter(player => player.teamId === currentId)
    .filter(player => player.status == null || player.status === 1)
    .sort((a, b) => (b.value || 0) - (a.value || 0));
}

function opponentTeamPlayers() {
  const opponentId = Number(state.input.opponentTeamId);
  return (state.context?.players || [])
    .filter(player => player.teamId === opponentId)
    .filter(player => player.status == null || player.status === 1)
    .sort((a, b) => (b.value || 0) - (a.value || 0));
}

function selectedLoanPlayers() {
  const currentId = state.context?.currentTeam?.id;
  const selected = new Set(state.input.lineupPlayerIds.map(Number));
  return (state.context?.players || [])
    .filter(player => selected.has(player.id))
    .filter(player => player.teamId !== currentId);
}

function defaultOpponentId() {
  const currentId = state.context?.currentTeam?.id;
  const opponent = (state.context?.teams || []).find(team => team.id !== currentId);
  return opponent ? String(opponent.id) : "";
}

function defaultOpponentLineupIds() {
  return opponentTeamPlayers().slice(0, 5).map(player => player.id);
}

function currentTeamLabel() {
  const team = state.context?.currentTeam;
  if (!team) return "未登录";
  return `${team.state} · ${team.name}`;
}

function formatSigned(value) {
  const number = Number(value || 0);
  return number > 0 ? `+${number}` : String(number);
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function renderMarkdown(value) {
  const escaped = escapeHtml(value || "");
  const lines = escaped.split(/\r?\n/);
  const html = [];
  let listTag = "";
  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (/^[-*]\s+/.test(line)) {
      if (listTag && listTag !== "ul") {
        html.push(`</${listTag}>`);
        listTag = "";
      }
      if (!listTag) {
        html.push("<ul>");
        listTag = "ul";
      }
      html.push(`<li>${inlineMarkdown(line.replace(/^[-*]\s+/, ""))}</li>`);
      continue;
    }
    if (/^\d+\.\s+/.test(line)) {
      if (listTag && listTag !== "ol") {
        html.push(`</${listTag}>`);
        listTag = "";
      }
      if (!listTag) {
        html.push("<ol>");
        listTag = "ol";
      }
      html.push(`<li>${inlineMarkdown(line.replace(/^\d+\.\s+/, ""))}</li>`);
      continue;
    }
    if (listTag) {
      html.push(`</${listTag}>`);
      listTag = "";
    }
    if (!line) {
      html.push("<br>");
      continue;
    }
    if (/^#{1,6}\s*/.test(line)) {
      const level = Math.min(line.match(/^#+/)?.[0].length || 3, 3);
      html.push(`<h${level + 3}>${inlineMarkdown(line.replace(/^#{1,6}\s*/, ""))}</h${level + 3}>`);
      continue;
    }
    html.push(`<p>${inlineMarkdown(line)}</p>`);
  }
  if (listTag) {
    html.push(`</${listTag}>`);
  }
  return html.join("");
}

function inlineMarkdown(value) {
  return value
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\*([^*]+)\*/g, "<em>$1</em>");
}
