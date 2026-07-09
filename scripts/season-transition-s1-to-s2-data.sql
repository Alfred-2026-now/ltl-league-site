-- LTL 联赛 S1→S2 过渡：数据迁移（DML）
-- 前置：season-transition-s1-to-s2-schema.sql 已执行成功
-- 事务包裹，执行后用尾部校验查询确认，异常可 ROLLBACK
-- 生成时间: 2026-07-09

USE ltl_league;

START TRANSACTION;

-- 1. 显式确认现有战队标记为 S1（NOT NULL DEFAULT 已自动填充，此处防御性兜底）
UPDATE `teams` SET `season` = 's1'
WHERE (`season` IS NULL OR `season` = '') AND `deleted` = 0;

-- 2. 所有选手变为自由人（保留 value/deposit/position/is_substitute/game_account/puuid）
--    无论原状态是在职(1)、离队(2) 还是已是自由人(3)，统一为 status=3
UPDATE `players` SET
  `team_id` = NULL,
  `status` = 3,
  `is_loan` = 0,
  `loan_team_id` = NULL,
  `updated_at` = NOW()
WHERE `deleted` = 0;

COMMIT;

-- ===== 校验查询 =====
SELECT '=== 战队赛季标记（应为全部 s1）===' AS '';
SELECT `season`, COUNT(*) AS cnt FROM `teams` WHERE `deleted` = 0 GROUP BY `season`;

SELECT '=== S1 战队积分保留情况 ===' AS '';
SELECT `state`, `name`, `points`, `p_coins`, `rank`, `season`
FROM `teams` WHERE `deleted` = 0 ORDER BY `state`;

SELECT '=== 选手状态分布（应全部 status=3）===' AS '';
SELECT `status`, COUNT(*) AS cnt FROM `players` WHERE `deleted` = 0 GROUP BY `status`;

SELECT '=== 仍归属战队的选手数（应为 0）===' AS '';
SELECT COUNT(*) AS still_in_team FROM `players` WHERE `team_id` IS NOT NULL AND `deleted` = 0;

SELECT '=== 抽查选手身价/存款未变 ===' AS '';
SELECT `name`, `value`, `deposit`, `status`, `team_id`
FROM `players` WHERE `deleted` = 0 ORDER BY `id` LIMIT 10;
