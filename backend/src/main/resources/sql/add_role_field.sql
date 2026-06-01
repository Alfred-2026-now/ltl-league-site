-- 添加 role 字段到 players 表
-- 用于区分用户角色：0=普通用户，1=管理员

ALTER TABLE players ADD COLUMN role INT DEFAULT 0 COMMENT '用户角色：0=普通用户，1=管理员' AFTER deposit;

-- 更新默认管理员（天下人、陶吉吉、大橙子）
UPDATE players SET role = 1 WHERE name IN ('天下人', '陶吉吉', '大橙子');
