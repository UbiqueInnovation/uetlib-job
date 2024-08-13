-- 1.0.0

create table t_job
(
    pk_job_id  integer auto_increment not null primary key,
    clazz      text                   not null,
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
    job_version       integer                not null,

    runner_key        text                   not null,
    created_at        timestamp              not null,
    updated_at        timestamp              not null,
    started_at        timestamp              not null,
    finished_at       timestamp,
    job_run_arguments json
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

-- 1.3.0

CREATE TABLE logging_event
(
    timestmp          BIGINT       NOT NULL,
    formatted_message TEXT         NOT NULL,
    logger_name       VARCHAR(254) NOT NULL,
    level_string      VARCHAR(254) NOT NULL,
    thread_name       VARCHAR(254),
    reference_flag    SMALLINT,
    arg0              VARCHAR(254),
    arg1              VARCHAR(254),
    arg2              VARCHAR(254),
    arg3              VARCHAR(254),
    caller_filename   VARCHAR(254) NOT NULL,
    caller_class      VARCHAR(254) NOT NULL,
    caller_method     VARCHAR(254) NOT NULL,
    caller_line       CHAR(4)      NOT NULL,
    job_run_id        INTEGER      NOT NULL,
    event_id          BIGINT auto_increment PRIMARY KEY
) charset = utf8mb4;

CREATE TABLE logging_event_property
(
    event_id     BIGINT       NOT NULL,
    mapped_key   VARCHAR(254) NOT NULL,
    mapped_value VARCHAR(1024),
    PRIMARY KEY (event_id, mapped_key),
    FOREIGN KEY (event_id) REFERENCES logging_event (event_id) ON UPDATE CASCADE ON DELETE CASCADE
) charset = utf8mb4;

CREATE TABLE logging_event_exception
(
    event_id   BIGINT       NOT NULL,
    i          SMALLINT     NOT NULL,
    trace_line VARCHAR(254) NOT NULL,
    PRIMARY KEY (event_id, i),
    FOREIGN KEY (event_id) REFERENCES logging_event (event_id) ON UPDATE CASCADE ON DELETE CASCADE
) charset = utf8mb4;

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

-- 2.1.2

CREATE INDEX logging_event_job_run_id_index
    ON logging_event (job_run_id);

-- 2.3.0

create table t_job_run_database_submission
(
    pk_job_run_database_submission_id     integer auto_increment not null primary key,
    fk_job_id                             integer                not null references t_job (pk_job_id) on delete cascade on update cascade,
    created_at                            timestamp              not null,
    updated_at                            timestamp              not null,
    executed_at                           timestamp,
    job_run_database_submission_arguments json                   not null
) charset = utf8mb4;

-- 2.4.0

create table t_k8s_jobs
(
    pk_k8s_jobs_id integer auto_increment not null primary key,
    fk_job_run_id  integer                not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    k8s_job_name   text                   not null,
    created_at     timestamp              not null,
    updated_at     timestamp              not null
) charset = utf8mb4;

-- 2.4.1

CREATE TABLE t_job_run_state_transition
(
    pk_job_run_state_transition_id INT auto_increment NOT NULL PRIMARY KEY,
    fk_job_run_id                  INT                NOT NULL REFERENCES t_job_run (pk_job_run_id) ON DELETE CASCADE ON UPDATE CASCADE,
    state                          TEXT               NOT NULL,
    transitioned_at                TIMESTAMP          NOT NULL,
    index (transitioned_at)
);
