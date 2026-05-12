-- Wall-clock time when each meal_schedule row was logged (regression x-axis vs. meal_time).
ALTER TABLE meal_schedule
    ADD COLUMN recorded_at DATETIME(6) NULL COMMENT 'When this row was logged';

-- Backfill legacy rows so they sort deterministically; trend model may be flat until new logs arrive.
UPDATE meal_schedule
SET recorded_at = UTC_TIMESTAMP(6)
WHERE recorded_at IS NULL;
