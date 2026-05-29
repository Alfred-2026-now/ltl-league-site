// 前台页面统一导航注入脚本
(function() {
  const currentPage = window.location.pathname.split('/').pop() || 'index.html';

  const navItems = [
    { href: "announcements.html", text: "公告" },
    { href: "standings.html", text: "积分榜" },
    { href: "teams.html", text: "队伍" },
    { href: "player-rankings.html", text: "选手榜" },
    { href: "rules.html", text: "规则" },
    { href: "tools.html", text: "计算器" },
    { href: "schedule.html", text: "赛程" },
    { href: "match-history.html", text: "战绩" }
  ];

  const navLinksContainer = document.getElementById('navLinks');
  if (navLinksContainer) {
    navLinksContainer.innerHTML = navItems.map(item => {
      const isActive = item.href === currentPage ? ' class="active"' : "";
      return `<a href="${item.href}"${isActive}>${item.text}</a>`;
    }).join("");
  }
})();
