// Shared admin shell. Page controls and authorization remain owned by each page.
(function () {
  function initNav() {
    const container = document.getElementById('navLinks');
    if (!container) return;
    const page = location.pathname.split('/').pop();
    const aliases = {
      'admin-prizes.html': 'admin-prize-mgmt.html',
      'admin-prize-exchanges.html': 'admin-prize-mgmt.html',
      'admin-match-result.html': 'admin-matches.html'
    };
    const groups = [
      ['赛事运营', [
        ['admin-event-tasks.html', '任务管理', 'task'],
        ['admin-prize-mgmt.html', '奖品兑换', 'gift'],
        ['admin-announcements.html', '公告管理', 'notice']
      ]],
      ['选手管理', [
        ['admin-players.html', '选手档案', 'player'],
        ['admin-player-deposit-ledger.html', '选手积分', 'coin'],
        ['admin-player-rankings.html', '积分排行', 'rank'],
        ['admin-player-bounty.html', '选手赏金', 'target']
      ]],
      ['资产与规则', [
        ['admin-assets.html', '资产监测', 'chart'],
        ['admin-valuation.html', '身价管理', 'scale'],
        ['admin-reward-rules.html', '规则参数调整', 'settings']
      ]]
    ];
    // Keep older team-season pages reachable in their existing context.
    const legacy = [
      ['admin-matches.html', '比赛管理', 'task'],
      ['admin-p-ledger.html', '队伍 P 币流水', 'coin'],
      ['admin-rules.html', '规则内容', 'notice']
    ];
    if (legacy.some(([href]) => href === (aliases[page] || page))) groups.push(['战队赛事', legacy]);
    const paths = {
      task: '<path d="M8 5H5v16h14V5h-3M9 3h6v4H9zM8 12l2 2 5-5M8 18h8"/>',
      gift: '<path d="M3 8h18v4H3zM5 12v9h14v-9M12 8v13M12 8C5 8 5 2 8 3c3 0 4 5 4 5s1-5 4-5c3-1 3 5-4 5"/>',
      notice: '<path d="M4 10v5h4l10 5V5L8 10H4zM8 15l2 6M21 9v7"/>',
      player: '<circle cx="12" cy="7" r="4"/><path d="M4 21v-3a8 8 0 0116 0v3"/>',
      coin: '<circle cx="12" cy="12" r="9"/><path d="M10 17V7h3a3 3 0 010 6h-3"/>',
      rank: '<path d="M3 21V11h6v10M9 21V4h6v17M15 21v-7h6v7"/>',
      target: '<circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="3"/><path d="M12 1v3M12 20v3M1 12h3M20 12h3"/>',
      chart: '<path d="M3 3v18h18M7 16l4-5 4 2 6-8"/>',
      scale: '<path d="M12 3v18M5 7h14M2 15l3-8 3 8H2zM16 15l3-8 3 8h-6zM7 21h10"/>',
      settings: '<path d="M4 6h16M4 12h16M4 18h16"/><path d="M8 3v6M16 9v6M10 15v6"/>'
    };
    const icon = name => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name]}</svg>`;
    container.innerHTML = groups.map(([title, items]) => `<div class="admin-nav-group"><p class="admin-nav-label">${title}</p>${items.map(([href, title, glyph]) => `<a href="${href}"${href === (aliases[page] || page) ? ' class="active" aria-current="page"' : ''}>${icon(glyph)}<span>${title}</span></a>`).join('')}</div>`).join('') + '<a class="admin-return" href="index.html"><span aria-hidden="true">↗</span>返回官网</a>';
    container.setAttribute('aria-label', '后台栏目');
    const nav = container.closest('nav');
    const brand = nav.querySelector('.brand');
    brand.insertAdjacentHTML('beforeend', '<span class="admin-brand-copy">赛事管理中心<small>LTL LEAGUE · ADMIN</small></span>');
    let toggle = document.getElementById('navToggle');
    if (!toggle) {
      toggle = document.createElement('button');
      toggle.id = 'navToggle';
      toggle.className = 'nav-toggle';
      toggle.textContent = '☰';
      nav.insertBefore(toggle, container);
    }
    toggle.type = 'button';
    toggle.setAttribute('aria-controls', 'navLinks');
    const setOpen = open => {
      container.classList.toggle('show', open);
      toggle.setAttribute('aria-expanded', String(open));
      toggle.setAttribute('aria-label', open ? '收起导航' : '展开导航');
      toggle.textContent = open ? '✕' : '☰';
    };
    setOpen(false);
    toggle.addEventListener('click', () => setOpen(!container.classList.contains('show')));
    container.addEventListener('click', event => { if (event.target.closest('a')) setOpen(false); });
    document.addEventListener('keydown', event => {
      if (event.key === 'Escape' && container.classList.contains('show')) { setOpen(false); toggle.focus(); }
    });
    document.addEventListener('click', event => { if (!nav.contains(event.target)) setOpen(false); });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initNav);
  else initNav();
})();
