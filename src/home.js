import { getApiBase } from "./config/api.js";

// 首页只请求公告，避免其他业务接口故障影响入口和首屏。
const list = document.getElementById("homeNewsList");

async function loadNews() {
  list.setAttribute("aria-busy", "true");
  try {
    const response = await fetch(`${getApiBase()}/announcements`, { signal: AbortSignal.timeout(8000) });
    if (!response.ok) throw new Error("公告请求失败");
    const result = await response.json();
    if (result.code !== 200 || !Array.isArray(result.data)) throw new Error("公告数据不可用");
    const announcements = result.data.filter(item => item.isActive === 1)
      .sort((a, b) => String(b.announceDate || "").localeCompare(String(a.announceDate || ""))).slice(0, 3);
    list.replaceChildren();
    if (!announcements.length) {
      showState("新的征程即将展开，联赛公告将在这里更新。");
      return;
    }
    announcements.forEach((item, index) => {
      const link = document.createElement("a");
      link.className = "home-news-row";
      link.href = "announcements.html";
      const number = document.createElement("span");
      number.className = "news-index";
      number.textContent = String(index + 1).padStart(2, "0");
      const content = document.createElement("div");
      const date = document.createElement("time");
      date.textContent = item.announceDate || "联赛公告";
      if (item.announceDate) date.dateTime = item.announceDate;
      const title = document.createElement("h3");
      title.textContent = item.title;
      content.append(date, title);
      const arrow = document.createElement("span");
      arrow.className = "news-arrow";
      arrow.textContent = "↗";
      arrow.setAttribute("aria-hidden", "true");
      link.append(number, content, arrow);
      list.append(link);
    });
  } catch {
    showState("公告暂时未能加载，请稍后重试。", true);
  } finally {
    list.setAttribute("aria-busy", "false");
  }
}

function showState(message, retry = false) {
  const state = document.createElement("div");
  state.className = "home-news-state";
  const text = document.createElement("p");
  text.textContent = message;
  state.append(text);
  if (retry) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "text-link";
    button.textContent = "重新加载 ↻";
    button.addEventListener("click", () => {
      button.disabled = true;
      button.textContent = "正在加载…";
      loadNews();
    });
    state.append(button);
  }
  list.replaceChildren(state);
}

loadNews();

// 保留战队区重新开放时的数据渲染；准备期不发起无用请求。
const teamGrid = document.getElementById("homeTeamGrid");
if (teamGrid && getComputedStyle(document.getElementById("homeTeams")).display !== "none") {
  Promise.all([import("./services/api.js"), import("./features/teams.js")])
    .then(async ([api, teams]) => teams.renderHomeTeams(await api.getTeams()))
    .catch(() => { teamGrid.textContent = "战队信息暂时未能加载，请稍后刷新。"; });
}
