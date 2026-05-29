-- LTL League 选手存款流水表
-- 用于记录选手P币存款的变动流水

USE ltl_league;

-- 创建选手存款流水表
CREATE TABLE IF NOT EXISTS `player_deposit_ledger` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `player_id` BIGINT UNSIGNED NOT NULL COMMENT '选手ID',
  `match_id` BIGINT UNSIGNED NULL COMMENT '关联比赛ID（如果有）',
  `result_id` BIGINT UNSIGNED NULL COMMENT '关联赛果版本ID（如果有）',
  `type` VARCHAR(30) NOT NULL COMMENT '流水类型（loan_fee/manual_adjustment/race_reward/etc）',
  `amount` INT NOT NULL COMMENT '金额（正负表示增减）',
  `reason` VARCHAR(500) NULL COMMENT '原因说明',
  `balance_before` INT NULL COMMENT '变动前余额',
  `balance_after` INT NULL COMMENT '变动后余额',
  `source` VARCHAR(30) NOT NULL DEFAULT 'manual_admin' COMMENT '来源（match_result/manual_admin）',
  `operator` VARCHAR(50) NULL COMMENT '操作人',
  `is_voided` TINYINT NOT NULL DEFAULT 0 COMMENT '是否作废',
  `voided_at` DATETIME NULL COMMENT '作废时间',
  `void_reason` VARCHAR(500) NULL COMMENT '作废原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_player_id` (`player_id`),
  KEY `idx_match_id` (`match_id`),
  KEY `idx_result_id` (`result_id`),
  KEY `idx_type` (`type`),
  KEY `idx_source` (`source`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `fk_deposit_ledger_player` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`),
  CONSTRAINT `fk_deposit_ledger_match` FOREIGN KEY (`match_id`) REFERENCES `matches` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选手存款流水表';

-- 执行完成
-- 验证：SHOW CREATE TABLE player_deposit_ledger;
