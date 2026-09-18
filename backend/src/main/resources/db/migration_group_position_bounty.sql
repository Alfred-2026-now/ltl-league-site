-- ============================================================
-- LTL 联赛 分组 + 位置身价 + 赏金币 迁移脚本
-- 功能：
--   1. 新建「登峰组」「涅槃组」两支队伍
--   2. players 表新增位置身价/激活状态/最高身价/赏金币字段
--   3. 初始化：5 位置身价 = 现值，max_value = 现值，激活全 0，赏金 0
--   4. 按当前身价降序，前 30 人挂登峰组，其余挂涅槃组
-- ============================================================

-- ---------- 1. 新建两个分组队伍 ----------
INSERT INTO `teams` (`state`, `season`, `name`, `p_coins`, `points`, `rank`, `logo_url`, `description`, `deleted`)
SELECT 'DENGFENG', 's2', '登峰组', 0, 0, 0, NULL, '登峰组（身价前30）', 0
WHERE NOT EXISTS (SELECT 1 FROM `teams` WHERE `name` = '登峰组' AND `deleted` = 0);

INSERT INTO `teams` (`state`, `season`, `name`, `p_coins`, `points`, `rank`, `logo_url`, `description`, `deleted`)
SELECT 'NIEPAN', 's2', '涅槃组', 0, 0, 0, NULL, '涅槃组（身价30名之后）', 0
WHERE NOT EXISTS (SELECT 1 FROM `teams` WHERE `name` = '涅槃组' AND `deleted` = 0);

-- ---------- 2. players 表新增字段 ----------
ALTER TABLE `players`
  ADD COLUMN `top_value` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上路身价' AFTER `value`,
  ADD COLUMN `jug_value` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '打野身价' AFTER `top_value`,
  ADD COLUMN `mid_value` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '中路身价' AFTER `jug_value`,
  ADD COLUMN `bot_value` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '下路身价' AFTER `mid_value`,
  ADD COLUMN `sup_value` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '辅助身价' AFTER `bot_value`,
  ADD COLUMN `top_active` TINYINT NOT NULL DEFAULT 0 COMMENT '上路身价是否激活(0未激活1已激活)' AFTER `sup_value`,
  ADD COLUMN `jug_active` TINYINT NOT NULL DEFAULT 0 COMMENT '打野身价是否激活' AFTER `top_active`,
  ADD COLUMN `mid_active` TINYINT NOT NULL DEFAULT 0 COMMENT '中路身价是否激活' AFTER `jug_active`,
  ADD COLUMN `bot_active` TINYINT NOT NULL DEFAULT 0 COMMENT '下路身价是否激活' AFTER `mid_active`,
  ADD COLUMN `sup_active` TINYINT NOT NULL DEFAULT 0 COMMENT '辅助身价是否激活' AFTER `bot_active`,
  ADD COLUMN `max_value` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最高身价(五个位置身价最大值)' AFTER `sup_active`,
  ADD COLUMN `bounty` INT NOT NULL DEFAULT 0 COMMENT '赏金币' AFTER `deposit`;

-- ---------- 3. 初始化位置身价与最高身价 ----------
UPDATE `players`
SET `top_value` = `value`,
    `jug_value` = `value`,
    `mid_value` = `value`,
    `bot_value` = `value`,
    `sup_value` = `value`,
    `max_value` = `value`
WHERE `deleted` = 0;

-- ---------- 4. 按身价降序分组（前 30 登峰，其余涅槃） ----------
-- 4a. 先把所有在职选手清空队伍（回到自由人）
UPDATE `players` SET `team_id` = NULL WHERE `deleted` = 0;

-- 4b. 前 30 名（按身价降序）挂登峰组
SET @dengfeng_id = (SELECT `id` FROM `teams` WHERE `name` = '登峰组' AND `deleted` = 0 LIMIT 1);

UPDATE `players` p
JOIN (
  SELECT `id`
  FROM `players`
  WHERE `deleted` = 0
  ORDER BY `value` DESC, `id` ASC
  LIMIT 30
) top30 ON p.`id` = top30.`id`
SET p.`team_id` = @dengfeng_id;

-- 4c. 其余在职选手挂涅槃组
SET @niepan_id = (SELECT `id` FROM `teams` WHERE `name` = '涅槃组' AND `deleted` = 0 LIMIT 1);

UPDATE `players`
SET `team_id` = @niepan_id
WHERE `deleted` = 0 AND `team_id` IS NULL;
