-- Add columns for t_job
ALTER TABLE t_job
    ADD COLUMN created_at TIMESTAMP;
ALTER TABLE t_job
    ADD COLUMN updated_at TIMESTAMP;
UPDATE t_job
SET created_at = NOW(),
    updated_at = NOW()
WHERE true;
ALTER TABLE t_job
    MODIFY created_at TIMESTAMP NOT NULL;
ALTER TABLE t_job
    MODIFY updated_at TIMESTAMP NOT NULL;

-- Add columns for t_job_run
ALTER TABLE t_job_run
    ADD COLUMN created_at TIMESTAMP;
ALTER TABLE t_job_run
    ADD COLUMN updated_at TIMESTAMP;
UPDATE t_job_run
SET created_at = started_at,
    updated_at = COALESCE(finished_at, started_at)
WHERE true;
ALTER TABLE t_job_run
    MODIFY created_at TIMESTAMP NOT NULL;
ALTER TABLE t_job_run
    MODIFY updated_at TIMESTAMP NOT NULL;

-- Add columns for t_job_run_input
ALTER TABLE t_job_run_input
    ADD COLUMN created_at TIMESTAMP;
ALTER TABLE t_job_run_input
    ADD COLUMN updated_at TIMESTAMP;
UPDATE t_job_run_input
SET created_at = NOW(),
    updated_at = NOW()
WHERE true;
ALTER TABLE t_job_run_input
    MODIFY created_at TIMESTAMP NOT NULL;
ALTER TABLE t_job_run_input
    MODIFY updated_at TIMESTAMP NOT NULL;

-- Add columns for t_job_properties
ALTER TABLE t_job_properties
    ADD COLUMN created_at TIMESTAMP;
ALTER TABLE t_job_properties
    ADD COLUMN updated_at TIMESTAMP;
UPDATE t_job_properties
SET created_at = NOW(),
    updated_at = NOW()
WHERE true;
ALTER TABLE t_job_properties
    MODIFY created_at TIMESTAMP NOT NULL;
ALTER TABLE t_job_properties
    MODIFY updated_at TIMESTAMP NOT NULL;

-- Add columns for t_job_trigger
ALTER TABLE t_job_trigger
    ADD COLUMN created_at TIMESTAMP;
ALTER TABLE t_job_trigger
    ADD COLUMN updated_at TIMESTAMP;
UPDATE t_job_trigger
SET created_at = NOW(),
    updated_at = NOW()
WHERE true;
ALTER TABLE t_job_trigger
    MODIFY created_at TIMESTAMP NOT NULL;
ALTER TABLE t_job_trigger
    MODIFY updated_at TIMESTAMP NOT NULL;

-- Rename column and add updated_at for t_job_run_database_submission
ALTER TABLE t_job_run_database_submission RENAME COLUMN submitted_at TO created_at;
ALTER TABLE t_job_run_database_submission
    ADD COLUMN updated_at TIMESTAMP;
UPDATE t_job_run_database_submission
SET updated_at = COALESCE(executed_at, created_at)
WHERE true;
ALTER TABLE t_job_run_database_submission
    MODIFY updated_at TIMESTAMP NOT NULL;
