-- 清理LTL联赛赛程和战绩数据
-- 注意：此脚本会逻辑删除（设置deleted=1）所有比赛相关数据

-- 开始事务
START TRANSACTION;

-- 1. 清理小局参与记录
UPDATE game_participants SET deleted = 1 WHERE deleted = 0;

-- 2. 清理小局记录
UPDATE games SET deleted = 1 WHERE deleted = 0;

-- 3. 清理赛果版本
UPDATE match_results SET deleted = 1 WHERE deleted = 0;

-- 4. 清理比赛
UPDATE matches SET deleted = 1 WHERE deleted = 0;

-- 5. 清理身价变化记录
UPDATE valuation_changes SET deleted = 1 WHERE deleted = 0;

-- 6. 清理赛果租借费输入
UPDATE match_result_loan_inputs SET deleted = 1 WHERE deleted = 0;

-- 7. 清理赛果身价调整输入
UPDATE match_result_valuation_inputs SET deleted = 1 WHERE deleted = 0;

-- 8. 清理附件
UPDATE attachments SET deleted = 1 WHERE deleted = 0;

-- 9. 清理比赛相关的P币流水（仅清除比赛结果产生的流水，保留初始化的）
UPDATE p_ledger SET deleted = 1, is_voided = 1 WHERE deleted = 0 AND source = 'match_result';

-- 10. 清理比赛相关的选手存款流水（仅清除比赛结果产生的流水，保留初始化的）
UPDATE player_deposit_ledger SET deleted = 1, is_voided = 1 WHERE deleted = 0 AND source = 'match_result';

-- 提交事务
COMMIT;

-- 验证清理结果
SELECT '清理后的数据统计:' as '';
SELECT '比赛数量' as type, COUNT(*) as count FROM matches WHERE deleted = 0;
SELECT '赛果数量' as type, COUNT(*) as count FROM match_results WHERE deleted = 0;
SELECT '小局数量' as type, COUNT(*) as count FROM games WHERE deleted = 0;
