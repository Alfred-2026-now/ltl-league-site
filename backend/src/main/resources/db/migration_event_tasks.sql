-- 赛事任务系统数据库迁移
-- 执行前请备份数据库；本脚本只执行一次。
ALTER TABLE `player_deposit_ledger`
  ADD COLUMN `ref_table` VARCHAR(50) NULL COMMENT '关联业务表' AFTER `source`,
  ADD COLUMN `ref_id` BIGINT UNSIGNED NULL COMMENT '关联业务记录ID' AFTER `ref_table`,
  ADD KEY `idx_player_deposit_ref` (`ref_table`, `ref_id`),
  ADD UNIQUE KEY `uk_player_deposit_business_flow`
    (`player_id`, `type`, `ref_table`, `ref_id`, `is_voided`);

CREATE TABLE `event_tasks` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `season` VARCHAR(20) NOT NULL,
  `publisher_player_id` BIGINT UNSIGNED NOT NULL,
  `publisher_name_snapshot` VARCHAR(50) NOT NULL,
  `official` TINYINT NOT NULL DEFAULT 0,
  `title` VARCHAR(200) NOT NULL,
  `requirements` TEXT NOT NULL,
  `p_reward` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '每位完成者P币奖励',
  `bounty_reward` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '每位完成者赏金积分',
  `budget_note` VARCHAR(500) NULL COMMENT '只作提示，不参与计算',
  `claim_fee` INT UNSIGNED NULL,
  `max_claimants` INT UNSIGNED NULL,
  `claimed_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `completed_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `escrow_total` INT UNSIGNED NOT NULL DEFAULT 0,
  `escrow_remaining` INT UNSIGNED NOT NULL DEFAULT 0,
  `status` VARCHAR(30) NOT NULL,
  `latest_review_comment` VARCHAR(500) NULL,
  `reviewed_by_player_id` BIGINT UNSIGNED NULL,
  `reviewed_at` DATETIME NULL,
  `published_at` DATETIME NULL,
  `closed_at` DATETIME NULL,
  `close_reason` VARCHAR(500) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_event_task_public` (`season`, `status`, `published_at`),
  KEY `idx_event_task_publisher` (`publisher_player_id`, `created_at`),
  CONSTRAINT `fk_event_task_publisher` FOREIGN KEY (`publisher_player_id`) REFERENCES `players` (`id`),
  CONSTRAINT `fk_event_task_reviewer` FOREIGN KEY (`reviewed_by_player_id`) REFERENCES `players` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赛事任务';

CREATE TABLE `event_task_reviews` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT UNSIGNED NOT NULL,
  `action` VARCHAR(30) NOT NULL,
  `operator_player_id` BIGINT UNSIGNED NOT NULL,
  `comment` VARCHAR(500) NULL,
  `claim_fee_snapshot` INT UNSIGNED NULL,
  `max_claimants_snapshot` INT UNSIGNED NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_event_task_review_task` (`task_id`, `created_at`),
  CONSTRAINT `fk_event_task_review_task` FOREIGN KEY (`task_id`) REFERENCES `event_tasks` (`id`),
  CONSTRAINT `fk_event_task_review_operator` FOREIGN KEY (`operator_player_id`) REFERENCES `players` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务发布审核历史';

CREATE TABLE `event_task_claims` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT UNSIGNED NOT NULL,
  `player_id` BIGINT UNSIGNED NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `fee_amount` INT UNSIGNED NOT NULL DEFAULT 0,
  `p_reward_snapshot` INT UNSIGNED NOT NULL DEFAULT 0,
  `bounty_reward_snapshot` INT UNSIGNED NOT NULL DEFAULT 0,
  `claimed_at` DATETIME NOT NULL,
  `proof_submitted_at` DATETIME NULL,
  `abandoned_at` DATETIME NULL,
  `abandon_refunded` TINYINT NOT NULL DEFAULT 0,
  `completed_at` DATETIME NULL,
  `terminated_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_task_claim_player` (`task_id`, `player_id`),
  KEY `idx_event_task_claim_status` (`task_id`, `status`),
  KEY `idx_event_task_claim_player_time` (`player_id`, `created_at`),
  CONSTRAINT `fk_event_task_claim_task` FOREIGN KEY (`task_id`) REFERENCES `event_tasks` (`id`),
  CONSTRAINT `fk_event_task_claim_player` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务接取记录';

