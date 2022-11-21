create table t_job
(
    pk_job_id       integer generated always as identity primary key,
    clazz           text        not null,
    cron_expression text
);

create table t_job_run
(
    pk_job_run_id integer generated always as identity primary key,
    fk_job_id     integer                  not null references t_job (pk_job_id),
    job_version   integer                  not null,
    state         varchar(20)              not null,
    log_output    text                     not null,
    started_at    timestamp with time zone not null,
    finished_at   timestamp with time zone
);

create table t_job_run_input
(
    pk_job_run_input_id integer generated always as identity primary key,
    fk_job_run_id       integer      not null references t_job_run (pk_job_run_id),
    input_name          varchar(255) not null,
    input_hash          varchar(128) not null
);