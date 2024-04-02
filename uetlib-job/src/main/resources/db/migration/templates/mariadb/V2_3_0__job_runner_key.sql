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