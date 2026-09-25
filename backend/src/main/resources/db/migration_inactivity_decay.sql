-- ============================================================
-- LTL 联赛 · 未参赛身价衰减
-- 幂等：字段/索引已存在则跳过
-- 执行：mysql -h <host> -u <user> -p ltl_league < migration_inactivity_decay.sql
-- ============================================================

-- ---------- 1. players：衰减计时字段 ----------
SET @has_next_decay := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'players' AND COLUMN_NAME = 'next_decay_at'
);
SET @sql := IF(@has_next_decay = 0,
  'ALTER TABLE `players` ADD COLUMN `next_decay_at` DATETIME NULL COMMENT ''下次身价衰减日期（未参赛衰减）；NULL 表示尚未启动计时''',
  'SELECT ''players.next_decay_at 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_decay_cnt := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'players' AND COLUMN_NAME = 'decay_count'
);
SET @sql := IF(@has_decay_cnt = 0,
  'ALTER TABLE `players` ADD COLUMN `decay_count` INT NOT NULL DEFAULT 0 COMMENT ''已发生的衰减次数''',
  'SELECT ''players.decay_count 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_decay_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'players' AND INDEX_NAME = 'idx_player_next_decay'
);
SET @sql := IF(@has_decay_idx = 0,
  'ALTER TABLE `players` ADD KEY `idx_player_next_decay` (`next_decay_at`)',
  'SELECT ''idx_player_next_decay 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. valuation_changes：撤回时恢复衰减进程所需快照 ----------
SET @has_before_nda := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'valuation_changes' AND COLUMN_NAME = 'before_next_decay_at'
);
SET @sql := IF(@has_before_nda = 0,
  'ALTER TABLE `valuation_changes` ADD COLUMN `before_next_decay_at` DATETIME NULL COMMENT ''改动前的下次衰减日期（撤回时恢复）''',
  'SELECT ''valuation_changes.before_next_decay_at 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_before_dc := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'valuation_changes' AND COLUMN_NAME = 'before_decay_count'
);
SET @sql := IF(@has_before_dc = 0,
  'ALTER TABLE `valuation_changes` ADD COLUMN `before_decay_count` INT NULL COMMENT ''改动前的已衰减次数（撤回时恢复）''',
  'SELECT ''valuation_changes.before_decay_count 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
