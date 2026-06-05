-- 规则参数调整功能
-- 执行方式: mysql -h <host> -u <user> -p ltl_league < migration_rule_parameters.sql

CREATE TABLE IF NOT EXISTS `rule_parameters` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '参数ID',
  `param_key` VARCHAR(100) NOT NULL COMMENT '参数唯一Key',
  `group_key` VARCHAR(50) NOT NULL COMMENT '分组Key',
  `group_name` VARCHAR(50) NOT NULL COMMENT '分组名称',
  `name` VARCHAR(100) NOT NULL COMMENT '参数名称',
  `description` VARCHAR(500) NOT NULL COMMENT '参数说明',
  `value_type` VARCHAR(20) NOT NULL COMMENT '值类型（int/decimal）',
  `value_text` VARCHAR(100) NOT NULL COMMENT '当前值',
  `unit` VARCHAR(20) NULL COMMENT '单位',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_parameter_key` (`param_key`, `deleted`),
  KEY `idx_rule_parameter_group` (`group_key`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规则参数表';

CREATE TABLE IF NOT EXISTS `rule_parameter_histories` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '历史ID',
  `parameter_id` BIGINT UNSIGNED NULL COMMENT '参数ID',
  `param_key` VARCHAR(100) NOT NULL COMMENT '参数Key',
  `param_name` VARCHAR(100) NOT NULL COMMENT '参数名称',
  `group_key` VARCHAR(50) NOT NULL COMMENT '分组Key',
  `group_name` VARCHAR(50) NOT NULL COMMENT '分组名称',
  `old_value` VARCHAR(500) NULL COMMENT '原值',
  `new_value` VARCHAR(500) NULL COMMENT '新值',
  `operator` VARCHAR(100) NOT NULL COMMENT '操作人',
  `reason` VARCHAR(500) NOT NULL COMMENT '修改原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_rule_parameter_history_key` (`param_key`, `created_at`),
  KEY `idx_rule_parameter_history_group` (`group_key`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规则参数变更历史表';
