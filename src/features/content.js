export function renderStats(stats) {
  const grid = document.getElementById("statsGrid");
  if (!grid) return;

  grid.innerHTML = stats.map(item => `
    <article class="stat">
      <span>${item.label}</span>
      <strong>${item.value}</strong>
      <small>${item.description}</small>
    </article>
  `).join("");
}

export function renderAnnouncements(announcements) {
  const list = document.getElementById("announcementList");
  if (!list) return;

  if (list.classList.contains("page-timeline")) {
    list.innerHTML = announcements.map((item, index) => `
      <details class="announcement-item${item.active ? " active" : ""}"${index === 0 ? " open" : ""}>
        <summary>
          <time>${item.date}</time>
          <span>${item.title}</span>
          <b>+</b>
        </summary>
        <p>${item.content}</p>
      </details>
    `).join("");
    return;
  }

  list.innerHTML = announcements.map(item => `
    <article class="timeline-item${item.active ? " active" : ""}">
      <time>${item.date}</time>
      <h3>${item.title}</h3>
      <p>${item.content}</p>
    </article>
  `).join("");
}

export function renderRules(rules) {
  const rulebook = document.getElementById("rulebook");
  if (!rulebook) return;

  rulebook.innerHTML = rules.map(rule => `
    <article class="accordion-item${rule.open ? " open" : ""}">
      <button class="accordion-title"><span>${rule.title}</span><b>+</b></button>
      <div class="accordion-panel">${rule.content}</div>
    </article>
  `).join("");
}
