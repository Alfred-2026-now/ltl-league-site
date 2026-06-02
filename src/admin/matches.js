import {
  createAdminMatch,
  getTeams,
  listAdminMatches,
  publishSchedule,
  unpublishSchedule,
  updateAdminMatch
} from "./api.js";
import { getStatusMeta } from "../services/matchMetrics.js";

const els = {
  filterSeason: document.getElementById("filterSeason"),
  filterRound: document.getElementById("filterRound"),
  filterTeam: document.getElementById("filterTeam"),
  filterFormat: document.getElementById("filterFormat"),
  filterPublished: document.getElementById("filterPublished"),
  filterStatus: document.getElementById("filterStatus"),
  resetFiltersBtn: document.getElementById("resetFiltersBtn"),
  refreshBtn: document.getElementById("refreshBtn"),
  createMatchBtn: document.getElementById("createMatchBtn"),
  matchTableBody: document.getElementById("matchTableBody"),

  matchDialog: document.getElementById("matchDialog"),
  closeMatchDialogBtn: document.getElementById("closeMatchDialogBtn"),
  dialogTitle: document.getElementById("dialogTitle"),
  saveBtn: document.getElementById("saveBtn"),
  formMatchId: document.getElementById("formMatchId"),
  formRound: document.getElementById("formRound"),
  formRoundLabel: document.getElementById("formRoundLabel"),
  formMatchDate: document.getElementById("formMatchDate"),
  formFormat: document.getElementById("formFormat"),
  formStatus: document.getElementById("formStatus"),
  formHomeTeam: document.getElementById("formHomeTeam"),
  formAwayTeam: document.getElementById("formAwayTeam"),
  formLiveUrl: document.getElementById("formLiveUrl"),
  formNotes: document.getElementById("formNotes")
};

let teams = [];
let matches = [];

function option(label, value) {
  const el = document.createElement("option");
  el.value = value;
  el.textContent = label;
  return el;
}

function renderTeamOptions(select, includeAll = false) {
  select.innerHTML = "";
  if (includeAll) select.append(option("全部", ""));
  teams.forEach(t => select.append(option(`${t.state} · ${t.name}`, String(t.id))));
}

function fmtDate(v) {
  if (!v) return "待定";
  return String(v).replace("T", " ");
}

function badge(text, tone) {
  const colors = {
    success: "rgba(36, 210, 140, .18)",
    danger: "rgba(255, 86, 120, .18)",
    info: "rgba(25, 168, 255, .18)",
    muted: "rgba(168, 182, 214, .12)"
  };
  const bg = colors[tone] || colors.muted;
  return `<span style="display:inline-block; padding:.15rem .5rem; border-radius:999px; background:${bg}; border: 1px solid rgba(126,200,255,.18);">${text}</span>`;
}

function getTeamLabelById(id) {
  const t = teams.find(x => String(x.id) === String(id));
  return t ? `${t.state} · ${t.name}` : String(id || "");
}

function renderTable() {
  if (!els.matchTableBody) return;
  if (!matches.length) {
    els.matchTableBody.innerHTML = `<tr><td colspan="8" style="padding:1rem; color: var(--muted);">暂无比赛</td></tr>`;
    return;
  }

  els.matchTableBody.innerHTML = matches.map(m => {
    const published = String(m.schedulePublished) === "1";
    const publishBadge = published ? badge("已发布", "success") : badge("未发布", "muted");

    const resultBadge = m.hasPublishedResult
      ? badge("已发布赛果", "danger")
      : (m.hasResultDraft ? badge("赛果草稿", "info") : badge("无", "muted"));

    const actions = `
      <div style="display:flex; gap:.35rem; flex-wrap: wrap;">
        <button class="btn ghost" data-action="edit" data-id="${m.id}">编辑</button>
        ${published
          ? `<button class="btn ghost" data-action="unpublish" data-id="${m.id}">撤回</button>`
          : `<button class="btn primary" data-action="publish" data-id="${m.id}">发布</button>`
        }
        <a class="btn ghost" href="admin-match-result.html?matchId=${m.id}">录入赛果</a>
      </div>`;

    return `
      <tr style="border-top: 1px solid rgba(126, 200, 255, 0.18);">
        <td style="padding:.75rem 1rem;">${m.roundLabel || `第 ${m.round} 轮`}</td>
        <td style="padding:.75rem 1rem;">${fmtDate(m.matchDate)}</td>
        <td style="padding:.75rem 1rem;">${getTeamLabelById(m.homeTeamId)} vs ${getTeamLabelById(m.awayTeamId)}</td>
        <td style="padding:.75rem 1rem;">${m.format || ""}</td>
        <td style="padding:.75rem 1rem;">${getStatusMeta(m.status).label}</td>
        <td style="padding:.75rem 1rem;">${publishBadge}</td>
        <td style="padding:.75rem 1rem;">${resultBadge}</td>
        <td style="padding:.75rem 1rem;">${actions}</td>
      </tr>
    `;
  }).join("");
}

