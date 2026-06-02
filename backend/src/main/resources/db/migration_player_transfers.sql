USE ltl_league;

CREATE TABLE IF NOT EXISTS `player_transfers` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '转赠ID',
  `donor_player_id` BIGINT UNSIGNED NOT NULL COMMENT '赠与人选手ID',
  `recipient_type` VARCHAR(20) NOT NULL COMMENT '受赠类型：PLAYER/TEAM',
  `recipient_player_id` BIGINT UNSIGNED NULL COMMENT '受赠选手ID',
  `recipient_team_id` BIGINT UNSIGNED NULL COMMENT '受赠战队ID',
  `amount` INT UNSIGNED NOT NULL COMMENT '受赠方到账金额',
  `fee_amount` INT UNSIGNED NOT NULL COMMENT '手续费金额',
  `total_cost` INT UNSIGNED NOT NULL COMMENT '赠与人总扣款',
  `status` VARCHAR(20) NOT NULL DEFAULT 'completed' COMMENT '状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_donor_created_at` (`donor_player_id`, `created_at`),
  KEY `idx_recipient_player` (`recipient_player_id`),
  KEY `idx_recipient_team` (`recipient_team_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_transfer_donor_player` FOREIGN KEY (`donor_player_id`) REFERENCES `players` (`id`),
  CONSTRAINT `fk_transfer_recipient_player` FOREIGN KEY (`recipient_player_id`) REFERENCES `players` (`id`),
  CONSTRAINT `fk_transfer_recipient_team` FOREIGN KEY (`recipient_team_id`) REFERENCES `teams` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选手积分转赠记录表';
