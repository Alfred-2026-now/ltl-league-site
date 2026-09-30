-- 比赛竞猜系统数据库迁移
-- 执行前请备份数据库；本脚本只执行一次。
ALTER TABLE `player_bounty_ledger`
  ADD COLUMN `ref_table` VARCHAR(50) NULL COMMENT '关联业务表' AFTER `operator`,
  ADD COLUMN `ref_id` BIGINT UNSIGNED NULL COMMENT '关联业务记录ID' AFTER `ref_table`,
  ADD KEY `idx_player_bounty_ref` (`ref_table`, `ref_id`);

CREATE TABLE `match_predictions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `season` VARCHAR(20) NOT NULL,
  `match_id` BIGINT UNSIGNED NULL COMMENT '关联比赛，NULL 表示自由竞猜',
  `title` VARCHAR(200) NOT NULL,
  `description` VARCHAR(1000) NULL,
  `reward_p_total` INT UNSIGNED NOT NULL COMMENT '总奖励P币',
  `reward_bounty_total` INT UNSIGNED NOT NULL COMMENT '总奖励赏金积分',
  `deadline_at` DATETIME NOT NULL COMMENT '截止竞猜时间',
  `status` VARCHAR(30) NOT NULL COMMENT 'PUBLISHED/SETTLED/CANCELLED',
  `correct_option_id` BIGINT UNSIGNED NULL,
  `winner_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `reward_p_per_winner` INT UNSIGNED NOT NULL DEFAULT 0,
  `reward_bounty_per_winner` INT UNSIGNED NOT NULL DEFAULT 0,
  `settled_at` DATETIME NULL,
  `settled_by_player_id` BIGINT UNSIGNED NULL,
  `cancelled_at` DATETIME NULL,
  `cancel_reason` VARCHAR(500) NULL,
  `created_by_player_id` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_match_prediction_public` (`season`, `status`, `created_at`),
  KEY `idx_match_prediction_match` (`match_id`),
  CONSTRAINT `fk_match_prediction_match` FOREIGN KEY (`match_id`) REFERENCES `matches` (`id`),
  CONSTRAINT `fk_match_prediction_creator` FOREIGN KEY (`created_by_player_id`) REFERENCES `players` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='比赛竞猜';

CREATE TABLE `match_prediction_options` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `prediction_id` BIGINT UNSIGNED NOT NULL,
  `label` VARCHAR(100) NOT NULL,
  `sort_order` INT UNSIGNED NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_match_prediction_option_pred` (`prediction_id`, `sort_order`),
  CONSTRAINT `fk_match_prediction_option_pred` FOREIGN KEY (`prediction_id`) REFERENCES `match_predictions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='竞猜选项';

CREATE TABLE `match_prediction_bets` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `prediction_id` BIGINT UNSIGNED NOT NULL,
  `option_id` BIGINT UNSIGNED NOT NULL,
  `player_id` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_prediction_bet_player` (`prediction_id`, `player_id`),
  KEY `idx_match_prediction_bet_option` (`prediction_id`, `option_id`),
  CONSTRAINT `fk_match_prediction_bet_pred` FOREIGN KEY (`prediction_id`) REFERENCES `match_predictions` (`id`),
  CONSTRAINT `fk_match_prediction_bet_option` FOREIGN KEY (`option_id`) REFERENCES `match_prediction_options` (`id`),
  CONSTRAINT `fk_match_prediction_bet_player` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='竞猜投注';
