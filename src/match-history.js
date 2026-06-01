import { setupMatchHistory } from "./features/matches.js";
import { setupNav } from "./features/navigation.js";
import { loadAllData } from "./services/api.js";

async function initMatchHistory() {
  try {
    // setupNav() 已在 public-nav.js 中处理，不需要再次调用
    const data = await loadAllData();
    setupMatchHistory(data.schedule, data.teams);
  } catch (error) {
    console.error("比赛历史数据加载失败:", error);
    document.querySelector(".container")?.insertAdjacentHTML("afterbegin", `
      <div style="padding: 2rem; text-align: center; background: #fee;">
        <h3>数据加载失败</h3>
        <p>${error.message}</p>
      </div>
    `);
  }
}

document.addEventListener("DOMContentLoaded", initMatchHistory);
