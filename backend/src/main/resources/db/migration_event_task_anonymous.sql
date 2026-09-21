-- 赛事任务匿名发布增量迁移
-- 前置条件：已执行 migration_event_tasks.sql。执行前请备份数据库；本脚本只执行一次。

ALTER TABLE `event_tasks`
  ADD COLUMN `anonymous` TINYINT NOT NULL DEFAULT 0 COMMENT '是否匿名公开' AFTER `official`,
  ADD COLUMN `anonymous_fee_rate_snapshot` INT UNSIGNED NULL COMMENT '审核发布时匿名费率百分比快照' AFTER `budget_note`,
  ADD COLUMN `anonymous_fee_amount` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已收取匿名发布费' AFTER `anonymous_fee_rate_snapshot`;

INSERT IGNORE INTO `rule_parameters`
  (`param_key`, `group_key`, `group_name`, `name`, `description`, `value_type`, `value_text`, `unit`, `sort_order`, `is_active`)
VALUES
  ('event_task.anonymous_fee_rate', 'event_task', '赛事任务', '匿名发布费率',
   '匿名发布费取50P与（每人P币奖励×最大接取人数×本费率）中的较高值，百分比结果向上取整。',
   'int', '10', '%', 700, 1);
