-- LTL 联赛数据库建表脚本
-- 数据库: ltl_league

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS ltl_league DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ltl_league;

-- 1. 队伍表
CREATE TABLE IF NOT EXISTS `teams` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '队伍ID',
  `state` VARCHAR(20) NOT NULL COMMENT '国家简称（秦/楚/蜀/吴/越/燕）',
  `name` VARCHAR(50) NOT NULL COMMENT '队伍名称',
  `p_coins` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '队伍P币数量',
  `points` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '积分',
  `rank` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '排名',
  `logo_url` VARCHAR(255) NULL COMMENT '队徽图片URL',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_state` (`state`),
  KEY `idx_rank` (`rank`),
  KEY `idx_points` (`points`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='队伍表';

-- 2. 选手表
CREATE TABLE IF NOT EXISTS `players` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '选手ID',
  `team_id` BIGINT UNSIGNED NOT NULL COMMENT '所属队伍ID',
  `name` VARCHAR(50) NOT NULL COMMENT '选手名称',
  `value` INT UNSIGNED NOT NULL DEFAULT 2000 COMMENT '选手身价',
  `position` VARCHAR(10) NULL COMMENT '主力位置（TOP/JUG/MID/BOT/SUP）',
  `game_account` VARCHAR(100) NULL COMMENT '游戏账号',
  `puuid` VARCHAR(100) NULL COMMENT 'LOL PUUID',
  `is_substitute` TINYINT NOT NULL DEFAULT 0 COMMENT '是否替补',
  `is_loan` TINYINT NOT NULL DEFAULT 0 COMMENT '是否租借状态',
  `loan_team_id` BIGINT UNSIGNED NULL COMMENT '租借队伍ID',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1:在职 2:离队 3:自由人）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_name` (`team_id`, `name`),
  KEY `idx_team_id` (`team_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_player_team` FOREIGN KEY (`team_id`) REFERENCES `teams` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选手表';

-- 3. 比赛表
CREATE TABLE IF NOT EXISTS `matches` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '比赛ID',
  `match_id` VARCHAR(50) NOT NULL COMMENT '比赛唯一标识（如s1-r1-qin-yan）',
  `season` VARCHAR(20) NOT NULL COMMENT '赛季',
  `round` INT UNSIGNED NOT NULL COMMENT '轮次',
  `round_label` VARCHAR(20) NOT NULL COMMENT '轮次标签',
  `match_date` DATETIME NULL COMMENT '比赛时间',
  `format` VARCHAR(10) NOT NULL COMMENT '赛制（BO1/BO2/BO3）',
  `status` VARCHAR(20) NOT NULL DEFAULT 'scheduled' COMMENT '状态（scheduled/live/finished/forfeit/postponed/cancelled）',
  `home_team_id` BIGINT UNSIGNED NOT NULL COMMENT '主队ID',
  `away_team_id` BIGINT UNSIGNED NOT NULL COMMENT '客队ID',
  `home_score` TINYINT UNSIGNED NULL COMMENT '主队得分',
  `away_score` TINYINT UNSIGNED NULL COMMENT '客队得分',
  `forfeit_team_id` BIGINT UNSIGNED NULL COMMENT '弃赛队伍ID',
  `live_url` VARCHAR(255) NULL COMMENT '直播链接',
  `notes` TEXT NULL COMMENT '备注说明',
  `source` VARCHAR(20) NULL COMMENT '数据来源（manual_entry/lcu_api）',
  `version` VARCHAR(20) NULL COMMENT '结算版本号',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_id` (`match_id`, `version`),
  KEY `idx_season_round` (`season`, `round`),
  KEY `idx_status` (`status`),
  KEY `idx_date` (`match_date`),
  CONSTRAINT `fk_match_home_team` FOREIGN KEY (`home_team_id`) REFERENCES `teams` (`id`),
  CONSTRAINT `fk_match_away_team` FOREIGN KEY (`away_team_id`) REFERENCES `teams` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='比赛表';

-- 4. 小局表
CREATE TABLE IF NOT EXISTS `games` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '小局ID',
  `match_id` BIGINT UNSIGNED NOT NULL COMMENT '所属比赛ID',
  `game_index` TINYINT UNSIGNED NOT NULL COMMENT '小局序号',
  `winner` VARCHAR(20) NOT NULL COMMENT '胜方队伍简称',
  `blue_team` VARCHAR(20) NOT NULL COMMENT '蓝方队伍简称',
  `red_team` VARCHAR(20) NOT NULL COMMENT '红方队伍简称',
  `home_team` VARCHAR(20) NOT NULL COMMENT '主队简称',
  `away_team` VARCHAR(20) NOT NULL COMMENT '客队简称',
  `duration_seconds` INT UNSIGNED NULL COMMENT '比赛时长（秒）',
  `source_game_id` VARCHAR(100) NULL COMMENT 'LOL游戏ID',
  `game_version` VARCHAR(20) NULL COMMENT '游戏版本',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_game` (`match_id`, `game_index`),
  KEY `idx_match_id` (`match_id`),
  CONSTRAINT `fk_game_match` FOREIGN KEY (`match_id`) REFERENCES `matches` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小局表';

-- 5. 小局参与表
CREATE TABLE IF NOT EXISTS `game_participants` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `game_id` BIGINT UNSIGNED NOT NULL COMMENT '小局ID',
  `player_id` BIGINT UNSIGNED NOT NULL COMMENT '选手ID',
  `team_id` BIGINT UNSIGNED NOT NULL COMMENT '代表队伍ID',
  `source_team_id` BIGINT UNSIGNED NULL COMMENT '原队伍ID',
  `position` VARCHAR(10) NOT NULL COMMENT '位置（TOP/JUG/MID/BOT/SUP）',
  `champion` VARCHAR(50) NULL COMMENT '英雄名称',
  `is_loan` TINYINT NOT NULL DEFAULT 0 COMMENT '是否租借',
  `is_substitute` TINYINT NOT NULL DEFAULT 0 COMMENT '是否替补',
  `kills` INT UNSIGNED DEFAULT 0 COMMENT '击杀',
  `deaths` INT UNSIGNED DEFAULT 0 COMMENT '死亡',
  `assists` INT UNSIGNED DEFAULT 0 COMMENT '助攻',
  `cs` INT UNSIGNED DEFAULT 0 COMMENT '补刀',
  `gold_earned` INT UNSIGNED DEFAULT 0 COMMENT '获得经济',
  `damage_dealt` INT UNSIGNED DEFAULT 0 COMMENT '造成伤害',
  `damage_taken` INT UNSIGNED DEFAULT 0 COMMENT '承受伤害',
  `vision_score` INT UNSIGNED DEFAULT 0 COMMENT '视野分数',
  `kill_participation` DECIMAL(5,4) DEFAULT 0 COMMENT '参团率',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_game_player` (`game_id`, `player_id`),
  KEY `idx_game_id` (`game_id`),
  KEY `idx_player_id` (`player_id`),
  CONSTRAINT `fk_participant_game` FOREIGN KEY (`game_id`) REFERENCES `games` (`id`),
  CONSTRAINT `fk_participant_player` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小局参与表';

-- 6. P币流水表
CREATE TABLE IF NOT EXISTS `p_ledger` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `team_id` BIGINT UNSIGNED NOT NULL COMMENT '队伍ID',
  `match_id` BIGINT UNSIGNED NULL COMMENT '关联比赛ID',
  `type` VARCHAR(30) NOT NULL COMMENT '流水类型（match_reward/luxury_tax/loan_fee/forfeit_penalty/quiz_reward/admin_adjustment/catchup_fund/league_reclaim）',
  `amount` INT NOT NULL COMMENT '金额（正负表示增减）',
  `reason` VARCHAR(500) NULL COMMENT '原因说明',
  `version` VARCHAR(20) NULL COMMENT '结算版本号',
  `is_voided` TINYINT NOT NULL DEFAULT 0 COMMENT '是否作废',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_team_id` (`team_id`),
  KEY `idx_match_id` (`match_id`),
  KEY `idx_type` (`type`),
  KEY `idx_version` (`version`),
  CONSTRAINT `fk_ledger_team` FOREIGN KEY (`team_id`) REFERENCES `teams` (`id`),
  CONSTRAINT `fk_ledger_match` FOREIGN KEY (`match_id`) REFERENCES `matches` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='P币流水表';

-- 7. 身价变化表
CREATE TABLE IF NOT EXISTS `valuation_changes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '变化ID',
  `match_id` BIGINT UNSIGNED NOT NULL COMMENT '关联比赛ID',
  `player_id` BIGINT UNSIGNED NOT NULL COMMENT '选手ID',
  `before_value` INT UNSIGNED NOT NULL COMMENT '赛前身价',
  `objective_delta` INT NOT NULL COMMENT '客观变化',
  `subjective_delta` INT DEFAULT 0 COMMENT '主观变化',
  `subjective_reason` VARCHAR(500) NULL COMMENT '主观原因',
  `after_value` INT UNSIGNED NOT NULL COMMENT '赛后身价',
  `version` VARCHAR(20) NULL COMMENT '结算版本号',
  `is_voided` TINYINT NOT NULL DEFAULT 0 COMMENT '是否作废',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_match_id` (`match_id`),
  KEY `idx_player_id` (`player_id`),
  KEY `idx_version` (`version`),
  CONSTRAINT `fk_valuation_match` FOREIGN KEY (`match_id`) REFERENCES `matches` (`id`),
  CONSTRAINT `fk_valuation_player` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='身价变化表';

-- 8. 公告表
CREATE TABLE IF NOT EXISTS `announcements` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `title` VARCHAR(200) NOT NULL COMMENT '公告标题',
  `content` TEXT NOT NULL COMMENT '公告内容',
  `announce_date` DATE NOT NULL COMMENT '公告日期',
  `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否激活',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_date` (`announce_date`),
  KEY `idx_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告表';

-- 9. 规则表
CREATE TABLE IF NOT EXISTS `rules` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  `title` VARCHAR(200) NOT NULL COMMENT '规则标题',
  `content` TEXT NOT NULL COMMENT '规则内容（HTML）',
  `display_order` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `is_open` TINYINT NOT NULL DEFAULT 0 COMMENT '是否展开',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规则表';

-- 10. 附件表
CREATE TABLE IF NOT EXISTS `attachments` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '附件ID',
  `match_id` BIGINT UNSIGNED NULL COMMENT '关联比赛ID',
  `game_id` BIGINT UNSIGNED NULL COMMENT '关联小局ID',
  `type` VARCHAR(20) NOT NULL COMMENT '附件类型（score_screenshot/replay_file/other）',
  `label` VARCHAR(200) NOT NULL COMMENT '附件标签',
  `url` VARCHAR(500) NOT NULL COMMENT '附件URL',
  `file_path` VARCHAR(500) NULL COMMENT '文件路径',
  `uploaded_by` VARCHAR(50) NULL COMMENT '上传者',
  `note` TEXT NULL COMMENT '备注说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_match_id` (`match_id`),
  KEY `idx_game_id` (`game_id`),
  KEY `idx_type` (`type`),
  CONSTRAINT `fk_attachment_match` FOREIGN KEY (`match_id`) REFERENCES `matches` (`id`),
  CONSTRAINT `fk_attachment_game` FOREIGN KEY (`game_id`) REFERENCES `games` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='附件表';
