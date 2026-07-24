-- Replace roster-size luxury-tax multipliers with per-player game-weighted lineup values.
-- Execute before deploying the backend that reads match_result_lineup_inputs.

USE ltl_league;

ALTER TABLE match_results
  MODIFY COLUMN `home_line_value` DECIMAL(12,2) UNSIGNED NULL COMMENT '主队按出场局数加权的总身价',
  MODIFY COLUMN `away_line_value` DECIMAL(12,2) UNSIGNED NULL COMMENT '客队按出场局数加权的总身价';

CREATE TABLE IF NOT EXISTS `match_result_lineup_inputs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '出场输入ID',
  `result_id` BIGINT UNSIGNED NOT NULL COMMENT '关联赛果版本ID',
  `match_id` BIGINT UNSIGNED NOT NULL COMMENT '关联比赛ID',
  `team_id` BIGINT UNSIGNED NOT NULL COMMENT '本场使用该选手的队伍ID',
  `player_id` BIGINT UNSIGNED NOT NULL COMMENT '出场选手ID',
  `player_type` VARCHAR(20) NOT NULL COMMENT '选手类型（roster/loan）',
  `games_played` TINYINT UNSIGNED NOT NULL COMMENT '本场出场局数',
  `player_value` DECIMAL(12,2) UNSIGNED NOT NULL COMMENT '保存草稿时的选手身价快照',
  `advantage_tiers` VARCHAR(100) NULL COMMENT '身价差优势档位，逗号分隔',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_lineup_result_team` (`result_id`, `team_id`),
  KEY `idx_lineup_match_id` (`match_id`),
  KEY `idx_lineup_player_id` (`player_id`),
  CONSTRAINT `fk_lineup_input_result` FOREIGN KEY (`result_id`) REFERENCES `match_results` (`id`),
  CONSTRAINT `fk_lineup_input_match` FOREIGN KEY (`match_id`) REFERENCES `matches` (`id`),
  CONSTRAINT `fk_lineup_input_team` FOREIGN KEY (`team_id`) REFERENCES `teams` (`id`),
  CONSTRAINT `fk_lineup_input_player` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赛果选手出场局数输入表';

UPDATE rule_parameters
SET `is_active` = 0,
    `deleted` = 1,
    `updated_at` = CURRENT_TIMESTAMP
WHERE `param_key` IN (
  'luxury.roster_factor.le5',
  'luxury.roster_factor.eq6',
  'luxury.roster_factor.eq7',
  'luxury.roster_factor.eq8',
  'luxury.roster_factor.eq9',
  'luxury.roster_factor.ge10'
);
