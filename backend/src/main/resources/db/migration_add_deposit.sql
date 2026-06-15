-- 添加选手存款字段
-- 执行方式: mysql -h 123.57.19.160 -u ltl_user -p ltl_league < migration_add_deposit.sql

USE ltl_league;

ALTER TABLE players ADD COLUMN `deposit` INT NOT NULL DEFAULT 0 COMMENT '选手P币存款' AFTER `status`;
