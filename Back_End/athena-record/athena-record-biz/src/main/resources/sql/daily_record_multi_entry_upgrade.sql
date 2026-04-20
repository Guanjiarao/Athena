ALTER TABLE `daily_record`
    DROP INDEX `uk_user_date_item`;

ALTER TABLE `daily_record`
    ADD INDEX `idx_user_date` (`user_id`, `record_date`),
    ADD INDEX `idx_user_date_item` (`user_id`, `record_date`, `record_item_id`);
