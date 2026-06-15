-- Store which home-team player is replaced by a loaned player in match settlement input.

USE ltl_league;

ALTER TABLE match_result_loan_inputs
  ADD COLUMN `replaced_player_id` BIGINT UNSIGNED NULL COMMENT '被替换选手ID' AFTER `player_id`,
  ADD KEY `idx_replaced_player_id` (`replaced_player_id`),
  ADD CONSTRAINT `fk_loan_input_replaced_player`
    FOREIGN KEY (`replaced_player_id`) REFERENCES `players` (`id`);
