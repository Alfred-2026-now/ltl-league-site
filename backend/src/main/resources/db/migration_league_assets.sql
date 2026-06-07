-- 联盟总资产流水
-- 执行方式: mysql -h 123.57.19.160 -u ltl_user -p ltl_league < migration_league_assets.sql

CREATE TABLE IF NOT EXISTS `league_asset_ledger` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '联盟资产流水ID',
  `type` VARCHAR(50) NOT NULL COMMENT '类型：league_reclaim/manual_income/welfare_expense/reversal等',
  `amount` INT NOT NULL COMMENT '金额，正数表示联盟资产增加，负数表示减少',
  `reason` VARCHAR(500) NULL COMMENT '原因说明',
  `source` VARCHAR(50) NOT NULL DEFAULT 'system' COMMENT '来源',
  `ref_table` VARCHAR(50) NULL COMMENT '来源表',
  `ref_id` BIGINT UNSIGNED NULL COMMENT '来源记录ID',
  `match_id` BIGINT UNSIGNED NULL COMMENT '关联比赛ID',
  `result_id` BIGINT UNSIGNED NULL COMMENT '关联赛果版本ID',
  `operator` VARCHAR(50) NULL COMMENT '操作人',
  `balance_before` INT NOT NULL DEFAULT 0 COMMENT '变动前联盟资产',
  `balance_after` INT NOT NULL DEFAULT 0 COMMENT '变动后联盟资产',
  `is_voided` TINYINT NOT NULL DEFAULT 0 COMMENT '是否作废',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_type` (`type`),
  KEY `idx_source` (`source`),
  KEY `idx_ref` (`ref_table`, `ref_id`),
  KEY `idx_match_result` (`match_id`, `result_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='联盟总资产流水表';

SET @league_asset_seed := (
  SELECT
    COALESCE((
      SELECT SUM(-amount)
      FROM p_ledger
      WHERE type = 'luxury_tax' AND amount < 0 AND is_voided = 0 AND deleted = 0
    ), 0)
    + COALESCE((
      SELECT -(
        COALESCE((SELECT SUM(amount) FROM p_ledger WHERE type = 'loan_fee' AND is_voided = 0 AND deleted = 0), 0)
        + COALESCE((SELECT SUM(amount) FROM player_deposit_ledger WHERE type = 'loan_fee' AND is_voided = 0 AND deleted = 0), 0)
      )
    ), 0)
    + COALESCE((
      SELECT SUM(fee_amount)
      FROM player_transfers
      WHERE status = 'completed' AND deleted = 0
    ), 0)
    + COALESCE((
      SELECT SUM(cost_points)
      FROM prize_exchanges
      WHERE status IN ('pending', 'completed') AND deleted = 0
    ), 0)
    + COALESCE((
      SELECT SUM(-amount)
      FROM p_ledger
      WHERE type IN ('salary_deduct', 'manual_admin', 'league_reclaim')
        AND amount < 0
        AND is_voided = 0
        AND deleted = 0
    ), 0)
);

INSERT INTO league_asset_ledger
  (`type`, `amount`, `reason`, `source`, `operator`, `balance_before`, `balance_after`, `is_voided`)
SELECT
  'history_seed',
  @league_asset_seed,
  '历史已上交/销毁P币初始化',
  'migration',
  'system',
  0,
  @league_asset_seed,
  0
WHERE @league_asset_seed > 0
  AND NOT EXISTS (
    SELECT 1 FROM league_asset_ledger WHERE source = 'migration' AND type = 'history_seed' AND deleted = 0
  );

SET @idx_latest_balance_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'league_asset_ledger'
    AND index_name = 'idx_latest_balance'
);

SET @create_latest_balance_index := IF(
  @idx_latest_balance_exists = 0,
  'ALTER TABLE league_asset_ledger ADD INDEX idx_latest_balance (`deleted`, `created_at`, `id`)',
  'SELECT 1'
);
PREPARE stmt FROM @create_latest_balance_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
