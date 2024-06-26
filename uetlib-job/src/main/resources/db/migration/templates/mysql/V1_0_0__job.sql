create table t_job
(
    pk_job_id       integer auto_increment not null primary key,
    clazz           text                   not null,
    cron_expression text
) charset = utf8mb4;

create table t_job_properties
(
    fk_job_id integer      not null references t_job (pk_job_id) on delete cascade on update cascade,
    `key`     varchar(100) not null,
    value     text,
    PRIMARY KEY (fk_job_id, `key`)
) charset = utf8mb4;

create table t_job_run
(
    pk_job_run_id integer auto_increment not null primary key,
    fk_job_id     integer                not null references t_job (pk_job_id) on delete cascade on update cascade,
    job_version   integer                not null,
    state         text                   not null,
    started_at    timestamp              not null,
    finished_at   timestamp
) charset = utf8mb4;

create index idx_finished_at on t_job_run (finished_at);

create table t_job_run_input
(
    pk_job_run_input_id integer auto_increment not null primary key,
    fk_job_run_id       integer                not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    input_name          varchar(255)           not null,
    input_hash          varchar(128)           not null
) charset = utf8mb4;