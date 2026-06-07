import { readFileSync } from "node:fs";
import assert from "node:assert/strict";

const service = readFileSync("backend/src/main/java/com/ltl/league/admin/service/impl/AdminAssetServiceImpl.java", "utf8");
const migration = readFileSync("backend/src/main/resources/db/migration_league_assets.sql", "utf8");
const schema = readFileSync("backend/src/main/resources/db/schema.sql", "utf8");
const currentLeagueAssetsMethod = service.match(/private Integer currentLeagueAssets\(\) \{[\s\S]*?\n    \}/)?.[0] || "";

assert.match(currentLeagueAssetsMethod, /orderByDesc\(LeagueAssetLedger::getCreatedAt\)/);
assert.match(currentLeagueAssetsMethod, /orderByDesc\(LeagueAssetLedger::getId\)/);
assert.match(currentLeagueAssetsMethod, /getBalanceAfter\(\)/);
assert.doesNotMatch(currentLeagueAssetsMethod, /mapToInt|SUM|sum\(/i);
assert.doesNotMatch(service, /LeagueAssetAccount/);
assert.doesNotMatch(migration, /league_asset_account/);
assert.doesNotMatch(schema, /league_asset_account/);
assert.match(migration, /idx_latest_balance/);
assert.match(schema, /idx_latest_balance/);
