-- just assume runner_key CURRENT for all existing runs, this is best-effort and sometimes wrong
alter table t_job_run
    add column runner_key text;
update t_job_run
set runner_key = 'CURRENT'
where runner_key is null;
alter table t_job_run
    modify runner_key text not null;

-- use text, not limited varchar
alter table t_job_run
    modify column state text;


create table t_job_run_database_submission
(
    pk_job_run_database_submission_id     integer auto_increment not null primary key,
    fk_job_id                             integer                not null references t_job (pk_job_id) on delete cascade on update cascade,
    submitted_at                          timestamp              not null,
    executed_at                           timestamp,
    job_run_database_submission_arguments json                   not null
) charset = utf8mb4;

DELETE
FROM t_job_properties
WHERE `key` = 'K8S_SKU';