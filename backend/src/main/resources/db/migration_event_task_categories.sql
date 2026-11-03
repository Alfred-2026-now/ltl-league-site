-- ============================================================
-- LTL · 赛事任务分类
-- 1) 新增分类表 event_task_categories（管理员在后台自行维护）
-- 2) event_tasks 增加 category_id（NULL = 未分类）
-- 幂等：可重复执行
-- 执行：mysql -h <host> -u <user> -p ltl_league < migration_event_task_categories.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS `event_task_categories` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '展示顺序，小的在前',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赛事任务分类';

-- 已有 event_tasks 表则补列（幂等）
SET @has_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event_tasks' AND COLUMN_NAME = 'category_id');
SET @sql = IF(@has_col = 0,
  'ALTER TABLE `event_tasks` ADD COLUMN `category_id` BIGINT UNSIGNED NULL COMMENT ''任务分类，NULL=未分类'' AFTER `pinned`, ADD KEY `idx_event_task_category` (`category_id`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
