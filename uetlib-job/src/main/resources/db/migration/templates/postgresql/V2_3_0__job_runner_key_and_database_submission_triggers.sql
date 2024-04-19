-- just assume runner_key CURRENT for all existing runs, this is best-effort and sometimes wrong
alter table t_job_run
    add column runner_key text not null default 'CURRENT';
alter table t_job_run
    alter column runner_key drop default;

-- use text, not limited varchar
alter table t_job_run
    alter column state type text;


create table t_job_run_database_submission
(
    pk_job_run_database_submission_id     integer generated always as identity primary key,
    fk_job_id                             integer                  not null references t_job (pk_job_id) on delete cascade on update cascade,
    submitted_at                          timestamp with time zone not null,
    executed_at                           timestamp with time zone,
    job_run_database_submission_arguments jsonb                    not null
);

DELETE
FROM t_job_properties
WHERE key = 'K8S_SKU';