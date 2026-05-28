-- 为比赛发布功能扩展 matches 表（直接改原表，执行一次即可）
-- 说明：
-- 1) 增加赛程发布字段（schedule_published*）
-- 2) 预留赛果发布标记（result_published），用于后续赛果录入模块限制编辑/撤回
-- 3) 增加同赛季/轮次/主客队组合唯一约束，避免重复创建

ALTER TABLE matches
  ADD COLUMN schedule_published TINYINT NOT NULL DEFAULT 0 COMMENT '赛程是否发布（0:未发布 1:已发布）' AFTER away_team_id,
  ADD COLUMN schedule_published_at DATETIME NULL COMMENT '赛程发布时间' AFTER schedule_published,
  ADD COLUMN schedule_unpublished_at DATETIME NULL COMMENT '赛程撤回时间' AFTER schedule_published_at,
  ADD COLUMN result_published TINYINT NOT NULL DEFAULT 0 COMMENT '赛果是否已发布（预留，0:未发布 1:已发布）' AFTER schedule_unpublished_at;

ALTER TABLE matches
  ADD UNIQUE KEY uk_season_round_teams (season, round, home_team_id, away_team_id, deleted);

-- 存量数据回填（避免上线过滤后前台赛程为空）
UPDATE matches
SET schedule_published = 1,
    schedule_published_at = COALESCE(updated_at, NOW())
WHERE deleted = 0;

