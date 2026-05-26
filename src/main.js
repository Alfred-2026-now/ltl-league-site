import { announcements, leagueStats, rules, schedule, teams } from "./data/league.js";
import { setupCalculators } from "./features/calculators.js";
import { renderAnnouncements, renderRules, renderStats } from "./features/content.js";
import { renderSchedule } from "./features/matches.js";
import { setupAccordion, setupActiveNav, setupNav } from "./features/navigation.js";
import { renderStandings } from "./features/standings.js";
import { renderTeams, setupTeamSearch } from "./features/teams.js";

document.addEventListener("DOMContentLoaded", () => {
  renderStats(leagueStats);
  renderAnnouncements(announcements);
  renderStandings(teams);
  renderTeams(teams);
  renderRules(rules);
  renderSchedule(schedule, teams);

  setupAccordion();
  setupNav();
  setupTeamSearch(teams);
  setupCalculators(teams);
  setupActiveNav();
});
