create table t_job
(
    pk_job_id  integer auto_increment not null primary key,
    clazz      text                   not null,
    enabled    boolean                not null default true,
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
    primary key (fk_job_id, `key`)
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
    latest_state      text                   not null,
    job_run_arguments json,
    index (started_at),
    index (finished_at),
    index (latest_state(10))
) charset = utf8mb4;


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
    formatted_message   text                   not null,
    index (fk_job_run_id, timestamp asc)
) charset = utf8mb4;


create table t_job_trigger
(
    pk_job_trigger_id  integer auto_increment not null primary key,
    fk_job_id          integer                not null references t_job (pk_job_id) on delete cascade on update cascade,
    type               varchar(100)           not null, -- mysql requires a limit on keys / indices, whereas mariadb does not
    trigger_definition json                   not null,
    created_at         timestamp              not null,
    updated_at         timestamp              not null,
    index (type),
    index (fk_job_id)
) charset = utf8mb4;


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


create table t_job_run_state_transition
(
    pk_job_run_state_transition_id int auto_increment not null primary key,
    fk_job_run_id                  int                not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    state                          text               not null,
    transitioned_at                timestamp(3)       not null,
    index (transitioned_at),
    index (transitioned_at desc),
    index (state(10))
) charset = utf8mb4;

create view v_latest_job_run as
select r.*
from t_job_run r
         join (select max(pk_job_run_id) as latest_job_run_pk
               from t_job_run
               group by fk_job_id) m
              on m.latest_job_run_pk = r.pk_job_run_id;

create view v_latest_finished_job_run as
select r.*
from t_job_run r
         join (select max(pk_job_run_id) as latest_job_run_pk
               from t_job_run
               where finished_at is not null
               group by fk_job_id) m
              on m.latest_job_run_pk = r.pk_job_run_id;

CREATE TABLE t_job_run_queue
(
    pk_job_run_queue_id INTEGER AUTO_INCREMENT NOT NULL PRIMARY KEY,
    fk_job_id           INTEGER                NOT NULL REFERENCES t_job (pk_job_id) ON DELETE CASCADE,
    version_tag         TEXT                   NOT NULL,
    priority            INTEGER,
    run_now             BOOLEAN                NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP              NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP              NOT NULL DEFAULT CURRENT_TIMESTAMP,

    queue_state         VARCHAR(32)            NOT NULL,

    fk_job_run_id       INTEGER REFERENCES t_job_run (pk_job_run_id) ON DELETE CASCADE,
    runner              TEXT,

    taints              JSON                   NOT NULL,
    job_run_arguments   JSON                   NOT NULL,
    INDEX (fk_job_id),
    INDEX (queue_state),
    INDEX (fk_job_run_id)
) charset = utf8mb4;

CREATE TABLE t_job_daemon
(
    pk_job_daemon_id   INTEGER      AUTO_INCREMENT NOT NULL PRIMARY KEY,
    daemon_name       VARCHAR(255) NOT NULL,
    version            TEXT         NOT NULL,
    taints             JSON         NOT NULL,
    primary_candidate  BOOLEAN      NOT NULL,
    trigger_count      INTEGER,
    -- The primary lease: 1 on the one instance holding it, NULL on all others. UNIQUE (NULLs never
    -- collide) makes a second holder impossible.
    primary_slot       INTEGER CHECK (primary_slot = 1),
    primary_since      TIMESTAMP    NULL,
    started_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,
    UNIQUE (daemon_name),
    UNIQUE (primary_slot)
) charset = utf8mb4;
