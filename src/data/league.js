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

export const schedule = [
  {
    round: "第一轮BO3",
    matches: "秦国队vs燕国队｜楚国队vs蜀国队｜吴国队vs越国队"
  },
  {
    round: "第二轮BO2",
    matches: "楚国队vs越国队｜秦国队vs蜀国队｜吴国队vs燕国队"
  }
];
