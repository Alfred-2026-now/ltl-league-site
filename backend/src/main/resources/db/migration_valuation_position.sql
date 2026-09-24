-- ============================================================
-- 身价变化表增加 position 字段
-- 用于「身价管理」手动调整时记录调整的是哪个位置（TOP/JUG/MID/BOT/SUP）
-- NULL 表示旧的总身价调整（兼容历史数据与赛果结算）
-- ============================================================

ALTER TABLE `valuation_changes`
  ADD COLUMN `position` VARCHAR(10) NULL COMMENT '调整的位置（TOP/JUG/MID/BOT/SUP，NULL表示总身价）' AFTER `player_id`;
