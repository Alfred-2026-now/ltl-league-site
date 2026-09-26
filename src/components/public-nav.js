// 所有前台页面共用导航；公共入口先渲染，账户请求不阻塞导航。
(async function() {
  const { getApiBase } = await import("../config/api.js");
  const currentPage = window.location.pathname.split('/').pop() || 'index.html';
  const navLinks = document.getElementById('navLinks');
  const toggle = document.getElementById('navToggle');
  if (!navLinks || !toggle) return;

  const baseNavItems = [
    { href: 'index.html', text: '首页' },
    { href: 'announcements.html', text: '公告' },
    { href: 'standings.html', text: '战队榜' },
    { href: 'teams.html', text: '队伍' },
    { href: 'player-rankings.html', text: '选手榜' },
    { href: 'rules.html', text: '规则' },
    { href: 'tools.html', text: '计算器' },
    { href: 'schedule.html', text: '赛程' },
    { href: 'match-history.html', text: '战绩' },
    { href: 'event-tasks.html', text: '赛事任务' },
    { href: 'prize-exchange.html', text: '积分兑换' }
  ];

  function escapeHtml(value) {
    return String(value ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  async function fetchCurrentUser() {
    try {
      const response = await fetch(`${getApiBase()}/user/info`, {
        credentials: 'include', signal: AbortSignal.timeout(8000)
      });
      if (!response.ok) return response.status === 401 ? null : undefined;
      const data = await response.json();
      return data.code === 200 ? data.data : null;
    } catch {
      return undefined;
    }
  }

  // 使用统一品牌，不再把首页 logo 插入导航标签组。
  const brand = toggle.closest('nav').querySelector('.brand');
  if (brand && !brand.querySelector('.brand-caption')) {
    brand.setAttribute('aria-label', 'LTL 联赛首页');
    const caption = document.createElement('span');
    caption.className = 'brand-caption';
    caption.innerHTML = 'LTL 联赛<small>LEAGUE OF LEGENDS</small>';
    brand.append(caption);
  }
  toggle.closest('nav').setAttribute('aria-label', '主导航');
  toggle.setAttribute('aria-controls', 'navLinks');
  toggle.setAttribute('aria-expanded', 'false');

  function renderNav(user) {
    const items = [...baseNavItems];
    if (user && (user.role & 1)) items.push({ href: 'admin-event-tasks.html', text: '管理后台' });
    if (user && (user.role & 2)) items.push({ href: 'captain.html', text: '队长管理' });
    const links = items.map(item => {
      const active = item.href === currentPage || (currentPage === 'valuation-rules.html' && item.href === 'rules.html');
      return `<a href="${item.href}"${active ? ' class="active" aria-current="page"' : ''}>${item.text}</a>`;
    }).join('');
    const account = user
      ? `<div class="nav-user-menu"><div class="nav-user-identity"><a href="profile.html" class="nav-user-name">${escapeHtml(user.playerName)}</a><span class="nav-user-balance" aria-label="当前个人 P 币">P币 ${Number(user.deposit ?? 0).toLocaleString('zh-CN')}</span></div><a href="#" class="nav-logout" data-logout>登出</a></div>`
      : '<a href="login.html" class="nav-login">登录 <span aria-hidden="true">↗</span></a>';
    navLinks.innerHTML = `<div class="nav-center">${links}</div><div class="nav-right"><span class="nav-divider" aria-hidden="true"></span>${account}</div>`;
  }

  function setMenu(open) {
    navLinks.classList.toggle('show', open);
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', open ? '收起导航' : '展开导航');
    toggle.textContent = open ? '×' : '☰';
  }

  toggle.addEventListener('click', () => setMenu(!navLinks.classList.contains('show')));
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && navLinks.classList.contains('show')) {
      setMenu(false);
      toggle.focus();
    }
  });
  document.addEventListener('click', event => {
    if (!event.target.closest('.nav')) setMenu(false);
  });
  navLinks.addEventListener('click', async event => {
    const link = event.target.closest('a');
    if (!link) return;
    setMenu(false);
    if (link.hasAttribute('data-logout')) {
      event.preventDefault();
      try {
        const response = await fetch(`${getApiBase()}/auth/logout`, { method: 'POST', credentials: 'include' });
        if (response.ok) window.location.href = 'index.html';
      } catch (error) {
        console.error('登出失败', error);
      }
    }
  });

  let currentUser = null;
  let refreshing = false;
  renderNav(currentUser);
  window.refreshNavBalance = async function() {
    if (refreshing) return;
    refreshing = true;
    try {
      const user = await fetchCurrentUser();
      if (user === undefined) return; // 网络故障时保留已知的账户状态。
      const identityChanged = Boolean(user) !== Boolean(currentUser)
        || user?.role !== currentUser?.role || user?.playerName !== currentUser?.playerName;
      currentUser = user;
      if (identityChanged) {
        renderNav(user);
      } else {
        const balance = navLinks.querySelector('.nav-user-balance');
        if (balance && user) balance.textContent = `P币 ${Number(user.deposit ?? 0).toLocaleString('zh-CN')}`;
      }
    } finally {
      refreshing = false;
    }
  };
  window.addEventListener('ltl:balance-changed', window.refreshNavBalance);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) window.refreshNavBalance();
  });
  window.refreshNavBalance();
})();
