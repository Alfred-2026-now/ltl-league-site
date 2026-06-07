import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const source = readFileSync(
  "backend/src/main/java/com/ltl/league/admin/service/impl/AdminEconomyServiceImpl.java",
  "utf8"
);

assert.match(
  source,
  /PLedger lastLedger = pLedgerMapper\.selectOne\(new LambdaQueryWrapper<PLedger>\(\)[\s\S]*?\.orderByDesc\(PLedger::getCreatedAt\)[\s\S]*?\.orderByDesc\(PLedger::getId\)[\s\S]*?\.last\("LIMIT 1"\)\);/,
  "manualAddPLedger must break same-createdAt ties by latest PLedger id"
);
