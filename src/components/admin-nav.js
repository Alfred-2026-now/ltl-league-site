// 管理后台统一导航注入脚本
(function() {
  const currentPage = window.location.pathname.split('/').pop() || 'index.html';
  const brandText = document.querySelector('.brand-text')?.textContent || '管理后台';

  const navItems = [
    { href: "admin-matches.html", text: "赛程管理" },
    { href: "admin-p-ledger.html", text: "P币流水" },
    { href: "admin-valuation.html", text: "身价管理" },
    { href: "admin-reward-rules.html", text: "奖励规则" },
    { href: "admin-announcements.html", text: "公告管理" },
    { href: "admin-players.html", text: "选手管理" },
    { href: "admin-player-deposit-ledger.html", text: "选手存款流水" },
    { href: "index.html", text: "返回官网" }
  ];

  const navLinksContainer = document.getElementById('navLinks');
  if (navLinksContainer) {
    navLinksContainer.innerHTML = navItems.map(item => {
      const isActive = item.href === currentPage ? ' class="active"' : "";
      return `<a href="${item.href}"${isActive}>${item.text}</a>`;
    }).join("");
  }
})();