CREATE TABLE `event_task_proofs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT UNSIGNED NOT NULL,
  `claim_id` BIGINT UNSIGNED NOT NULL,
  `player_id` BIGINT UNSIGNED NOT NULL,
  `attempt_no` INT UNSIGNED NOT NULL,
  `description` TEXT NULL,
  `status` VARCHAR(30) NOT NULL,
  `review_comment` VARCHAR(500) NULL,
  `reviewed_by_player_id` BIGINT UNSIGNED NULL,
  `reviewed_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_task_proof_attempt` (`claim_id`, `attempt_no`),
  KEY `idx_event_task_proof_review` (`status`, `created_at`),
  CONSTRAINT `fk_event_task_proof_task` FOREIGN KEY (`task_id`) REFERENCES `event_tasks` (`id`),
  CONSTRAINT `fk_event_task_proof_claim` FOREIGN KEY (`claim_id`) REFERENCES `event_task_claims` (`id`),
  CONSTRAINT `fk_event_task_proof_player` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`),
  CONSTRAINT `fk_event_task_proof_reviewer` FOREIGN KEY (`reviewed_by_player_id`) REFERENCES `players` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务完成证明';

CREATE TABLE `event_task_proof_images` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `proof_id` BIGINT UNSIGNED NOT NULL,
  `task_id` BIGINT UNSIGNED NOT NULL,
  `claim_id` BIGINT UNSIGNED NOT NULL,
  `label` VARCHAR(200) NOT NULL,
  `url` VARCHAR(500) NOT NULL,
  `file_path` VARCHAR(500) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_event_task_proof_image` (`proof_id`),
  CONSTRAINT `fk_event_task_proof_image_proof` FOREIGN KEY (`proof_id`) REFERENCES `event_task_proofs` (`id`),
  CONSTRAINT `fk_event_task_proof_image_task` FOREIGN KEY (`task_id`) REFERENCES `event_tasks` (`id`),
  CONSTRAINT `fk_event_task_proof_image_claim` FOREIGN KEY (`claim_id`) REFERENCES `event_task_claims` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务证明截图';

CREATE TABLE `player_bounty_ledger` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `player_id` BIGINT UNSIGNED NOT NULL,
  `season` VARCHAR(20) NOT NULL,
  `task_id` BIGINT UNSIGNED NULL,
  `claim_id` BIGINT UNSIGNED NULL,
  `proof_id` BIGINT UNSIGNED NULL,
  `type` VARCHAR(30) NOT NULL,
  `amount` INT NOT NULL,
  `reason` VARCHAR(500) NULL,
  `balance_before` INT NOT NULL,
  `balance_after` INT NOT NULL,
  `operator` VARCHAR(50) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bounty_task_reward` (`claim_id`, `type`),
  KEY `idx_bounty_rank` (`season`, `player_id`, `created_at`),
  CONSTRAINT `fk_bounty_ledger_player` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`),
  CONSTRAINT `fk_bounty_ledger_task` FOREIGN KEY (`task_id`) REFERENCES `event_tasks` (`id`),
  CONSTRAINT `fk_bounty_ledger_claim` FOREIGN KEY (`claim_id`) REFERENCES `event_task_claims` (`id`),
  CONSTRAINT `fk_bounty_ledger_proof` FOREIGN KEY (`proof_id`) REFERENCES `event_task_proofs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赏金积分流水';
