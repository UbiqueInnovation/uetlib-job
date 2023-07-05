create table t_job
(
    pk_job_id       integer generated always as identity primary key,
    clazz           text not null,
    cron_expression text
);

create table t_job_properties
(
    fk_job_id integer not null references t_job (pk_job_id) on delete cascade on update cascade,
    key       text    not null,
    value     text,
    PRIMARY KEY (fk_job_id, key)
);

create table t_job_run
(
    pk_job_run_id integer generated always as identity primary key,
    fk_job_id     integer                  not null references t_job (pk_job_id) on delete cascade on update cascade,
    job_version   integer                  not null,
    state         varchar(20)              not null,
    started_at    timestamp with time zone not null,
    finished_at   timestamp with time zone
);

create index idx_finished_at on t_job_run (finished_at);

create table t_job_run_input
(
    pk_job_run_input_id integer generated always as identity primary key,
    fk_job_run_id       integer      not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    input_name          varchar(255) not null,
    input_hash          varchar(128) not null
);