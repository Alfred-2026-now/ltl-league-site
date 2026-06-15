USE ltl_league;

ALTER TABLE `match_results`
  ADD COLUMN `tax_exempt` TINYINT NOT NULL DEFAULT 0 COMMENT '是否免奢侈税' AFTER `notes`,
  ADD COLUMN `home_line_value` INT UNSIGNED NULL COMMENT '主队本场出场总身价' AFTER `tax_exempt`,
  ADD COLUMN `away_line_value` INT UNSIGNED NULL COMMENT '客队本场出场总身价' AFTER `home_line_value`,
  ADD COLUMN `home_roster_size` INT UNSIGNED NULL COMMENT '主队结算名单人数' AFTER `away_line_value`,
  ADD COLUMN `away_roster_size` INT UNSIGNED NULL COMMENT '客队结算名单人数' AFTER `home_roster_size`;

ALTER TABLE `p_ledger`
  ADD COLUMN `result_id` BIGINT UNSIGNED NULL COMMENT '关联赛果版本ID' AFTER `match_id`,
  ADD COLUMN `source` VARCHAR(30) NOT NULL DEFAULT 'match_result' COMMENT '来源（match_result/manual_admin）' AFTER `version`,
  ADD COLUMN `balance_before` INT NULL COMMENT '变动前余额' AFTER `source`,
  ADD COLUMN `balance_after` INT NULL COMMENT '变动后余额' AFTER `balance_before`,
  ADD KEY `idx_result_id` (`result_id`);

ALTER TABLE `valuation_changes`
  MODIFY COLUMN `match_id` BIGINT UNSIGNED NULL COMMENT '关联比赛ID',
  ADD COLUMN `result_id` BIGINT UNSIGNED NULL COMMENT '关联赛果版本ID' AFTER `match_id`,
  ADD COLUMN `source` VARCHAR(30) NOT NULL DEFAULT 'match_result' COMMENT '来源（match_result/manual_adjustment）' AFTER `version`,
  ADD COLUMN `operator` VARCHAR(50) NULL COMMENT '操作人' AFTER `source`,
  ADD KEY `idx_result_id` (`result_id`);

CREATE TABLE IF NOT EXISTS `settlement_reward_rules` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  `format` VARCHAR(10) NOT NULL COMMENT '赛制（BO1/BO2/BO3/BO5）',
  `score_pattern` VARCHAR(10) NOT NULL COMMENT '比分模式（如2:0/1:1）',
  `winner_amount` INT NULL COMMENT '胜方奖励',
  `loser_amount` INT NULL COMMENT '败方奖励',
  `draw_amount` INT NULL COMMENT '平局双方奖励',
  `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reward_rule` (`format`, `score_pattern`, `deleted`),
  KEY `idx_format` (`format`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赛果奖励规则表';

CREATE TABLE IF NOT EXISTS `match_result_loan_inputs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '输入ID',
  `result_id` BIGINT UNSIGNED NOT NULL COMMENT '关联赛果版本ID',
  `match_id` BIGINT UNSIGNED NOT NULL COMMENT '关联比赛ID',
  `paying_team_id` BIGINT UNSIGNED NOT NULL COMMENT '使用租借选手队伍ID',
  `player_id` BIGINT UNSIGNED NOT NULL COMMENT '租借选手ID',
  `replaced_player_id` BIGINT UNSIGNED NULL COMMENT '被替换选手ID',
  `player_value` INT UNSIGNED NOT NULL COMMENT '结算身价',
  `source_type` VARCHAR(20) NOT NULL COMMENT '来源类型（original_team/free_agent）',
  `source_team_id` BIGINT UNSIGNED NULL COMMENT '原队伍ID',
  `reason` VARCHAR(500) NULL COMMENT '原因说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_result_id` (`result_id`),
  KEY `idx_match_id` (`match_id`),
  KEY `idx_player_id` (`player_id`),
  KEY `idx_replaced_player_id` (`replaced_player_id`),
  CONSTRAINT `fk_loan_input_result` FOREIGN KEY (`result_id`) REFERENCES `match_results` (`id`),
  CONSTRAINT `fk_loan_input_match` FOREIGN KEY (`match_id`) REFERENCES `matches` (`id`),
  CONSTRAINT `fk_loan_input_player` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`),
  CONSTRAINT `fk_loan_input_replaced_player` FOREIGN KEY (`replaced_player_id`) REFERENCES `players` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赛果租借费输入表';

CREATE TABLE IF NOT EXISTS `match_result_valuation_inputs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '输入ID',
  `result_id` BIGINT UNSIGNED NOT NULL COMMENT '关联赛果版本ID',
  `match_id` BIGINT UNSIGNED NOT NULL COMMENT '关联比赛ID',
  `player_id` BIGINT UNSIGNED NOT NULL COMMENT '选手ID',
  `objective_delta` INT NOT NULL DEFAULT 0 COMMENT '客观变化',
  `subjective_delta` INT NOT NULL DEFAULT 0 COMMENT '主观变化',
  `subjective_reason` VARCHAR(500) NULL COMMENT '调整原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_result_id` (`result_id`),
  KEY `idx_match_id` (`match_id`),
  KEY `idx_player_id` (`player_id`),
  CONSTRAINT `fk_valuation_input_result` FOREIGN KEY (`result_id`) REFERENCES `match_results` (`id`),
  CONSTRAINT `fk_valuation_input_match` FOREIGN KEY (`match_id`) REFERENCES `matches` (`id`),
  CONSTRAINT `fk_valuation_input_player` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赛果身价调整输入表';
