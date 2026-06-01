-- LTL赛季数据初始化SQL
-- 生成时间: 2026-05-29 18:48:37

START TRANSACTION;

-- 1. 更新队伍数据
UPDATE teams SET points = 9, p_coins = 1137, `rank` = 1, updated_at = NOW() WHERE state = '秦';
UPDATE teams SET points = 4, p_coins = 8889, `rank` = 3, updated_at = NOW() WHERE state = '楚';
UPDATE teams SET points = 4, p_coins = 4027, `rank` = 3, updated_at = NOW() WHERE state = '蜀';
UPDATE teams SET points = 8, p_coins = 3482, `rank` = 2, updated_at = NOW() WHERE state = '吴';
UPDATE teams SET points = 3, p_coins = 6005, `rank` = 5, updated_at = NOW() WHERE state = '越';
UPDATE teams SET points = 3, p_coins = 6653, `rank` = 5, updated_at = NOW() WHERE state = '燕';

-- 2. 更新选手身价和存款
UPDATE players p SET value = 1800, deposit = 540, updated_at = NOW() WHERE p.name = 'ZerstaN' AND p.team_id = (SELECT id FROM teams WHERE state = '秦');
UPDATE players p SET value = 3030, deposit = 5931, updated_at = NOW() WHERE p.name = '天下人' AND p.team_id = (SELECT id FROM teams WHERE state = '秦');
UPDATE players p SET value = 1886, deposit = 1440, updated_at = NOW() WHERE p.name = 'LOL历史总得分王' AND p.team_id = (SELECT id FROM teams WHERE state = '秦');
UPDATE players p SET value = 2926, deposit = 290, updated_at = NOW() WHERE p.name = '樱岛麻衣' AND p.team_id = (SELECT id FROM teams WHERE state = '秦');
UPDATE players p SET value = 3466, deposit = 2761, updated_at = NOW() WHERE p.name = 'BUAA2wh' AND p.team_id = (SELECT id FROM teams WHERE state = '秦');
UPDATE players p SET value = 3583, deposit = -3820, updated_at = NOW() WHERE p.name = '大橙子' AND p.team_id = (SELECT id FROM teams WHERE state = '楚');
UPDATE players p SET value = 2100, deposit = 360, updated_at = NOW() WHERE p.name = '不早起的阿斗' AND p.team_id = (SELECT id FROM teams WHERE state = '楚');
UPDATE players p SET value = 1812, deposit = 1493, updated_at = NOW() WHERE p.name = 'cap999' AND p.team_id = (SELECT id FROM teams WHERE state = '楚');
UPDATE players p SET value = 1031, deposit = 161, updated_at = NOW() WHERE p.name = '是你的真如啊' AND p.team_id = (SELECT id FROM teams WHERE state = '楚');
UPDATE players p SET value = 1083, deposit = 267, updated_at = NOW() WHERE p.name = '猫喜欢吉良吉影k' AND p.team_id = (SELECT id FROM teams WHERE state = '楚');
UPDATE players p SET value = 2669, deposit = 952, updated_at = NOW() WHERE p.name = '广寒枝' AND p.team_id = (SELECT id FROM teams WHERE state = '楚');
UPDATE players p SET value = 3746, deposit = 1300, updated_at = NOW() WHERE p.name = 'BUAA5km' AND p.team_id = (SELECT id FROM teams WHERE state = '蜀');
UPDATE players p SET value = 2584, deposit = 1403, updated_at = NOW() WHERE p.name = '千山万水' AND p.team_id = (SELECT id FROM teams WHERE state = '蜀');
UPDATE players p SET value = 2981, deposit = 1408, updated_at = NOW() WHERE p.name = 'Puler' AND p.team_id = (SELECT id FROM teams WHERE state = '蜀');
UPDATE players p SET value = 1650, deposit = 960, updated_at = NOW() WHERE p.name = 'Kuromi' AND p.team_id = (SELECT id FROM teams WHERE state = '蜀');
UPDATE players p SET value = 1020, deposit = 839, updated_at = NOW() WHERE p.name = '脚踏实地' AND p.team_id = (SELECT id FROM teams WHERE state = '蜀');
UPDATE players p SET value = 3320, deposit = 63, updated_at = NOW() WHERE p.name = '实践检验认识' AND p.team_id = (SELECT id FROM teams WHERE state = '吴');
UPDATE players p SET value = 2844, deposit = 374, updated_at = NOW() WHERE p.name = '黑巧终结者' AND p.team_id = (SELECT id FROM teams WHERE state = '吴');
UPDATE players p SET value = 2511, deposit = 1442, updated_at = NOW() WHERE p.name = 'theshy' AND p.team_id = (SELECT id FROM teams WHERE state = '吴');
UPDATE players p SET value = 1815, deposit = 584, updated_at = NOW() WHERE p.name = '莫以故事、诉于卿' AND p.team_id = (SELECT id FROM teams WHERE state = '吴');
UPDATE players p SET value = 1559, deposit = 422, updated_at = NOW() WHERE p.name = '小铭慕斯raga' AND p.team_id = (SELECT id FROM teams WHERE state = '吴');
UPDATE players p SET value = 2318, deposit = 1967, updated_at = NOW() WHERE p.name = 'T1banana' AND p.team_id = (SELECT id FROM teams WHERE state = '越');
UPDATE players p SET value = 2584, deposit = 1618, updated_at = NOW() WHERE p.name = '忧伤博弈' AND p.team_id = (SELECT id FROM teams WHERE state = '越');
UPDATE players p SET value = 2497, deposit = 2919, updated_at = NOW() WHERE p.name = '何必恨王昌' AND p.team_id = (SELECT id FROM teams WHERE state = '越');
UPDATE players p SET value = 1945, deposit = 459, updated_at = NOW() WHERE p.name = '水龙吟苏幕遮' AND p.team_id = (SELECT id FROM teams WHERE state = '越');
UPDATE players p SET value = 1759, deposit = 1929, updated_at = NOW() WHERE p.name = '万泉诗人' AND p.team_id = (SELECT id FROM teams WHERE state = '越');
UPDATE players p SET value = 3297, deposit = 1547, updated_at = NOW() WHERE p.name = '想你时风起' AND p.team_id = (SELECT id FROM teams WHERE state = '燕');
UPDATE players p SET value = 2702, deposit = 2597, updated_at = NOW() WHERE p.name = '凯隐不是该赢吗' AND p.team_id = (SELECT id FROM teams WHERE state = '燕');
UPDATE players p SET value = 2442, deposit = 1668, updated_at = NOW() WHERE p.name = '不够活跃' AND p.team_id = (SELECT id FROM teams WHERE state = '燕');
UPDATE players p SET value = 1568, deposit = 805, updated_at = NOW() WHERE p.name = '明栗双收' AND p.team_id = (SELECT id FROM teams WHERE state = '燕');
UPDATE players p SET value = 2344, deposit = 1105, updated_at = NOW() WHERE p.name = '坑货、别靠近我' AND p.team_id = (SELECT id FROM teams WHERE state = '燕');

