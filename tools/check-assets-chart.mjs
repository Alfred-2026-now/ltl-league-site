import assert from "node:assert/strict";
import { buildChangeChartModel, findNearestChartPoint } from "../src/admin/assets-chart.js";

const rows = [
  { date: "2026-06-01", teamDelta: -10, leagueDelta: 20, playerDelta: 5 },
  { date: "2026-06-02", teamDelta: 30, leagueDelta: 0, playerDelta: -15 }
];

const model = buildChangeChartModel(rows, {
  teamAssets: 100,
  leagueAssets: 200,
  playerAssets: 300
});

assert.equal(model.series.length, 3);
assert.equal(model.labels[0], "06-01");
assert.equal(model.labels[1], "06-02");
assert.equal(model.minValue, 70);
assert.equal(model.maxValue, 315);
assert.equal(model.series[0].key, "teamDelta");
assert.deepEqual(model.series[0].values, [70, 100]);
assert.deepEqual(model.series[1].values, [200, 200]);
assert.deepEqual(model.series[2].values, [315, 300]);

const nearest = findNearestChartPoint([
  { x: 10, y: 10, label: "06-01", seriesLabel: "队伍资产", value: 70 },
  { x: 50, y: 50, label: "06-02", seriesLabel: "联盟资产", value: 200 }
], 12, 12, 12);

assert.equal(nearest.seriesLabel, "队伍资产");
assert.equal(nearest.value, 70);
assert.equal(findNearestChartPoint([{ x: 100, y: 100 }], 10, 10, 12), null);
