-- Create table to store k8s jobs for job runs
create table t_k8s_jobs
(
    pk_k8s_jobs_id integer auto_increment not null primary key,
    fk_job_run_id  integer                not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    k8s_job_name   text                   not null,
    created_at     timestamp              not null,
    updated_at     timestamp              not null
);

-- Add columns for t_job
ALTER TABLE t_job
    ADD COLUMN created_at TIMESTAMP;
ALTER TABLE t_job
    ADD COLUMN updated_at TIMESTAMP;
UPDATE t_job
SET created_at = NOW()
WHERE true;
UPDATE t_job
SET updated_at = created_at
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
SET created_at = NOW()
WHERE true;
UPDATE t_job_run_input
SET updated_at = created_at
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
SET created_at = NOW()
WHERE true;
UPDATE t_job_properties
SET updated_at = created_at
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
SET created_at = NOW()
WHERE true;
UPDATE t_job_trigger
SET updated_at = created_at
WHERE true;
ALTER TABLE t_job_trigger
    MODIFY created_at TIMESTAMP NOT NULL;
ALTER TABLE t_job_trigger
    MODIFY updated_at TIMESTAMP NOT NULL;

-- Rename column and add updated_at for t_job_run_database_submission
ALTER TABLE t_job_run_database_submission
    RENAME COLUMN submitted_at TO created_at;
ALTER TABLE t_job_run_database_submission
    ADD COLUMN updated_at TIMESTAMP;
UPDATE t_job_run_database_submission
SET updated_at = COALESCE(executed_at, created_at)
WHERE true;
ALTER TABLE t_job_run_database_submission
    MODIFY updated_at TIMESTAMP NOT NULL;
