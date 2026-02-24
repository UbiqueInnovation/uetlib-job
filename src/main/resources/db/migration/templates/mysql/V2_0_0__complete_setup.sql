-- 1.0.0

create table t_job
(
    pk_job_id  integer auto_increment not null primary key,
    clazz      text                   not null,
    enabled boolean not null default true,
    created_at timestamp              not null,
    updated_at timestamp              not null
) charset = utf8mb4;

create table t_job_properties
(
    fk_job_id  integer      not null references t_job (pk_job_id) on delete cascade on update cascade,
    `key`      varchar(100) not null,
    value      text,
    created_at timestamp    not null,
    updated_at timestamp    not null,
    PRIMARY KEY (fk_job_id, `key`)
) charset = utf8mb4;

create table t_job_run
(
    pk_job_run_id     integer auto_increment not null primary key,
    fk_job_id         integer                not null references t_job (pk_job_id) on delete cascade on update cascade,
    version_tag       text                   not null,
    runner_key        text                   not null,
    trigger_type      text,
    created_at        timestamp              not null,
    updated_at        timestamp              not null,
    started_at        timestamp              not null,
    finished_at       timestamp              null,
    job_run_arguments json,
    index (started_at)
) charset = utf8mb4;

create index idx_finished_at on t_job_run (finished_at);

create table t_job_run_input
(
    pk_job_run_input_id integer auto_increment not null primary key,
    fk_job_run_id       integer                not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    input_name          varchar(255)           not null,
    input_hash          varchar(128)           not null,
    created_at          timestamp              not null,
    updated_at          timestamp              not null
) charset = utf8mb4;

-- logging

create table t_logging_event
(
    pk_logging_event_id integer auto_increment not null primary key,
    fk_job_run_id       integer                not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    timestamp           timestamp              not null,
    formatted_message   text                   not null
) charset = utf8mb4;

create index idx_logging_event_job_run_id_timestamp on t_logging_event (fk_job_run_id, timestamp asc);

-- 2.0.0

CREATE TABLE t_job_trigger
(
    pk_job_trigger_id  INTEGER auto_increment NOT NULL PRIMARY KEY,
    fk_job_id          INTEGER                NOT NULL REFERENCES t_job (pk_job_id) ON DELETE CASCADE ON UPDATE CASCADE,
    type               VARCHAR(100)           NOT NULL, -- MySQL requires a limit on keys / indices, whereas MariaDB does not
    trigger_definition JSON                   NOT NULL,
    created_at         timestamp              not null,
    updated_at         timestamp              not null
) charset = utf8mb4;

CREATE INDEX idx_type ON t_job_trigger (type);
CREATE INDEX idx_job_id ON t_job_trigger (fk_job_id);

create table t_job_run_database_submission
(
    pk_job_run_database_submission_id     integer auto_increment not null primary key,
    fk_job_id                             integer                not null references t_job (pk_job_id) on delete cascade on update cascade,
    created_at                            timestamp              not null,
    updated_at                            timestamp              not null,
    executed_at                           timestamp              null,
    job_run_database_submission_arguments json                   not null
) charset = utf8mb4;


create table t_k8s_jobs
(
    pk_k8s_jobs_id integer auto_increment not null primary key,
    fk_job_run_id  integer                not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    k8s_job_name   text                   not null,
    created_at     timestamp              not null,
    updated_at     timestamp              not null
) charset = utf8mb4;

CREATE TABLE t_job_run_state_transition
(
    pk_job_run_state_transition_id INT auto_increment NOT NULL PRIMARY KEY,
    fk_job_run_id                  INT                NOT NULL REFERENCES t_job_run (pk_job_run_id) ON DELETE CASCADE ON UPDATE CASCADE,
    state                          TEXT               NOT NULL,
    transitioned_at                TIMESTAMP(3)       NOT NULL,
    index (transitioned_at),
    index (transitioned_at desc),
    index (state(10))
);

CREATE VIEW v_job_run_latest_state AS
SELECT tjr.*,
       (SELECT state
        FROM t_job_run_state_transition tjrs
        WHERE tjrs.fk_job_run_id = tjr.pk_job_run_id
        ORDER BY tjrs.transitioned_at DESC, tjrs.pk_job_run_state_transition_id DESC
        LIMIT 1) AS latest_state
from t_job_run tjr;