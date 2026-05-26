export const leagueStats = [
  { label: "战队数量", value: "6", description: "秦/楚/蜀/吴/越/燕" },
  { label: "选手数量", value: "31", description: "当前在战队名单" },
  { label: "赛制", value: "BO2/BO3", description: "周内与周末分开结算" },
  { label: "身价机制", value: "动态浮动", description: "表现好加分，表现差扣分" }
];

export const announcements = [
  {
    date: "2026.05.25",
    title: "多人名单奢侈税修正规则",
    content: "队伍可以拥有超过5名在职人数（包括教练），但队伍人数越多，最终奢侈税越高；临时租借选手不计入队伍在职人数。",
    active: true
  },
  {
    date: "2026.05.24",
    title: "多人名单规则讨论版",
    content: "明确奢侈税仍以实际出场5人总身价L为基础，并引入替补修正因子。"
  },
  {
    date: "2026.05.22",
    title: "租借规则与追赶基金",
    content: "BO2租借费45%，BO3租借费60%；取消固定工资补贴，改为面向落后队伍的追赶基金。"
  },
  {
    date: "2026.05.21",
    title: "选手身价动态调整规则启用",
    content: "每场正式比赛后，选手身价根据对位表现、赛果、KDA、参团率、输出占比与荣誉分实时浮动。"
  }
];

