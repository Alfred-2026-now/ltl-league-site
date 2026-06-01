// 管理后台统一导航注入脚本
(function() {
  // 确保DOM准备好后再执行
  function initNav() {
    const currentPage = window.location.pathname.split('/').pop() || 'index.html';
    const brandText = document.querySelector('.brand-text')?.textContent || '管理后台';

    const navItems = [
      { href: "admin-matches.html", text: "赛程管理" },
      { href: "admin-p-ledger.html", text: "P币流水" },
      { href: "admin-valuation.html", text: "身价管理" },
      { href: "admin-reward-rules.html", text: "奖励规则" },
      { href: "admin-rules.html", text: "规则管理" },
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

      // 直接在这里绑定导航事件
      const toggle = document.getElementById("navToggle");
      if (toggle) {
        toggle.addEventListener("click", () => navLinksContainer.classList.toggle("show"));
      }

      // 为所有链接添加点击关闭菜单的事件
      navLinksContainer.querySelectorAll("a").forEach(anchor => {
        anchor.addEventListener("click", () => navLinksContainer.classList.remove("show"));
      });
    }
  }

  // 如果DOM已经准备好，立即执行；否则等待DOMContentLoaded
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initNav);
  } else {
    initNav();
  }
})();
