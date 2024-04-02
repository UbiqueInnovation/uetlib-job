create table t_job_run_database_submission
(
    pk_job_run_database_submission_id     integer generated always as identity primary key,
    fk_job_id                             integer                  not null references t_job (pk_job_id) on delete cascade on update cascade,
    submitted_at                          timestamp with time zone not null,
    executed_at                           timestamp with time zone,
    job_run_database_submission_arguments jsonb                    not null
);