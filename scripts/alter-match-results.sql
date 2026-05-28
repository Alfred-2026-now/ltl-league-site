-- 赛果录入：新增 match_results 表并扩展现有表（执行一次）

CREATE TABLE IF NOT EXISTS `match_results` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '赛果版本ID',
  `match_id` BIGINT UNSIGNED NOT NULL COMMENT '关联比赛ID',
  `version_no` INT UNSIGNED NOT NULL COMMENT '版本号，从1递增',
  `status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT 'draft/published/withdrawn',
  `result_type` VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT 'normal/forfeit',
  `home_score` TINYINT UNSIGNED NULL COMMENT '主队比分',
  `away_score` TINYINT UNSIGNED NULL COMMENT '客队比分',
  `winner_team_id` BIGINT UNSIGNED NULL COMMENT '胜方队伍ID，平局为空',
  `home_points` INT NULL COMMENT '主队本场积分',
  `away_points` INT NULL COMMENT '客队本场积分',
  `notes` TEXT NULL COMMENT '备注',
  `published_at` DATETIME NULL COMMENT '发布时间',
  `withdrawn_at` DATETIME NULL COMMENT '撤回时间',
  `withdraw_reason` VARCHAR(500) NULL COMMENT '撤回原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_version` (`match_id`, `version_no`),
  KEY `idx_match_status` (`match_id`, `status`),
  CONSTRAINT `fk_result_match` FOREIGN KEY (`match_id`) REFERENCES `matches` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='比赛赛果版本表';

ALTER TABLE matches
  ADD COLUMN home_points INT NULL COMMENT '主队本场积分' AFTER away_score,
  ADD COLUMN away_points INT NULL COMMENT '客队本场积分' AFTER home_points;

ALTER TABLE games
  ADD COLUMN result_id BIGINT UNSIGNED NULL COMMENT '关联赛果版本ID' AFTER match_id,
  ADD KEY idx_result_id (result_id);

ALTER TABLE attachments
  ADD COLUMN result_id BIGINT UNSIGNED NULL COMMENT '关联赛果版本ID' AFTER match_id,
  ADD COLUMN is_voided TINYINT NOT NULL DEFAULT 0 COMMENT '是否作废' AFTER note,
  ADD KEY idx_result_id (result_id);

-- 存量回填：已有小局/比分的比赛生成 published 赛果
INSERT INTO match_results (
  match_id, version_no, status, result_type,
  home_score, away_score, winner_team_id, home_points, away_points,
  notes, published_at
)
SELECT
  m.id,
  1,
  'published',
  CASE WHEN m.status = 'forfeit' THEN 'forfeit' ELSE 'normal' END,
  m.home_score,
  m.away_score,
  CASE
    WHEN m.home_score IS NOT NULL AND m.away_score IS NOT NULL AND m.home_score > m.away_score THEN m.home_team_id
    WHEN m.home_score IS NOT NULL AND m.away_score IS NOT NULL AND m.away_score > m.home_score THEN m.away_team_id
    ELSE NULL
  END,
  m.home_points,
  m.away_points,
  m.notes,
  COALESCE(m.updated_at, NOW())
FROM matches m
WHERE m.deleted = 0
  AND (m.home_score IS NOT NULL OR EXISTS (SELECT 1 FROM games g WHERE g.match_id = m.id AND g.deleted = 0))
  AND NOT EXISTS (SELECT 1 FROM match_results mr WHERE mr.match_id = m.id AND mr.deleted = 0);

UPDATE games g
INNER JOIN match_results mr ON mr.match_id = g.match_id AND mr.status = 'published' AND mr.deleted = 0
SET g.result_id = mr.id
WHERE g.deleted = 0 AND g.result_id IS NULL;

UPDATE attachments a
INNER JOIN match_results mr ON mr.match_id = a.match_id AND mr.status = 'published' AND mr.deleted = 0
SET a.result_id = mr.id
WHERE a.deleted = 0 AND a.result_id IS NULL;

UPDATE matches m
INNER JOIN match_results mr ON mr.match_id = m.id AND mr.status = 'published' AND mr.deleted = 0
SET m.result_published = 1
WHERE m.deleted = 0 AND (m.result_published IS NULL OR m.result_published = 0);
