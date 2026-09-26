-- 已有赛事任务数据：新增管理员控制的置顶标记，现有任务默认不置顶。
-- 生产环境发布新后端前执行一次。
ALTER TABLE `event_tasks`
  ADD COLUMN `pinned` TINYINT NOT NULL DEFAULT 0 COMMENT '是否在公开任务列表置顶' AFTER `official`;
