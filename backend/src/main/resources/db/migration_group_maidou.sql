-- ============================================================
-- LTL · 新增「麦兜」分组（与登峰组/涅槃组并列）
-- 幂等：已存在则跳过
-- 执行：mysql -h <host> -u <user> -p ltl_league < migration_group_maidou.sql
-- ============================================================

INSERT INTO `teams` (`state`, `season`, `name`, `p_coins`, `points`, `rank`, `logo_url`, `description`, `deleted`)
SELECT 'MAIDOU', 's2', '麦兜组', 0, 0, 0, NULL, '麦兜组', 0
WHERE NOT EXISTS (SELECT 1 FROM `teams` WHERE `name` = '麦兜组' AND `deleted` = 0);

-- 注：选手归属由管理后台分配（把选手的队伍设为「麦兜组」即可）。
