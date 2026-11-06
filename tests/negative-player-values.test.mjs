import test from "node:test";
import assert from "node:assert/strict";
import { calculateWeightedLineValue, formatLineValue } from "../src/admin/lineup-value.js";
import { calcLoanFee, calcLuxuryTax, getLeagueStandardR, getTeamTotal } from "../src/services/leagueMetrics.js";

test("negative values reduce lineup totals, including advantage discounts", () => {
  assert.equal(calculateWeightedLineValue([
    { playerValue: -1000, gamesPlayed: 1 },
    { playerValue: 500, gamesPlayed: 1 }
  ], 2), -250);
  assert.equal(calculateWeightedLineValue([
    { playerValue: 500, gamesPlayed: 2, advantageTiers: [2000] }
  ], 2), -500);
  assert.equal(formatLineValue(-250.5), "-250.5");
});

test("nonpositive loan values have zero fees and zero shares", () => {
  for (const value of [-1000, 0]) {
    for (const format of ["BO2", "BO3"]) {
      for (const type of ["free", "original_team"]) {
        assert.deepEqual(calcLoanFee(value, format, type), {
          fee: 0, player: 0, sourceTeam: 0, league: 0
        });
      }
    }
  }
});

test("team and league totals retain negatives and luxury tax stays nonnegative", () => {
  const teams = [{ players: [["A", -1000], ["B", 600]] }];
  assert.equal(getTeamTotal(teams[0]), -400);
  assert.equal(getLeagueStandardR(teams), -1000);
  assert.equal(calcLuxuryTax(teams, -2000, "BO2").tax, 0);
  assert.equal(calcLuxuryTax(teams, -500, "BO2").tax, 336);
});