-- 3. 添加自由人选手
INSERT INTO players (team_id, name, value, deposit, status, created_at, updated_at) VALUES (NULL, '罗', 3300, 500, 3, NOW(), NOW());
INSERT INTO players (team_id, name, value, deposit, status, created_at, updated_at) VALUES (NULL, '陶吉吉', 2859, 4918, 3, NOW(), NOW());
INSERT INTO players (team_id, name, value, deposit, status, created_at, updated_at) VALUES (NULL, 'Yukari', 2818, 300, 3, NOW(), NOW());
INSERT INTO players (team_id, name, value, deposit, status, created_at, updated_at) VALUES (NULL, 'Kenzhou(教练）', 1500, 200, 3, NOW(), NOW());

-- 4. 创建队伍P币初始化流水
INSERT INTO p_ledger (team_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT id, 'admin_adjustment', 1137, '赛季初始化', 'season_init', 0, 1137, NOW(), 0 FROM teams WHERE state = '秦';
INSERT INTO p_ledger (team_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT id, 'admin_adjustment', 8889, '赛季初始化', 'season_init', 0, 8889, NOW(), 0 FROM teams WHERE state = '楚';
INSERT INTO p_ledger (team_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT id, 'admin_adjustment', 4027, '赛季初始化', 'season_init', 0, 4027, NOW(), 0 FROM teams WHERE state = '蜀';
INSERT INTO p_ledger (team_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT id, 'admin_adjustment', 3482, '赛季初始化', 'season_init', 0, 3482, NOW(), 0 FROM teams WHERE state = '吴';
INSERT INTO p_ledger (team_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT id, 'admin_adjustment', 6005, '赛季初始化', 'season_init', 0, 6005, NOW(), 0 FROM teams WHERE state = '越';
INSERT INTO p_ledger (team_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT id, 'admin_adjustment', 6653, '赛季初始化', 'season_init', 0, 6653, NOW(), 0 FROM teams WHERE state = '燕';

-- 5. 创建选手存款初始化流水
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 540, '赛季初始化', 'season_init', 0, 540, NOW(), 0 FROM players p WHERE p.name = 'ZerstaN' AND p.team_id = (SELECT id FROM teams WHERE state = '秦');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 5931, '赛季初始化', 'season_init', 0, 5931, NOW(), 0 FROM players p WHERE p.name = '天下人' AND p.team_id = (SELECT id FROM teams WHERE state = '秦');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 1440, '赛季初始化', 'season_init', 0, 1440, NOW(), 0 FROM players p WHERE p.name = 'LOL历史总得分王' AND p.team_id = (SELECT id FROM teams WHERE state = '秦');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 290, '赛季初始化', 'season_init', 0, 290, NOW(), 0 FROM players p WHERE p.name = '樱岛麻衣' AND p.team_id = (SELECT id FROM teams WHERE state = '秦');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 2761, '赛季初始化', 'season_init', 0, 2761, NOW(), 0 FROM players p WHERE p.name = 'BUAA2wh' AND p.team_id = (SELECT id FROM teams WHERE state = '秦');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', -3820, '赛季初始化', 'season_init', 0, -3820, NOW(), 0 FROM players p WHERE p.name = '大橙子' AND p.team_id = (SELECT id FROM teams WHERE state = '楚');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 360, '赛季初始化', 'season_init', 0, 360, NOW(), 0 FROM players p WHERE p.name = '不早起的阿斗' AND p.team_id = (SELECT id FROM teams WHERE state = '楚');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 1493, '赛季初始化', 'season_init', 0, 1493, NOW(), 0 FROM players p WHERE p.name = 'cap999' AND p.team_id = (SELECT id FROM teams WHERE state = '楚');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 161, '赛季初始化', 'season_init', 0, 161, NOW(), 0 FROM players p WHERE p.name = '是你的真如啊' AND p.team_id = (SELECT id FROM teams WHERE state = '楚');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 267, '赛季初始化', 'season_init', 0, 267, NOW(), 0 FROM players p WHERE p.name = '猫喜欢吉良吉影k' AND p.team_id = (SELECT id FROM teams WHERE state = '楚');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 952, '赛季初始化', 'season_init', 0, 952, NOW(), 0 FROM players p WHERE p.name = '广寒枝' AND p.team_id = (SELECT id FROM teams WHERE state = '楚');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 1300, '赛季初始化', 'season_init', 0, 1300, NOW(), 0 FROM players p WHERE p.name = 'BUAA5km' AND p.team_id = (SELECT id FROM teams WHERE state = '蜀');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 1403, '赛季初始化', 'season_init', 0, 1403, NOW(), 0 FROM players p WHERE p.name = '千山万水' AND p.team_id = (SELECT id FROM teams WHERE state = '蜀');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 1408, '赛季初始化', 'season_init', 0, 1408, NOW(), 0 FROM players p WHERE p.name = 'Puler' AND p.team_id = (SELECT id FROM teams WHERE state = '蜀');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 960, '赛季初始化', 'season_init', 0, 960, NOW(), 0 FROM players p WHERE p.name = 'Kuromi' AND p.team_id = (SELECT id FROM teams WHERE state = '蜀');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 839, '赛季初始化', 'season_init', 0, 839, NOW(), 0 FROM players p WHERE p.name = '脚踏实地' AND p.team_id = (SELECT id FROM teams WHERE state = '蜀');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 63, '赛季初始化', 'season_init', 0, 63, NOW(), 0 FROM players p WHERE p.name = '实践检验认识' AND p.team_id = (SELECT id FROM teams WHERE state = '吴');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 374, '赛季初始化', 'season_init', 0, 374, NOW(), 0 FROM players p WHERE p.name = '黑巧终结者' AND p.team_id = (SELECT id FROM teams WHERE state = '吴');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 1442, '赛季初始化', 'season_init', 0, 1442, NOW(), 0 FROM players p WHERE p.name = 'theshy' AND p.team_id = (SELECT id FROM teams WHERE state = '吴');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 584, '赛季初始化', 'season_init', 0, 584, NOW(), 0 FROM players p WHERE p.name = '莫以故事、诉于卿' AND p.team_id = (SELECT id FROM teams WHERE state = '吴');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 422, '赛季初始化', 'season_init', 0, 422, NOW(), 0 FROM players p WHERE p.name = '小铭慕斯raga' AND p.team_id = (SELECT id FROM teams WHERE state = '吴');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 1967, '赛季初始化', 'season_init', 0, 1967, NOW(), 0 FROM players p WHERE p.name = 'T1banana' AND p.team_id = (SELECT id FROM teams WHERE state = '越');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 1618, '赛季初始化', 'season_init', 0, 1618, NOW(), 0 FROM players p WHERE p.name = '忧伤博弈' AND p.team_id = (SELECT id FROM teams WHERE state = '越');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 2919, '赛季初始化', 'season_init', 0, 2919, NOW(), 0 FROM players p WHERE p.name = '何必恨王昌' AND p.team_id = (SELECT id FROM teams WHERE state = '越');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 459, '赛季初始化', 'season_init', 0, 459, NOW(), 0 FROM players p WHERE p.name = '水龙吟苏幕遮' AND p.team_id = (SELECT id FROM teams WHERE state = '越');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 1929, '赛季初始化', 'season_init', 0, 1929, NOW(), 0 FROM players p WHERE p.name = '万泉诗人' AND p.team_id = (SELECT id FROM teams WHERE state = '越');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 1547, '赛季初始化', 'season_init', 0, 1547, NOW(), 0 FROM players p WHERE p.name = '想你时风起' AND p.team_id = (SELECT id FROM teams WHERE state = '燕');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 2597, '赛季初始化', 'season_init', 0, 2597, NOW(), 0 FROM players p WHERE p.name = '凯隐不是该赢吗' AND p.team_id = (SELECT id FROM teams WHERE state = '燕');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 1668, '赛季初始化', 'season_init', 0, 1668, NOW(), 0 FROM players p WHERE p.name = '不够活跃' AND p.team_id = (SELECT id FROM teams WHERE state = '燕');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 805, '赛季初始化', 'season_init', 0, 805, NOW(), 0 FROM players p WHERE p.name = '明栗双收' AND p.team_id = (SELECT id FROM teams WHERE state = '燕');
INSERT INTO player_deposit_ledger (player_id, type, amount, reason, source, balance_before, balance_after, created_at, deleted) SELECT p.id, 'admin_adjustment', 1105, '赛季初始化', 'season_init', 0, 1105, NOW(), 0 FROM players p WHERE p.name = '坑货、别靠近我' AND p.team_id = (SELECT id FROM teams WHERE state = '燕');

COMMIT;

-- 验证查询
SELECT '队伍数据:' as '';
SELECT state, name, points, p_coins, `rank` FROM teams WHERE deleted = 0 ORDER BY state;

SELECT '选手数据:' as '';
SELECT p.name, t.state, p.value, p.deposit, p.status FROM players p LEFT JOIN teams t ON p.team_id = t.id WHERE p.deleted = 0 ORDER BY t.state, p.name;

SELECT '自由人选手:' as '';
SELECT name, value, deposit FROM players WHERE team_id IS NULL AND deleted = 0;