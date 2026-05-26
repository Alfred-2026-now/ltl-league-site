import { schedule, teams } from "./data/league.js";
import { setupMatchHistory } from "./features/matches.js";
import { setupNav } from "./features/navigation.js";

document.addEventListener("DOMContentLoaded", () => {
  setupNav();
  setupMatchHistory(schedule, teams);
});
