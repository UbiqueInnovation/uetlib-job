-- 1.0.0

create table t_job
(
    pk_job_id integer auto_increment not null primary key,
    clazz     text                   not null
);

create table t_job_properties
(
    fk_job_id integer      not null references t_job (pk_job_id) on delete cascade on update cascade,
    `key`     varchar(100) not null,
    value     text,
    PRIMARY KEY (fk_job_id, `key`)
);

create table t_job_run
(
    pk_job_run_id     integer auto_increment not null primary key,
    fk_job_id         integer                not null references t_job (pk_job_id) on delete cascade on update cascade,
    job_version       integer                not null,
    state             text                   not null,
    runner_key        text                   not null,
    started_at        timestamp              not null,
    finished_at       timestamp,
    job_run_arguments json
);

create index idx_finished_at on t_job_run (finished_at);

create table t_job_run_input
(
    pk_job_run_input_id integer auto_increment not null primary key,
    fk_job_run_id       integer                not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    input_name          varchar(255)           not null,
    input_hash          varchar(128)           not null
);

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
);

CREATE TABLE logging_event_property
(
    event_id     BIGINT       NOT NULL,
    mapped_key   VARCHAR(254) NOT NULL,
    mapped_value VARCHAR(1024),
    PRIMARY KEY (event_id, mapped_key),
    FOREIGN KEY (event_id) REFERENCES logging_event (event_id) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE logging_event_exception
(
    event_id   BIGINT       NOT NULL,
    i          SMALLINT     NOT NULL,
    trace_line VARCHAR(254) NOT NULL,
    PRIMARY KEY (event_id, i),
    FOREIGN KEY (event_id) REFERENCES logging_event (event_id) ON UPDATE CASCADE ON DELETE CASCADE
);

-- 2.0.0

CREATE TABLE t_job_trigger
(
    pk_job_trigger_id  INTEGER auto_increment NOT NULL PRIMARY KEY,
    fk_job_id          INTEGER                NOT NULL REFERENCES t_job (pk_job_id) ON DELETE CASCADE ON UPDATE CASCADE,
    type               VARCHAR(100)           NOT NULL, -- MySQL requires a limit on keys / indices
    trigger_definition JSON                   NOT NULL
);

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
    submitted_at                          timestamp              not null,
    executed_at                           timestamp,
    job_run_database_submission_arguments json                   not null
);
