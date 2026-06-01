/**
 * LTL League API 服务
 * 从后端获取数据
 */

const API_BASE_URL = "http://123.57.19.160:8080/api";

/**
 * 通用请求方法
 */
async function request(endpoint, options = {}) {
  const url = `${API_BASE_URL}${endpoint}`;
  try {
    const response = await fetch(url, options);
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    const data = await response.json();
    if (data.code !== 200) {
      throw new Error(data.message || "请求失败");
    }
    return data.data;
  } catch (error) {
    console.error(`API 请求失败 [${endpoint}]:`, error);
    throw error;
  }
}

/**
 * 获取所有队伍（包含选手）
 */
export async function getTeams() {
  const data = await request("/teams");
  const allPlayers = await request("/players");

  return data.map(item => {
    const teamPlayers = allPlayers
      .filter(p => p.teamId === item.id)
      .map(p => [p.name, p.value, p.deposit || 0]);

    return {
      id: item.id,
      state: item.state,
      name: item.name,
      p: item.pcoins,
      points: item.points,
      rank: item.rank,
      logoUrl: item.logoUrl,
      players: teamPlayers
    };
  });
}

/**
 * 获取单个队伍详情
 */
export async function getTeamDetail(teamId) {
  return request(`/teams/${teamId}`);
}

/**
 * 获取选手列表
 */
export async function getPlayers() {
  return request("/players");
}

/**
 * 获取队伍选手
 */
export async function getTeamPlayers(teamId) {
  return request(`/teams/${teamId}/players`);
}

function stateToAssetSlug(state) {
  const mapping = {
    秦: "qin",
    楚: "chu",
    蜀: "shu",
    吴: "wu",
    越: "yue",
    燕: "yan"
  };
  return mapping[state] || String(state || "").toLowerCase();
}

function isUploadedScreenshot(item) {
  const url = item?.url ? String(item.url) : "";
  return url.includes("/uploads/");
}

/** 无真实上传时使用队徽占位，与 main 分支体验一致 */
function resolveScoreScreenshots(game, match, index) {
  const uploaded = (game.scoreScreenshots || []).filter(
    item => item?.url && isUploadedScreenshot(item)
  );
  if (uploaded.length) return uploaded;

  if (!match.score) return [];

  const state = game.homeTeam || match.homeTeam || "";
  const slug = stateToAssetSlug(state);
  if (!slug) return [];

  return [
    {
      url: `/assets/thumbs/${slug}-160.png`,
      label: index ? `第${index}局战绩截图` : "战绩截图",
      note: "占位图：未上传战绩截图时展示队徽。",
      isPlaceholder: true
    }
  ];
}

/**
 * 获取所有比赛
 */
export async function getMatches() {
  const data = await request("/matches");

  return data.map(match => {
    const games = (match.games || []).map(game => {
      const index = game.index ?? game.gameIndex ?? null;
      const scoreScreenshots = resolveScoreScreenshots(game, match, index);
      return {
        ...game,
        index,
        scoreScreenshots
      };
    });

    // 转换 P 币流水数据结构
    const pLedger = (match.pLedger || match.pledger || []).map(item => ({
      team: item.teamState || item.team || "",
      type: item.type,
      amount: item.amount,
      reason: item.reason
    }));

    return {
      id: match.matchId || match.id,
      season: match.season,
      round: match.round,
      roundLabel: match.roundLabel,
      date: match.matchDate,
      format: match.format,
      status: match.status,
      homeTeam: match.homeTeam || "",
      awayTeam: match.awayTeam || "",
      score: match.score,
      homePoints: match.homePoints,
      awayPoints: match.awayPoints,
      live: match.live?.url ? { url: match.live.url, label: match.live.label || "直播间" } : null,
      games,
      pLedger,
      valuationChanges: match.valuationChanges || [],
      attachments: match.attachments || [],
      notes: match.notes,
      source: match.source,
      version: match.version
    };
  });
}

/**
 * 获取比赛详情
 */
export async function getMatchDetail(matchId) {
  return request(`/matches/${matchId}`);
}

/**
 * 获取公告列表（仅已发布）
 */
export async function getAnnouncements() {
  const data = await request("/announcements");
  return data
    .filter(item => item.isActive === 1)
    .map(item => ({
      date: item.announceDate,
      title: item.title,
      content: item.content,
      active: item.isActive === 1
    }));
}

/**
 * 获取规则列表
 */
export async function getRules() {
  const data = await request("/rules");
  return data.map(item => ({
    title: item.title,
    content: item.content,
    open: item.isOpen === 1
  }));
}

/**
 * 获取赛程
 */
export async function getSchedule() {
  return request("/schedule");
}

/**
 * 加载所有数据
 */
export async function loadAllData() {
  try {
    const teams = await getTeams();
    const announcements = await getAnnouncements();
    const rules = await getRules();
    const matches = await getMatches();

    const playerCount = teams.reduce((sum, team) => sum + (team.players?.length || 0), 0);

    return {
      teams,
      announcements,
      rules,
      schedule: matches,
      leagueStats: [
        { label: "战队数量", value: String(teams.length), description: teams.map(t => t.state).join("/") },
        { label: "选手数量", value: String(playerCount), description: "当前在战队名单" },
        { label: "赛制", value: "BO2/BO3", description: "周内与周末分开结算" },
        { label: "身价机制", value: "动态浮动", description: "表现好加分，表现差扣分" }
      ]
    };
  } catch (error) {
    console.error("加载数据失败:", error);
    throw error;
  }
}
