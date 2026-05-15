-- One row per (caregiver_id, meal_type, calendar day of recorded_at).
-- App writes recorded_at with LocalDateTime.now(MYT); MySQL DATE(recorded_at) is the logical "day".
--
-- If you already ran the old schedule_date migration, drop it first (ignore errors if not present):
--   ALTER TABLE meal_schedule DROP INDEX uk_meal_caregiver_type_day;
--   ALTER TABLE meal_schedule DROP COLUMN schedule_date;
--
-- If uk_meal_caregiver_type_recday already exists, DROP INDEX uk_meal_caregiver_type_recday; before re-run.

UPDATE meal_schedule
SET recorded_at = UTC_TIMESTAMP(6)
WHERE recorded_at IS NULL;

DELETE t
FROM meal_schedule t
         LEFT JOIN (SELECT MAX(id) AS id
                    FROM meal_schedule
                    WHERE recorded_at IS NOT NULL
                    GROUP BY caregiver_id, meal_type, DATE(recorded_at)) k ON t.id = k.id
WHERE k.id IS NULL
  AND t.recorded_at IS NOT NULL;

CREATE UNIQUE INDEX uk_meal_caregiver_type_recday
    ON meal_schedule (caregiver_id, meal_type, (CAST(recorded_at AS DATE)));