function getFilters() {
  return {
    season: els.filterSeason?.value || "",
    round: els.filterRound?.value || "",
    teamId: els.filterTeam?.value || "",
    format: els.filterFormat?.value || "",
    schedulePublished: els.filterPublished?.value || "",
    status: els.filterStatus?.value || ""
  };
}

async function refresh() {
  els.refreshBtn && (els.refreshBtn.disabled = true);
  try {
    matches = await listAdminMatches(getFilters());
    renderTable();
  } catch (e) {
    console.error(e);
    alert(`加载失败：${e.message}`);
  } finally {
    els.refreshBtn && (els.refreshBtn.disabled = false);
  }
}

function openDialog(mode, match) {
  els.formMatchId.value = match?.id ? String(match.id) : "";
  els.dialogTitle.textContent = mode === "edit" ? `编辑比赛 #${match.id}` : "新建比赛";

  els.formRound.value = match?.round ?? "";
  els.formRoundLabel.value = match?.roundLabel ?? "";
  els.formMatchDate.value = match?.matchDate ?? "";
  els.formFormat.value = match?.format ?? "BO3";
  if (els.formStatus) {
    els.formStatus.value = match?.status ?? "scheduled";
  }
  els.formHomeTeam.value = match?.homeTeamId ? String(match.homeTeamId) : (teams[0] ? String(teams[0].id) : "");
  els.formAwayTeam.value = match?.awayTeamId ? String(match.awayTeamId) : (teams[1] ? String(teams[1].id) : "");
  els.formLiveUrl.value = match?.liveUrl ?? "";
  els.formNotes.value = match?.notes ?? "";

  els.matchDialog.showModal();
}

async function saveCurrent() {
  const id = els.formMatchId.value ? Number(els.formMatchId.value) : null;
  const payload = {
    round: els.formRound.value ? Number(els.formRound.value) : null,
    roundLabel: els.formRoundLabel.value || null,
    matchDate: els.formMatchDate.value || null,
    format: els.formFormat.value || null,
    homeTeamId: els.formHomeTeam.value ? Number(els.formHomeTeam.value) : null,
    awayTeamId: els.formAwayTeam.value ? Number(els.formAwayTeam.value) : null,
    liveUrl: els.formLiveUrl.value || null,
    notes: els.formNotes.value || null
  };

  els.saveBtn.disabled = true;
  try {
    if (!payload.round) throw new Error("轮次不能为空");
    if (!payload.homeTeamId || !payload.awayTeamId) throw new Error("主队/客队不能为空");
    if (!payload.format) throw new Error("赛制不能为空");
    if (payload.homeTeamId === payload.awayTeamId) throw new Error("主队和客队不能相同");

    if (id) {
      if (els.formStatus?.value) payload.status = els.formStatus.value;
      await updateAdminMatch(id, payload);
    } else {
      await createAdminMatch(payload);
    }
    els.matchDialog.close();
    await refresh();
  } catch (e) {
    console.error(e);
    alert(`保存失败：${e.message}`);
  } finally {
    els.saveBtn.disabled = false;
  }
}

async function handleRowAction(action, id) {
  const match = matches.find(m => String(m.id) === String(id));
  if (!match) return;

  if (action === "edit") {
    openDialog("edit", match);
    return;
  }
  if (action === "publish") {
    if (!confirm("确认发布赛程？发布后前台可见。")) return;
    try {
      await publishSchedule(match.id);
      await refresh();
    } catch (e) {
      alert(`发布失败：${e.message}`);
    }
    return;
  }
  if (action === "unpublish") {
    if (!confirm("确认撤回赛程？撤回后前台隐藏。")) return;
    try {
      await unpublishSchedule(match.id);
      await refresh();
    } catch (e) {
      alert(`撤回失败：${e.message}`);
    }
  }
}

function wireEvents() {
  els.resetFiltersBtn?.addEventListener("click", () => {
    els.filterSeason.value = "";
    els.filterRound.value = "";
    els.filterTeam.value = "";
    els.filterFormat.value = "";
    els.filterPublished.value = "";
    els.filterStatus.value = "";
  });

  els.refreshBtn?.addEventListener("click", refresh);
  els.createMatchBtn?.addEventListener("click", () => openDialog("create"));
  els.closeMatchDialogBtn?.addEventListener("click", () => els.matchDialog.close());
  els.saveBtn?.addEventListener("click", saveCurrent);

  els.matchTableBody?.addEventListener("click", e => {
    const btn = e.target.closest("button[data-action]");
    if (!btn) return;
    handleRowAction(btn.dataset.action, btn.dataset.id);
  });

  ["filterSeason", "filterRound", "filterTeam", "filterFormat", "filterPublished", "filterStatus"].forEach(id => {
    const el = document.getElementById(id);
    el?.addEventListener("change", refresh);
  });
}

async function init() {
  try {
    teams = await getTeams();
    renderTeamOptions(els.filterTeam, true);
    renderTeamOptions(els.formHomeTeam, false);
    renderTeamOptions(els.formAwayTeam, false);
    wireEvents();
    await refresh();
  } catch (e) {
    console.error(e);
    alert(`初始化失败：${e.message}`);
  }
}

document.addEventListener("DOMContentLoaded", init);

