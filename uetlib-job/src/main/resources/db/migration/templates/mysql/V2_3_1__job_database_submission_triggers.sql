create table t_job_run_database_submission
(
    pk_job_run_database_submission_id     integer auto_increment not null primary key,
    fk_job_id                             integer                not null references t_job (pk_job_id) on delete cascade on update cascade,
    submitted_at                          timestamp              not null,
    executed_at                           timestamp,
    job_run_database_submission_arguments json                   not null
);