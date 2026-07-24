import test from "node:test";
import assert from "node:assert/strict";

import {
  appearanceSlotTotal,
  calculateWeightedLineValue,
  formatLineValue,
  totalGamesFromScores
} from "../src/admin/lineup-value.js";

test("calculates a lineup value from each player's share of the games", () => {
  const value = calculateWeightedLineValue([
    { playerValue: 3000, gamesPlayed: 2 },
    { playerValue: 1500, gamesPlayed: 1 }
  ], 3);

  assert.equal(value, 2500);
});

test("rounds the accumulated weighted value to two decimal places", () => {
  assert.equal(calculateWeightedLineValue([
    { playerValue: 1000, gamesPlayed: 1 }
  ], 3), 333.33);
});

test("applies selected advantage tiers to the player's folded match value", () => {
  assert.equal(calculateWeightedLineValue([
    {
      playerValue: 4000,
      gamesPlayed: 2,
      advantageTiers: [1000, 1500]
    }
  ], 2), 2750);
});

test("uses the BO format game count as the advantage-rule denominator", () => {
  assert.equal(calculateWeightedLineValue([
    {
      playerValue: 4000,
      gamesPlayed: 2,
      advantageTiers: [1500]
    }
  ], 2, 3), 3500);
});

test("counts appearance slots and formats totals for the admin form", () => {
  assert.equal(appearanceSlotTotal([{ gamesPlayed: 2 }, { gamesPlayed: 3 }]), 5);
  assert.equal(formatLineValue(2500), "2500");
  assert.equal(formatLineValue(2500.5), "2500.5");
});

test("derives the actual number of games from the score", () => {
  assert.equal(totalGamesFromScores(2, 1), 3);
  assert.equal(totalGamesFromScores("", 1), 0);
});
