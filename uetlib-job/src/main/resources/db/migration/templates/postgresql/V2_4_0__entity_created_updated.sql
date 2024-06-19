-- Add columns for t_job
alter table t_job
    add column created_at timestamp with time zone;
alter table t_job
    add column updated_at timestamp with time zone;
update t_job
set created_at = now()
where true;
update t_job
set updated_at = created_at
where true;
alter table t_job
    alter column created_at set not null;
alter table t_job
    alter column updated_at set not null;

-- Add columns for t_job_run
alter table t_job_run
    add column created_at timestamp with time zone;
alter table t_job_run
    add column updated_at timestamp with time zone;
update t_job_run
set created_at = started_at,
    updated_at = coalesce(finished_at, started_at)
where true;
alter table t_job_run
    alter column created_at set not null;
alter table t_job_run
    alter column updated_at set not null;

-- Add columns for t_job_run_input
alter table t_job_run_input
    add column created_at timestamp with time zone;
alter table t_job_run_input
    add column updated_at timestamp with time zone;
update t_job_run_input
set created_at = now()
where true;
update t_job_run_input
set updated_at = created_at
where true;
alter table t_job_run_input
    alter column created_at set not null;
alter table t_job_run_input
    alter column updated_at set not null;

-- Add columns for t_job_properties
alter table t_job_properties
    add column created_at timestamp with time zone;
alter table t_job_properties
    add column updated_at timestamp with time zone;
update t_job_properties
set created_at = now()
where true;
update t_job_properties
set updated_at = created_at
where true;
alter table t_job_properties
    alter column created_at set not null;
alter table t_job_properties
    alter column updated_at set not null;

-- Add columns for t_job_trigger
alter table t_job_trigger
    add column created_at timestamp with time zone;
alter table t_job_trigger
    add column updated_at timestamp with time zone;
update t_job_trigger
set created_at = now()
where true;
update t_job_trigger
set updated_at = created_at
where true;
alter table t_job_trigger
    alter column created_at set not null;
alter table t_job_trigger
    alter column updated_at set not null;

-- Rename column and add updated_at for t_job_run_database_submission
alter table t_job_run_database_submission
    rename column submitted_at to created_at;
alter table t_job_run_database_submission
    add column updated_at timestamp with time zone;
update t_job_run_database_submission
set updated_at = coalesce(executed_at, created_at)
where true;
alter table t_job_run_database_submission
    alter column updated_at set not null;
