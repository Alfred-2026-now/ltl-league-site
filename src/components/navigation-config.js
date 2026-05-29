// 统一导航配置
export const ADMIN_NAV = [
  { href: "admin-matches.html", text: "赛程管理" },
  { href: "admin-p-ledger.html", text: "P币流水" },
  { href: "admin-valuation.html", text: "身价管理" },
  { href: "admin-reward-rules.html", text: "奖励规则" },
  { href: "admin-announcements.html", text: "公告管理" },
  { href: "admin-players.html", text: "选手管理" },
  { href: "admin-player-deposit-ledger.html", text: "选手存款流水" },
  { href: "index.html", text: "返回官网" }
];

export const PUBLIC_NAV = [
  { href: "announcements.html", text: "公告" },
  { href: "standings.html", text: "积分榜" },
  { href: "teams.html", text: "队伍" },
  { href: "player-rankings.html", text: "选手榜" },
  { href: "rules.html", text: "规则" },
  { href: "tools.html", text: "计算器" },
  { href: "schedule.html", text: "赛程" },
  { href: "match-history.html", text: "战绩" }
];

// 渲染导航HTML的函数
export function renderNav(navItems, currentPage) {
  return navItems.map(item => {
    const isActive = item.href === currentPage ? ' class="active"' : "";
    return `<a href="${item.href}"${isActive}>${item.text}</a>`;
  }).join("");
}

// 渲染管理后台页面的完整导航HTML
export function renderAdminNav(currentPage, brandText) {
  const navLinks = renderNav(ADMIN_NAV, currentPage);
  return `
    <header class="site-header">
      <nav class="nav">
        <a class="brand" href="admin-matches.html"><span class="brand-mark">LTL</span><span class="brand-text">${brandText}</span></a>
        <button class="nav-toggle" id="navToggle" aria-label="展开导航">☰</button>
        <div class="nav-links" id="navLinks">
          ${navLinks}
        </div>
      </nav>
    </header>
  `;
}

// 渲染前台页面的完整导航HTML
export function renderPublicNav(currentPage) {
  const navLinks = renderNav(PUBLIC_NAV, currentPage);
  return `
    <header class="site-header">
      <nav class="nav">
        <a class="brand" href="index.html"><span class="brand-mark">LTL</span><span class="brand-text">联赛规则中心</span></a>
        <button class="nav-toggle" id="navToggle" aria-label="展开导航">☰</button>
        <div class="nav-links" id="navLinks">
          ${navLinks}
        </div>
      </nav>
    </header>
  `;
}
