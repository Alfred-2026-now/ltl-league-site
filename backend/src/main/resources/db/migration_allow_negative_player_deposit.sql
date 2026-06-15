-- Allow admin manual adjustments and fines to bring player deposits below zero.
-- Existing active spending flows must still enforce sufficient balance in code.

USE ltl_league;

ALTER TABLE players
  MODIFY COLUMN `deposit` INT NOT NULL DEFAULT 0 COMMENT '选手P币存款';
