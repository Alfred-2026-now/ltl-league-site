-- 未删除的选手名称全站唯一（含自由人），逻辑删除后可用原名称重新注册。
-- 升级已有库时，备份后先检查同名档案：
-- SELECT name, COUNT(*) FROM players WHERE deleted = 0 GROUP BY name HAVING COUNT(*) > 1;
-- 如有结果，需先确认并处理同名档案。迁移遇到重名会失败，原约束仍然生效。
-- 只调整唯一约束，保留选手档案及历史关联。新版建表脚本已包含该约束。
SET @has_active_name_index = EXISTS (
  SELECT 1 FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'players' AND INDEX_NAME = 'uk_active_player_name'
);

SET @sql = IF(@has_active_name_index, 'SELECT 1',
  'ALTER TABLE `players`
     ADD COLUMN `active_name_key` TINYINT GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN 1 ELSE NULL END) VIRTUAL COMMENT ''仅未删除选手参与全站名称唯一校验'',
     DROP INDEX `uk_team_name`,
     ADD UNIQUE KEY `uk_active_player_name` (`name`, `active_name_key`)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
