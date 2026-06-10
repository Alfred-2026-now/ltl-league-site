import { setupCalculators } from "./features/calculators.js";
import { renderAnnouncements, renderRules, renderStats } from "./features/content.js";
import { renderSchedule } from "./features/matches.js";
import { setupAccordion, setupActiveNav, setupNav } from "./features/navigation.js";
import { renderStandings } from "./features/standings.js";
import { setupTeamManagerAgent } from "./features/teamManagerAgent.js";
import { renderTeams, setupTeamSearch } from "./features/teams.js";
import { loadAllData } from "./services/api.js";

async function initApp() {
  setupTeamManagerAgent();

  try {
    const data = await loadAllData();

    renderStats(data.leagueStats);
    renderAnnouncements(data.announcements);
    renderStandings(data.teams);
    renderTeams(data.teams);
    renderRules(data.rules);
    renderSchedule(data.schedule, data.teams);

    setupAccordion();
    // setupNav() 已在 public-nav.js 中处理，不需要再次调用
    setupTeamSearch(data.teams);
    setupCalculators(data.teams, data.ruleParameters);
    setupActiveNav();
  } catch (error) {
    console.error("应用初始化失败:", error);
    const container = document.querySelector(".container");
    if (container) {
      container.innerHTML = `
        <div style="padding: 2rem; text-align: center;">
          <h2>数据加载失败</h2>
          <p>请检查后端服务是否正常运行</p>
          <p style="color: #999;">错误信息: ${error.message}</p>
        </div>
      `;
    }
  }
}

document.addEventListener("DOMContentLoaded", initApp);
