-- 添加 password 字段到 players 表
ALTER TABLE players ADD COLUMN password VARCHAR(255) DEFAULT NULL COMMENT '登录密码（加密存储）' AFTER role;

-- 初始化所有选手的默认密码：123456（使用 MD5 加密）
UPDATE players SET password = 'e10adc3949ba59abbe56e057f20f883e' WHERE password IS NULL;

-- 注意：e10adc3949ba59abbe56e057f20f883e 是 "123456" 的 MD5 值
-- 用户首次登录后可在个人中心修改密码
