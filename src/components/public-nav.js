// 前台页面统一导航注入脚本
(function() {
  function initNav() {
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
    const toggle = document.getElementById("navToggle");

    if (navLinksContainer && toggle) {
      navLinksContainer.innerHTML = navItems.map(item => {
        const isActive = item.href === currentPage ? ' class="active"' : "";
        return `<a href="${item.href}"${isActive}>${item.text}</a>`;
      }).join("");

      // 立即绑定导航事件
      toggle.addEventListener("click", function(e) {
        e.preventDefault();
        e.stopPropagation();
        navLinksContainer.classList.toggle("show");
      });

      // 为所有链接添加点击关闭菜单的事件
      navLinksContainer.querySelectorAll("a").forEach(anchor => {
        anchor.addEventListener("click", function() {
          navLinksContainer.classList.remove("show");
        });
      });
    }
  }

  // 直接执行，不管 DOM 状态
  // 因为这个脚本放在 body 底部，此时 nav 元素已经解析完成
  initNav();
})();