export const teams = [
  {
    state: "秦",
    name: "秦国队",
    p: 807,
    points: 9,
    rank: 1,
    players: [
      ["ZerstaN", 1800],
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

export const rules = [
  {
    title: "1. 赛制与积分",
    open: true,
    content: `
      <table class="rule-table">
        <thead><tr><th>赛制</th><th>结果</th><th>积分</th></tr></thead>
        <tbody>
          <tr><td>BO3</td><td>2:0获胜</td><td>胜方+3，败方+0</td></tr>
          <tr><td>BO3</td><td>2:1获胜</td><td>胜方+2，败方+1</td></tr>
          <tr><td>BO2</td><td>2:0获胜</td><td>胜方+3，败方+0</td></tr>
          <tr><td>BO2</td><td>1:1平局</td><td>双方各+1</td></tr>
          <tr><td>BO1</td><td>1:0获胜</td><td>胜方+1，败方+0</td></tr>
          <tr><td>弃赛</td><td>弃赛方</td><td>-3，对手按对应赛制获得最大胜利积分</td></tr>
        </tbody>
      </table>
    `
  },
  {
    title: "2. 多人名单奢侈税修正规则",
    content: `
      <p>队伍可以拥有超过5名在职人数（包括教练），但队伍在职人数越多，最终奢侈税越高。本场基础奢侈税仍按照实际出场5人的总身价L计算；若队伍在职人数超过5人，则用于奢侈税计算的总身价L乘以修正因子。</p>
      <table class="rule-table">
        <thead><tr><th>在职人数</th><th>修正因子</th></tr></thead>
        <tbody><tr><td>5人</td><td>×1.00</td></tr><tr><td>6人</td><td>×1.10</td></tr><tr><td>7人</td><td>×1.25</td></tr><tr><td>8人</td><td>×1.45</td></tr><tr><td>9人</td><td>×1.70</td></tr><tr><td>10人及以上</td><td>×2.00</td></tr></tbody>
      </table>
      <p class="note">租借选手临时出场不计入队伍在职人数；若正式登记进入队伍名单，则计入在职人数。</p>
    `
  },
  {
    title: "3. 奢侈税分段税率",
    content: `
      <p>应税部分X=max(0，修正后的L−0.92R)，其中R=所有在战队选手的身价平均值×5（目前31位）。</p>
      <div class="two-col">
        <table class="rule-table"><thead><tr><th colspan="2">周内BO2</th></tr></thead><tbody><tr><td>0-1000P</td><td>×0.8</td></tr><tr><td>1001-2000P</td><td>×1.1</td></tr><tr><td>2001-3000P</td><td>×1.4</td></tr><tr><td>3001-4000P</td><td>×1.8</td></tr><tr><td>4000P以上</td><td>×2.3</td></tr></tbody></table>
        <table class="rule-table"><thead><tr><th colspan="2">周末BO3</th></tr></thead><tbody><tr><td>0-1000P</td><td>×1.0</td></tr><tr><td>1001-2000P</td><td>×1.3</td></tr><tr><td>2001-3000P</td><td>×1.7</td></tr><tr><td>3001-4000P</td><td>×2.2</td></tr><tr><td>4000P以上</td><td>×2.8</td></tr></tbody></table>
      </div>
    `
  },
  {
    title: "4. 租借规则",
    content: `
      <ul class="rule-list">
        <li>租借必须由租借队伍、原队伍和选手本人三方同意。</li>
        <li>周内BO2租借费为选手身价45%；周末BO3租借费为选手身价60%。</li>
        <li>若租借选手出任不同位置，由双方队长议定等效身价，并报联盟批准。</li>
        <li>租借费分配：40%进入选手个人账户，40%归原队伍，20%由联盟回收；自由人租借时60%由联盟回收。</li>
        <li>租借选手出场时，其身价计入租借队伍本场出场总身价L，并参与奢侈税计算。</li>
        <li>租借结束后，选手自动回归原队。</li>
        <li>若因选手日程冲突导致缺人且租借队伍P币不足，可申请救急租借，先支付部分租借费，剩余费用从赛后奖金或下周补贴中扣除，并附带利息。</li>
      </ul>
    `
  },
  {
    title: "5. 追赶基金",
    content: `
      <p>取消每队每周固定3000P工资补贴，改为追赶基金。</p>
      <ul class="rule-list">
        <li>每周比赛全部结算后，若队伍排名倒数前2且P币低于5000P，可申请领取补助。</li>
        <li>倒数第1补助4000P，倒数第2补助2000P。</li>
        <li>补助后队伍P币最高不得超过6000P，实际补助=min(应得补助，6000P-当前P币)。</li>
        <li>领取追赶基金后，该小队需保证队内现有成员在下一周内至少一次共同出场比赛。</li>
      </ul>
    `
  },
  {
    title: "6. 身价动态调整与软边界",
    content: `
      <p>每场正式比赛后，选手身价根据“对位表现分+赛果分+数据修正分+荣誉分”调整。对位表现采用非线性对位预期评分差公式；最终按1P取整。</p>
      <p>软边界修正规则：若选手当前身价高于3500P，则其正向涨幅乘以<code>(1500/(当前身价-2000))²</code>；若选手当前身价低于1500P，则其负向跌幅乘以<code>(1000/(2500-当前身价))²</code>。该规则不设置硬上限或硬下限。</p>
    `
  },
  {
    title: "7. P币兑换",
    content: "<p>选手每拥有10000P币，则可兑换10000英雄联盟点券或¥100。</p>"
  }
];

function sampleParticipant({
  team,
  sourceTeam = team,
  playerName,
  accountName = playerName,
  position,
  champion,
  kda,
  cs,
  gold,
  damage,
  taken,
  vision,
  kp,
  objectiveDelta = 0,
  subjectiveDelta = 0,
  before = 2000,
  isLoan = false
}) {
  return {
    position,
    account: {
      puuid: `sample-${accountName.toLowerCase().replaceAll(" ", "-")}`,
      summonerName: accountName,
      gameName: accountName,
      tagLine: "LTL"
    },
    mappedPlayer: {
      playerId: `player-${playerName.toLowerCase().replaceAll(" ", "-")}`,
      playerName,
      confirmedBy: "管理员示例",
      confirmedAt: "2026-05-26T20:00:00+08:00"
    },
    rosterContext: {
      representingTeam: team,
      sourceTeam,
      isLoan,
      isSubstitute: false
    },
    loadout: {
      champion: { id: null, name: champion },
      spells: ["闪现", position === "JUG" ? "惩戒" : "待导入"],
      runes: { primary: "待导入", secondary: "待导入" },
      items: { final: [], purchases: [] }
    },
    combatStats: {
      kills: kda[0],
      deaths: kda[1],
      assists: kda[2],
      largestKillingSpree: 0,
      damageDealtToChampions: damage,
      physicalDamageDealtToChampions: 0,
      magicDamageDealtToChampions: 0,
      trueDamageDealtToChampions: 0,
      totalDamageDealt: 0,
      totalDamageTaken: taken,
      damageSelfMitigated: 0,
      healingDone: 0,
      shieldingDone: 0
    },
    economyStats: {
      goldEarned: gold,
      goldSpent: 0,
      totalMinionsKilled: cs,
      neutralMinionsKilled: position === "JUG" ? cs : 0,
      csPerMinute: 0
    },
    visionStats: {
      visionScore: vision,
      wardsPlaced: 0,
      wardsKilled: 0,
      controlWardsPurchased: 0
    },
    objectiveStats: {
      turretKills: 0,
      inhibitorKills: 0,
      dragonKills: position === "JUG" ? 1 : 0,
      baronKills: 0
    },
    derivedStats: {
      killParticipation: kp,
      damageShare: 0,
      goldShare: 0,
      deathShare: 0
    },
    valuation: {
      before,
      objectiveDelta,
      subjectiveDelta,
      subjectiveReason: subjectiveDelta ? "示例主观表现分" : "",
      after: before + objectiveDelta + subjectiveDelta
    }
  };
}

const qinGameOne = [
  sampleParticipant({ team: "秦", playerName: "ZerstaN", position: "TOP", champion: "奎桑提", kda: [3, 2, 8], cs: 244, gold: 13280, damage: 21600, taken: 31800, vision: 27, kp: 0.61, before: 1800, objectiveDelta: 45 }),
  sampleParticipant({ team: "秦", playerName: "天下人", position: "JUG", champion: "蔚", kda: [4, 1, 12], cs: 181, gold: 12640, damage: 14800, taken: 28200, vision: 34, kp: 0.89, before: 3030, objectiveDelta: 82, subjectiveDelta: 20 }),
  sampleParticipant({ team: "秦", playerName: "LOL历史总得分王", position: "MID", champion: "阿狸", kda: [6, 1, 7], cs: 279, gold: 15120, damage: 28900, taken: 17400, vision: 25, kp: 0.72, before: 1831, objectiveDelta: 96 }),
  sampleParticipant({ team: "秦", playerName: "樱岛麻衣", position: "BOT", champion: "泽丽", kda: [8, 2, 6], cs: 312, gold: 17450, damage: 34100, taken: 19600, vision: 22, kp: 0.78, before: 2926, objectiveDelta: 110 }),
  sampleParticipant({ team: "秦", playerName: "BUAA2wh", position: "SUP", champion: "洛", kda: [1, 3, 16], cs: 38, gold: 8620, damage: 7200, taken: 24100, vision: 71, kp: 0.94, before: 3466, objectiveDelta: 54 }),
  sampleParticipant({ team: "燕", playerName: "想你时风起", position: "TOP", champion: "鳄鱼", kda: [2, 4, 3], cs: 226, gold: 11240, damage: 18900, taken: 33600, vision: 19, kp: 0.42, before: 3279, objectiveDelta: -38 }),
  sampleParticipant({ team: "燕", playerName: "凯隐不是该赢吗", position: "JUG", champion: "佛耶戈", kda: [3, 5, 4], cs: 168, gold: 10780, damage: 16700, taken: 27100, vision: 28, kp: 0.58, before: 2402, objectiveDelta: -44 }),
  sampleParticipant({ team: "燕", playerName: "不够活跃", position: "MID", champion: "辛德拉", kda: [1, 3, 5], cs: 258, gold: 11630, damage: 22400, taken: 15100, vision: 21, kp: 0.5, before: 2190, objectiveDelta: -26 }),
  sampleParticipant({ team: "燕", playerName: "明栗双收", position: "BOT", champion: "厄斐琉斯", kda: [3, 4, 3], cs: 296, gold: 13920, damage: 30100, taken: 20800, vision: 18, kp: 0.5, before: 1268, objectiveDelta: -12 }),
  sampleParticipant({ team: "燕", playerName: "坑货、别靠近我", position: "SUP", champion: "泰坦", kda: [0, 6, 7], cs: 42, gold: 7450, damage: 6100, taken: 26300, vision: 63, kp: 0.58, before: 2044, objectiveDelta: -35 })
];

const qinGameTwo = [
  sampleParticipant({ team: "秦", playerName: "ZerstaN", position: "TOP", champion: "兰博", kda: [1, 4, 5], cs: 218, gold: 10460, damage: 20500, taken: 28700, vision: 21, kp: 0.43, before: 1845, objectiveDelta: -24 }),
  sampleParticipant({ team: "秦", playerName: "天下人", position: "JUG", champion: "盲僧", kda: [2, 5, 6], cs: 154, gold: 9980, damage: 13100, taken: 25500, vision: 30, kp: 0.57, before: 3132, objectiveDelta: -31 }),
  sampleParticipant({ team: "秦", playerName: "LOL历史总得分王", position: "MID", champion: "沙皇", kda: [4, 2, 4], cs: 286, gold: 13820, damage: 26800, taken: 16500, vision: 24, kp: 0.57, before: 1927, objectiveDelta: 18 }),
  sampleParticipant({ team: "秦", playerName: "樱岛麻衣", position: "BOT", champion: "霞", kda: [2, 4, 6], cs: 301, gold: 13190, damage: 24600, taken: 19800, vision: 19, kp: 0.57, before: 3036, objectiveDelta: -16 }),
  sampleParticipant({ team: "秦", playerName: "BUAA2wh", position: "SUP", champion: "芮尔", kda: [0, 6, 8], cs: 35, gold: 7120, damage: 5900, taken: 30200, vision: 64, kp: 0.57, before: 3520, objectiveDelta: -22 }),
  sampleParticipant({ team: "燕", playerName: "想你时风起", position: "TOP", champion: "剑魔", kda: [5, 1, 8], cs: 247, gold: 14950, damage: 30400, taken: 34100, vision: 24, kp: 0.68, before: 3241, objectiveDelta: 74 }),
  sampleParticipant({ team: "燕", playerName: "凯隐不是该赢吗", position: "JUG", champion: "赵信", kda: [4, 2, 11], cs: 172, gold: 12840, damage: 17800, taken: 29400, vision: 32, kp: 0.79, before: 2358, objectiveDelta: 62 }),
  sampleParticipant({ team: "燕", playerName: "不够活跃", position: "MID", champion: "发条", kda: [5, 1, 9], cs: 271, gold: 14350, damage: 29100, taken: 14700, vision: 23, kp: 0.74, before: 2164, objectiveDelta: 76, subjectiveDelta: 15 }),
  sampleParticipant({ team: "燕", playerName: "明栗双收", position: "BOT", champion: "卡莎", kda: [6, 2, 7], cs: 318, gold: 16420, damage: 33100, taken: 18900, vision: 20, kp: 0.68, before: 1256, objectiveDelta: 88 }),
  sampleParticipant({ team: "燕", playerName: "坑货、别靠近我", position: "SUP", champion: "牛头", kda: [1, 3, 14], cs: 39, gold: 8320, damage: 6500, taken: 27900, vision: 70, kp: 0.79, before: 2009, objectiveDelta: 48 })
];

const qinGameThree = [
  sampleParticipant({ team: "秦", playerName: "ZerstaN", position: "TOP", champion: "奥恩", kda: [2, 1, 11], cs: 238, gold: 12460, damage: 17400, taken: 35600, vision: 26, kp: 0.65, before: 1821, objectiveDelta: 51 }),
  sampleParticipant({ team: "秦", playerName: "天下人", position: "JUG", champion: "猴子", kda: [5, 2, 10], cs: 176, gold: 13270, damage: 19200, taken: 30100, vision: 33, kp: 0.75, before: 3101, objectiveDelta: 79 }),
  sampleParticipant({ team: "秦", playerName: "LOL历史总得分王", position: "MID", champion: "塞拉斯", kda: [7, 2, 8], cs: 263, gold: 15680, damage: 33600, taken: 20200, vision: 22, kp: 0.75, before: 1945, objectiveDelta: 104 }),
  sampleParticipant({ team: "秦", playerName: "樱岛麻衣", position: "BOT", champion: "金克丝", kda: [9, 1, 7], cs: 329, gold: 18110, damage: 38200, taken: 17600, vision: 21, kp: 0.8, before: 3020, objectiveDelta: 126, subjectiveDelta: 25 }),
  sampleParticipant({ team: "秦", playerName: "BUAA2wh", position: "SUP", champion: "璐璐", kda: [0, 2, 18], cs: 31, gold: 8740, damage: 4900, taken: 19800, vision: 76, kp: 0.9, before: 3498, objectiveDelta: 62 }),
  sampleParticipant({ team: "燕", playerName: "想你时风起", position: "TOP", champion: "塞恩", kda: [1, 4, 6], cs: 229, gold: 10570, damage: 15800, taken: 38900, vision: 22, kp: 0.5, before: 3315, objectiveDelta: -42 }),
  sampleParticipant({ team: "燕", playerName: "凯隐不是该赢吗", position: "JUG", champion: "破败之王", kda: [3, 5, 5], cs: 164, gold: 10920, damage: 17100, taken: 27600, vision: 27, kp: 0.57, before: 2420, objectiveDelta: -39 }),
  sampleParticipant({ team: "燕", playerName: "不够活跃", position: "MID", champion: "维克托", kda: [2, 3, 5], cs: 281, gold: 12180, damage: 25200, taken: 15800, vision: 24, kp: 0.5, before: 2255, objectiveDelta: -28 }),
  sampleParticipant({ team: "燕", playerName: "明栗双收", position: "BOT", champion: "韦鲁斯", kda: [3, 4, 4], cs: 303, gold: 13250, damage: 28300, taken: 18400, vision: 18, kp: 0.5, before: 1344, objectiveDelta: -17 }),
  sampleParticipant({ team: "燕", playerName: "坑货、别靠近我", position: "SUP", champion: "烈娜塔", kda: [0, 5, 7], cs: 36, gold: 7240, damage: 5200, taken: 22500, vision: 67, kp: 0.5, before: 2057, objectiveDelta: -31 })
];

function gameSample(index, winner, blueTeam, redTeam, participants, durationSeconds) {
  return {
    index,
    winner,
    blueTeam,
    redTeam,
    homeTeam: "秦",
    awayTeam: "燕",
    durationSeconds,
    source: {
      type: "manual_entry",
      gameId: `sample-qin-yan-${index}`,
      gameVersion: "示例版本"
    },
    lineups: {
      home: participants.slice(0, 5),
      away: participants.slice(5)
    },
    teamStats: {
      blue: { kills: participants.slice(0, 5).reduce((sum, player) => sum + player.combatStats.kills, 0), gold: participants.slice(0, 5).reduce((sum, player) => sum + player.economyStats.goldEarned, 0), towers: 8, dragons: 3, barons: index === 3 ? 1 : 0 },
      red: { kills: participants.slice(5).reduce((sum, player) => sum + player.combatStats.kills, 0), gold: participants.slice(5).reduce((sum, player) => sum + player.economyStats.goldEarned, 0), towers: winner === "燕" ? 9 : 3, dragons: winner === "燕" ? 3 : 1, barons: winner === "燕" ? 1 : 0 }
    },
    timeline: {
      keyEvents: [
        { time: "08:12", label: "首条小龙" },
        { time: "21:40", label: "关键团战" }
      ]
    }
  };
}

export const schedule = [
  {
    id: "s1-r1-qin-yan",
    season: "第一赛季",
    round: 1,
    roundLabel: "第一轮",
    date: "2026.05.26",
    format: "BO3",
    status: "finished",
    homeTeam: "秦",
    awayTeam: "燕",
    score: { home: 2, away: 1 },
    source: "mixed",
    version: "v1",
    live: null,
    games: [
      gameSample(1, "秦", "秦", "燕", qinGameOne, 1942),
      gameSample(2, "燕", "燕", "秦", qinGameTwo, 2036),
      gameSample(3, "秦", "秦", "燕", qinGameThree, 2218)
    ],
    pLedger: [
      { team: "秦", type: "match_reward", amount: 1800, reason: "BO3 2:1 获胜示例奖励" },
      { team: "燕", type: "match_reward", amount: 800, reason: "BO3 1:2 败方积分奖励示例" },
      { team: "秦", type: "luxury_tax", amount: -420, reason: "示例奢侈税扣款" }
    ],
    valuationChanges: [
      { playerName: "樱岛麻衣", before: 2926, objective: 126, subjective: 25, after: 3077, reason: "第三局关键输出" },
      { playerName: "不够活跃", before: 2190, objective: 76, subjective: 15, after: 2281, reason: "第二局表现优秀" },
      { playerName: "天下人", before: 3030, objective: 82, subjective: 20, after: 3132, reason: "第一局节奏优势" }
    ],
    attachments: [
      { label: "示例战绩截图", url: "#" },
      { label: "示例回放文件", url: "#" }
    ],
    notes: "示例数据：用于预览历史比赛详情页结构，非真实比赛结果。"
  },
  {
    id: "s1-r1-chu-shu",
    season: "第一赛季",
    round: 1,
    roundLabel: "第一轮",
    date: "2026.05.27 20:00",
    format: "BO3",
    status: "live",
    homeTeam: "楚",
    awayTeam: "蜀",
    score: null,
    source: null,
    version: null,
    live: { label: "正在直播", url: "https://live.bilibili.com/" },
    games: [],
    pLedger: [],
    valuationChanges: [],
    attachments: [],
    notes: "示例直播状态：可在卡片和详情页展示直播链接。"
  },
  {
    id: "s1-r1-wu-yue",
    season: "第一赛季",
    round: 1,
    roundLabel: "第一轮",
    date: "2026.05.27",
    format: "BO3",
    status: "forfeit",
    homeTeam: "吴",
    awayTeam: "越",
    score: { home: 2, away: 0 },
    forfeitTeam: "越",
    source: "manual_entry",
    version: "v1",
    live: null,
    games: [],
    pLedger: [
      { team: "越", type: "forfeit_penalty", amount: -3000, reason: "弃赛罚款示例" },
      { team: "吴", type: "match_reward", amount: 1200, reason: "对手弃赛，按最大胜利结算示例" }
    ],
    valuationChanges: [],
    attachments: [{ label: "弃赛裁定说明", url: "#" }],
    notes: "示例弃赛：积分按规则文档结算，不产生小局战绩。"
  },
  {
    id: "s1-r2-chu-yue",
    season: "第一赛季",
    round: 2,
    roundLabel: "第二轮",
    date: "待定",
    format: "BO2",
    status: "scheduled",
    homeTeam: "楚",
    awayTeam: "越",
    score: null,
    live: null,
    games: [],
    pLedger: [],
    valuationChanges: [],
    attachments: [],
    notes: "等待比赛时间确认。"
  },
  {
    id: "s1-r2-qin-shu",
    season: "第一赛季",
    round: 2,
    roundLabel: "第二轮",
    date: "待定",
    format: "BO2",
    status: "scheduled",
    homeTeam: "秦",
    awayTeam: "蜀",
    score: null,
    live: null,
    games: [],
    pLedger: [],
    valuationChanges: [],
    attachments: [],
    notes: "等待比赛时间确认。"
  },
  {
    id: "s1-r2-wu-yan",
    season: "第一赛季",
    round: 2,
    roundLabel: "第二轮",
    date: "待定",
    format: "BO2",
    status: "scheduled",
    homeTeam: "吴",
    awayTeam: "燕",
    score: null,
    live: null,
    games: [],
    pLedger: [],
    valuationChanges: [],
    attachments: [],
    notes: "等待比赛时间确认。"
  }
];
