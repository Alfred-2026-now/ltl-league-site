-- LTL League 数据库迁移脚本
-- 执行方式: mysql -h 123.57.19.160 -u ltl_user -p ltl_league < migration_v1.1.sql

USE ltl_league;

-- 1. 添加选手存款字段
ALTER TABLE players ADD COLUMN `deposit` INT NOT NULL DEFAULT 0 COMMENT '选手P币存款' AFTER `status`;

-- 2. 修改team_id字段可为NULL（支持自由人选手）
ALTER TABLE players MODIFY COLUMN `team_id` BIGINT UNSIGNED NULL COMMENT '所属队伍ID';

-- 迁移完成
-- 验证：SELECT * FROM players WHERE status = 3; -- 查看自由人选手
-- 验证：SELECT * FROM players WHERE deposit > 0; -- 查看有存款的选手
