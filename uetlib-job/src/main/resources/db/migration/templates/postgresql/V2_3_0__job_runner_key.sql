-- just assume runner_key CURRENT for all existing runs, this is best-effort and sometimes wrong
alter table t_job_run
    add column runner_key text not null default 'CURRENT';
alter table t_job_run
    alter column runner_key drop default;

-- use text, not limited varchar
alter table t_job_run
    alter column state type text;