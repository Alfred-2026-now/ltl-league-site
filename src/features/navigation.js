export function setupAccordion() {
  document.querySelectorAll(".accordion-title").forEach(button => {
    button.addEventListener("click", () => {
      button.closest(".accordion-item").classList.toggle("open");
    });
  });
}

export function setupNav() {
  const toggle = document.getElementById("navToggle");
  const links = document.getElementById("navLinks");
  if (!toggle || !links) return;

  toggle.addEventListener("click", () => links.classList.toggle("show"));
  document.querySelectorAll(".nav-links a").forEach(anchor => {
    anchor.addEventListener("click", () => links.classList.remove("show"));
  });
}

export function setupActiveNav() {
  const links = [...document.querySelectorAll(".nav-links a")];
  const sections = links.map(anchor => document.querySelector(anchor.getAttribute("href"))).filter(Boolean);

  window.addEventListener("scroll", () => {
    let current = "";
    sections.forEach(section => {
      if (window.scrollY >= section.offsetTop - 120) current = `#${section.id}`;
    });
    links.forEach(anchor => anchor.classList.toggle("active", anchor.getAttribute("href") === current));
  });
}
