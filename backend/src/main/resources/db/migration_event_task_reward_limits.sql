-- Apply once before deploying the matching backend. Existing published tasks
-- keep their original reward limit, and old claim history remains intact.
ALTER TABLE `event_tasks`
  ADD COLUMN `repeatable` TINYINT NOT NULL DEFAULT 0 AFTER `anonymous`,
  ADD COLUMN `publication_fee_amount` INT UNSIGNED NOT NULL DEFAULT 0 AFTER `anonymous_fee_amount`,
  ADD COLUMN `max_reward_recipients` INT UNSIGNED NULL AFTER `max_claimants`;

UPDATE `event_tasks`
SET `max_reward_recipients` = `max_claimants`
WHERE `max_claimants` IS NOT NULL;
