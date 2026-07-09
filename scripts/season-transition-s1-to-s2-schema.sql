-- LTL 联赛 S1→S2 过渡：Schema 迁移（DDL）
-- 注意：MySQL DDL 自动提交，无法事务回滚，执行前务必备份数据库
-- 生成时间: 2026-07-09

USE ltl_league;

-- 1. teams 增加 season 字段（默认 s1，现有 6 支战队自动标记为上赛季）
ALTER TABLE `teams`
  ADD COLUMN `season` VARCHAR(20) NOT NULL DEFAULT 's1'
  COMMENT '赛季标识（如 s1/s2），与 matches.season 对齐' AFTER `state`;

-- 2. teams 唯一键由 (state) 改为 (state, season, deleted)，允许不同赛季复用国家简称
ALTER TABLE `teams`
  DROP INDEX `uk_state`,
  ADD UNIQUE KEY `uk_state_season` (`state`, `season`, `deleted`);

-- 3. players.team_id 改为可空（NULL 表示自由人），与实际实现对齐
ALTER TABLE `players`
  MODIFY COLUMN `team_id` BIGINT UNSIGNED NULL COMMENT '所属队伍ID（NULL表示自由人）';

-- 验证
SELECT '=== teams.season 列 ===' AS '';
SHOW COLUMNS FROM `teams` LIKE 'season';
SELECT '=== teams 新唯一键 ===' AS '';
SHOW INDEX FROM `teams` WHERE Key_name = 'uk_state_season';
SELECT '=== players.team_id 可空 ===' AS '';
SHOW COLUMNS FROM `players` LIKE 'team_id';
