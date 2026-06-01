-- LTL积分兑换功能数据库迁移SQL
-- 版本: v1.7
-- 创建时间: 2024-06-01

START TRANSACTION;

-- 1. 创建奖品表
CREATE TABLE IF NOT EXISTS prizes (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL COMMENT '奖品名称',
    description TEXT COMMENT '奖品描述',
    image_url VARCHAR(1024) COMMENT '奖品图片URL',
    cost_points INT NOT NULL COMMENT '兑换所需积分',
    stock INT NOT NULL DEFAULT 0 COMMENT '库存数量',
    is_active TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖品表';

-- 2. 创建兑换记录表
CREATE TABLE IF NOT EXISTS prize_exchanges (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    prize_id BIGINT UNSIGNED NOT NULL COMMENT '奖品ID',
    player_id BIGINT UNSIGNED NOT NULL COMMENT '选手ID',
    player_name VARCHAR(255) NOT NULL COMMENT '选手名称',
    cost_points INT NOT NULL COMMENT '消耗积分',
    status VARCHAR(50) DEFAULT 'pending' COMMENT '兑换状态：pending-待处理，completed-已完成，cancelled-已取消',
    contact_info VARCHAR(512) COMMENT '联系方式',
    remark TEXT COMMENT '备注信息',
    processed_at DATETIME COMMENT '处理时间',
    processed_by VARCHAR(255) COMMENT '处理人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    FOREIGN KEY (prize_id) REFERENCES prizes(id),
    FOREIGN KEY (player_id) REFERENCES players(id),
    INDEX idx_prize_id (prize_id),
    INDEX idx_player_id (player_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='兑换记录表';

-- 3. 创建选手存款流水记录类型（用于积分兑换）
-- 这个表已经存在，只需要在type字段中新增一种类型即可
-- player_deposit_ledger表的type字段将新增: 'prize_exchange' 类型

COMMIT;
