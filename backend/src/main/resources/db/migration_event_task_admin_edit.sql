-- Apply once after backing up the production database.
-- Multiple claim rows are needed when an administrator cancels a claim and
-- the player claims the same task again after the one-hour cooling period.
ALTER TABLE `event_task_claims`
  DROP INDEX `uk_event_task_claim_player`,
  ADD KEY `idx_event_task_claim_player_history` (`task_id`, `player_id`, `id`),
  ADD COLUMN `title_snapshot` VARCHAR(200) NULL AFTER `bounty_reward_snapshot`,
  ADD COLUMN `requirements_snapshot` TEXT NULL AFTER `title_snapshot`,
  ADD COLUMN `admin_cancel_reason` VARCHAR(500) NULL AFTER `terminated_at`;

UPDATE `event_task_claims` AS c
JOIN `event_tasks` AS t ON t.id = c.task_id
SET c.title_snapshot = t.title, c.requirements_snapshot = t.requirements
WHERE c.title_snapshot IS NULL;

ALTER TABLE `event_task_reviews`
  ADD COLUMN `before_snapshot` LONGTEXT NULL AFTER `max_claimants_snapshot`,
  ADD COLUMN `after_snapshot` LONGTEXT NULL AFTER `before_snapshot`;
