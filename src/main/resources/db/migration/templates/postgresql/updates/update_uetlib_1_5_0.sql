alter table t_job_run
    add column version_tag varchar not null default 'unknown';