USE ltl_league;

INSERT INTO settlement_reward_rules (format, score_pattern, winner_amount, loser_amount, draw_amount, is_active) VALUES
-- BO1
('BO1', '1:0', 800,  200, NULL, 1),
('BO1', '0:1', 800,  200, NULL, 1),
-- BO2
('BO2', '2:0', 1200, 300, NULL, 1),
('BO2', '0:2', 1200, 300, NULL, 1),
('BO2', '1:1', NULL,  NULL, 600, 1),
-- BO3
('BO3', '2:0', 1500, 300, NULL, 1),
('BO3', '0:2', 1500, 300, NULL, 1),
('BO3', '2:1', 1800, 800, NULL, 1),
('BO3', '1:2', 1800, 800, NULL, 1)
ON DUPLICATE KEY UPDATE winner_amount = VALUES(winner_amount), loser_amount = VALUES(loser_amount), draw_amount = VALUES(draw_amount);
