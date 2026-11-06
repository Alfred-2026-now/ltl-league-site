-- 允许负身价。升级已有库时，备份后在发布新版后端前执行。
-- 前置：已执行位置身价、租借输入、加权出场身价等既有迁移。
-- 仅修改字段类型，不改写现有数值；MySQL DDL 会隐式提交。
ALTER TABLE `players`
  MODIFY COLUMN `value` INT NOT NULL DEFAULT 2000 COMMENT '选手身价',
  MODIFY COLUMN `top_value` INT NOT NULL DEFAULT 0 COMMENT '上路身价',
  MODIFY COLUMN `jug_value` INT NOT NULL DEFAULT 0 COMMENT '打野身价',
  MODIFY COLUMN `mid_value` INT NOT NULL DEFAULT 0 COMMENT '中路身价',
  MODIFY COLUMN `bot_value` INT NOT NULL DEFAULT 0 COMMENT '下路身价',
  MODIFY COLUMN `sup_value` INT NOT NULL DEFAULT 0 COMMENT '辅助身价',
  MODIFY COLUMN `max_value` INT NOT NULL DEFAULT 0 COMMENT '最高身价(五个位置身价最大值)';

ALTER TABLE `valuation_changes`
  MODIFY COLUMN `before_value` INT NOT NULL COMMENT '赛前身价',
  MODIFY COLUMN `after_value` INT NOT NULL COMMENT '赛后身价';

ALTER TABLE `match_results`
  MODIFY COLUMN `home_line_value` DECIMAL(12,2) NULL COMMENT '主队按出场局数加权的总身价',
  MODIFY COLUMN `away_line_value` DECIMAL(12,2) NULL COMMENT '客队按出场局数加权的总身价';

ALTER TABLE `match_result_loan_inputs`
  MODIFY COLUMN `player_value` INT NOT NULL COMMENT '结算身价';

ALTER TABLE `match_result_lineup_inputs`
  MODIFY COLUMN `player_value` DECIMAL(12,2) NOT NULL COMMENT '保存草稿时的选手身价快照';
