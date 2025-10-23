alter table t_job_run
    add column version_tag varchar(32) not null default 'unknown';