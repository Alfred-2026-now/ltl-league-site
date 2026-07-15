// 前台页面统一导航注入脚本
(async function() {
  const { getApiBase } = await import("../config/api.js");

  async function fetchCurrentUser() {
    try {
      const response = await fetch(`${getApiBase()}/auth/current`, {
        credentials: 'include'
      });
      const data = await response.json();
      if (data.code === 200) {
        return data.data;
      }
      return null;
    } catch (error) {
      return null;
    }
  }

  function buildUserSection(currentUser) {
    const divider = document.createElement('span');
    divider.className = 'nav-divider';
    divider.style.cssText = 'border-left: 1px solid rgba(255,255,255,0.2); margin: 0 0.5rem;';

    if (currentUser) {
      const userMenu = document.createElement('div');
      userMenu.className = 'nav-user-menu';
      userMenu.style.cssText = 'display: flex; align-items: center; gap: 0.5rem;';
      userMenu.innerHTML = `
        <a href="profile.html" class="nav-user-name" style="color: #667eea; font-weight: 600; padding: 0.5rem 0;">
          ${currentUser.playerName}
        </a>
        <a href="#" class="nav-logout" onclick="handleLogout(event)" style="color: rgba(255,255,255,0.7); font-size: 0.9rem;">登出</a>
      `;
      return [divider, userMenu];
    }

    const loginLink = document.createElement('a');
    loginLink.href = 'login.html';
    loginLink.textContent = '登录';
    loginLink.style.cssText = 'color: #667eea; font-weight: 600;';
    return [divider, loginLink];
  }

  async function initNav() {
    const currentPage = window.location.pathname.split('/').pop() || 'index.html';

    // 获取当前用户
    const currentUser = await fetchCurrentUser();

    const baseNavItems = [
      { href: "index.html", text: "首页" },
      { href: "announcements.html", text: "公告" },
      { href: "standings.html", text: "战队榜" },
      { href: "teams.html", text: "队伍" },
      { href: "player-rankings.html", text: "选手榜" },
      { href: "rules.html", text: "规则" },
      { href: "tools.html", text: "计算器" },
      { href: "schedule.html", text: "赛程" },
      { href: "match-history.html", text: "战绩" },
      { href: "prize-exchange.html", text: "积分兑换", highlight: true }
    ];

    let navItems = [...baseNavItems];

    // 按角色添加专属入口（位掩码：1=管理员，2=队长，3=两者，两个 tab 独立显示）
    if (currentUser && (currentUser.role & 1)) {
      navItems.push({ href: "admin-matches.html", text: "管理后台", highlight: true });
    }
    if (currentUser && (currentUser.role & 2)) {
      navItems.push({ href: "captain.html", text: "队长管理", highlight: true });
    }

    const navLinksContainer = document.getElementById('navLinks');
    const toggle = document.getElementById("navToggle");

    if (navLinksContainer && toggle) {
      const isHomePage = currentPage === 'index.html';

      const linksHtml = navItems.map(item => {
        const isActive = item.href === currentPage ? ' class="active"' : "";
        const style = item.highlight ? ' style="color: #667eea; font-weight: 600;"' : "";
        return `<a href="${item.href}"${isActive}${style}>${item.text}</a>`;
      }).join("");

      if (isHomePage) {
        // 首页：LOGO + 导航链接居中，登录区右对齐
        navLinksContainer.innerHTML =
          `<div class="nav-center"><img class="nav-inline-logo" src="assets/ltl-logo.webp" alt="LTL联赛" />${linksHtml}</div>` +
          `<div class="nav-right"></div>`;

        const rightGroup = navLinksContainer.querySelector('.nav-right');
        buildUserSection(currentUser).forEach(el => rightGroup.appendChild(el));
      } else {
        // 其他页面：原有布局
        navLinksContainer.innerHTML = linksHtml;

        buildUserSection(currentUser).forEach(el => navLinksContainer.appendChild(el));
      }

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

    // 暴露登出函数到全局
    window.handleLogout = async function(e) {
      e.preventDefault();
      try {
        await fetch(`${getApiBase()}/auth/logout`, {
          method: 'POST',
          credentials: 'include'
        });
        window.location.href = 'index.html';
      } catch (error) {
        console.error('登出失败', error);
      }
    };
  }

  // 执行初始化
  initNav();
})();
