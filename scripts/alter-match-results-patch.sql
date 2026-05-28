ALTER TABLE games
  ADD COLUMN result_id BIGINT UNSIGNED NULL COMMENT '关联赛果版本ID' AFTER match_id,
  ADD KEY idx_result_id (result_id);

ALTER TABLE attachments
  ADD COLUMN result_id BIGINT UNSIGNED NULL COMMENT '关联赛果版本ID' AFTER match_id,
  ADD COLUMN is_voided TINYINT NOT NULL DEFAULT 0 COMMENT '是否作废' AFTER note,
  ADD KEY idx_result_id_attach (result_id);
