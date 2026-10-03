-- ============================================================
-- LTL · 未参赛衰减改为「按位置独立计时」
-- 幂等：字段已存在则跳过
-- 存量迁移：把每个选手现有的整人计时器（next_decay_at / decay_count）
--           复制到五个位置各自的计时器，保证既有衰减进度不变。
-- 执行：mysql -h <host> -u <user> -p ltl_league < migration_position_decay_clock.sql
-- ============================================================

-- ---------- 1. 五个位置的下次衰减时间 ----------
SET @s := (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `players` ADD COLUMN `top_decay_at` DATETIME NULL COMMENT ''上路下次衰减时间（按位置独立计时）''',
  'SELECT ''players.top_decay_at 已存在，跳过''')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='players' AND COLUMN_NAME='top_decay_at');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `players` ADD COLUMN `jug_decay_at` DATETIME NULL COMMENT ''打野下次衰减时间''',
  'SELECT ''players.jug_decay_at 已存在，跳过''')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='players' AND COLUMN_NAME='jug_decay_at');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `players` ADD COLUMN `mid_decay_at` DATETIME NULL COMMENT ''中路下次衰减时间''',
  'SELECT ''players.mid_decay_at 已存在，跳过''')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='players' AND COLUMN_NAME='mid_decay_at');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `players` ADD COLUMN `bot_decay_at` DATETIME NULL COMMENT ''下路下次衰减时间''',
  'SELECT ''players.bot_decay_at 已存在，跳过''')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='players' AND COLUMN_NAME='bot_decay_at');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `players` ADD COLUMN `sup_decay_at` DATETIME NULL COMMENT ''辅助下次衰减时间''',
  'SELECT ''players.sup_decay_at 已存在，跳过''')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='players' AND COLUMN_NAME='sup_decay_at');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 2. 五个位置的已衰减次数 ----------
SET @s := (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `players` ADD COLUMN `top_decay_count` INT NOT NULL DEFAULT 0 COMMENT ''上路已衰减次数''',
  'SELECT ''players.top_decay_count 已存在，跳过''')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='players' AND COLUMN_NAME='top_decay_count');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `players` ADD COLUMN `jug_decay_count` INT NOT NULL DEFAULT 0 COMMENT ''打野已衰减次数''',
  'SELECT ''players.jug_decay_count 已存在，跳过''')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='players' AND COLUMN_NAME='jug_decay_count');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `players` ADD COLUMN `mid_decay_count` INT NOT NULL DEFAULT 0 COMMENT ''中路已衰减次数''',
  'SELECT ''players.mid_decay_count 已存在，跳过''')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='players' AND COLUMN_NAME='mid_decay_count');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `players` ADD COLUMN `bot_decay_count` INT NOT NULL DEFAULT 0 COMMENT ''下路已衰减次数''',
  'SELECT ''players.bot_decay_count 已存在，跳过''')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='players' AND COLUMN_NAME='bot_decay_count');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `players` ADD COLUMN `sup_decay_count` INT NOT NULL DEFAULT 0 COMMENT ''辅助已衰减次数''',
  'SELECT ''players.sup_decay_count 已存在，跳过''')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='players' AND COLUMN_NAME='sup_decay_count');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 3. 存量迁移：把现有整人计时器复制到五个位置 ----------
-- 仅当五个位置计时都还为空时执行一次（幂等，避免重复覆盖）
SET @need_copy := (SELECT IF(
  SUM(CASE WHEN top_decay_at IS NOT NULL OR jug_decay_at IS NOT NULL OR mid_decay_at IS NOT NULL
            OR bot_decay_at IS NOT NULL OR sup_decay_at IS NOT NULL THEN 1 ELSE 0 END) = 0,
  1, 0) FROM players);

SET @s := IF(@need_copy = 1,
  'UPDATE `players` SET
     top_decay_at = next_decay_at, jug_decay_at = next_decay_at, mid_decay_at = next_decay_at,
     bot_decay_at = next_decay_at, sup_decay_at = next_decay_at,
     top_decay_count = COALESCE(decay_count,0), jug_decay_count = COALESCE(decay_count,0),
     mid_decay_count = COALESCE(decay_count,0), bot_decay_count = COALESCE(decay_count,0),
     sup_decay_count = COALESCE(decay_count,0)
   WHERE next_decay_at IS NOT NULL OR COALESCE(decay_count,0) <> 0',
  'SELECT ''五个位置计时已有数据，跳过存量复制''');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 注：旧列 next_decay_at / decay_count 保留但不再使用，便于回退与排查。
