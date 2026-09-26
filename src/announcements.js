import { getAnnouncements } from "./services/api.js";
import { renderAnnouncements } from "./features/content.js";

const list = document.getElementById("announcementList");

function showState(message, retry = false) {
  const state = document.createElement("div");
  state.className = "announcement-state";
  const text = document.createElement("p");
  text.textContent = message;
  state.append(text);
  if (retry) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "btn";
    button.textContent = "重新加载";
    button.addEventListener("click", loadAnnouncements);
    state.append(button);
  }
  list.replaceChildren(state);
}

async function loadAnnouncements() {
  list.setAttribute("aria-busy", "true");
  showState("正在加载公告…");
  try {
    // 公告页只依赖公告接口，不等待战队、规则参数和战绩。
    const announcements = await getAnnouncements({ signal: AbortSignal.timeout(10000) });
    if (announcements.length) {
      renderAnnouncements(announcements);
    } else {
      showState("暂无已发布的公告。");
    }
  } catch {
    showState("公告暂时未能加载，请稍后重试。", true);
  } finally {
    list.setAttribute("aria-busy", "false");
  }
}

loadAnnouncements();
