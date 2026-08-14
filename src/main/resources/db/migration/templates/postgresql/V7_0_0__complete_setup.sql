create table t_job
(
    pk_job_id  integer generated always as identity primary key,
    clazz      text                     not null,
    enabled    boolean                  not null default true,
    priority   integer                  not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);


create table t_job_properties
(
    fk_job_id  integer                  not null references t_job (pk_job_id) on delete cascade on update cascade,
    key        text                     not null,
    value      text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    primary key (fk_job_id, key)
);
create index idx_job_properties_job_id on t_job_properties (fk_job_id);


create table t_job_run
(
    pk_job_run_id     integer generated always as identity primary key,
    fk_job_id         integer                  not null references t_job (pk_job_id) on delete cascade on update cascade,
    version_tag       text                     not null,
    runner_key        text                     not null,
    trigger_type      text,
    created_at        timestamp with time zone not null,
    updated_at        timestamp with time zone not null,
    started_at        timestamp with time zone not null,
    finished_at       timestamp with time zone,
    latest_state      text                     not null,
    job_run_arguments jsonb
);
create index idx_job_run_job_id on t_job_run (fk_job_id);
create index job_run_idx_latest_state on t_job_run (latest_state);
create index idx_job_run_started_at on t_job_run (started_at);
create index job_run_idx_finished_at on t_job_run (finished_at);
create index idx_job_run_job_id_job_run_id on t_job_run (fk_job_id, pk_job_run_id desc);


create table t_job_run_input
(
    pk_job_run_input_id integer generated always as identity primary key,
    fk_job_run_id       integer                  not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    input_name          text                     not null,
    input_hash          text                     not null,
    created_at          timestamp with time zone not null,
    updated_at          timestamp with time zone not null
);
create index fk_idx_job_run_input_job_run_id on t_job_run_input (fk_job_run_id);


create table t_logging_event
(
    pk_logging_event_id integer generated always as identity primary key,
    fk_job_run_id       integer                  not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    timestamp           timestamp with time zone not null,
    formatted_message   text                     not null
);
create index idx_logging_event_job_run_id_timestamp on t_logging_event (fk_job_run_id, timestamp asc);


create table t_job_trigger
(
    pk_job_trigger_id  integer generated always as identity primary key,
    fk_job_id          integer                  not null references t_job (pk_job_id) on delete cascade on update cascade,
    type               text                     not null,
    trigger_definition jsonb                    not null,
    created_at         timestamp with time zone not null,
    updated_at         timestamp with time zone not null
);
create index fk_idx_job_trigger_job_id on t_job_trigger (fk_job_id);
create index idx_type on t_job_trigger (type);
create index idx_job_id on t_job_trigger (fk_job_id);


create table t_job_run_database_submission
(
    pk_job_run_database_submission_id     integer generated always as identity primary key,
    fk_job_id                             integer                  not null references t_job (pk_job_id) on delete cascade on update cascade,
    created_at                            timestamp with time zone not null,
    updated_at                            timestamp with time zone not null,
    executed_at                           timestamp with time zone,
    job_run_database_submission_arguments jsonb                    not null
);
create index fk_idx_job_run_database_submission_job_id on t_job_run_database_submission (fk_job_id);


create table t_k8s_jobs
(
    pk_k8s_jobs_id integer generated always as identity primary key,
    fk_job_run_id  integer                  not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    k8s_job_name   text                     not null,
    created_at     timestamp with time zone not null,
    updated_at     timestamp with time zone not null
);
create index fk_idx_k8s_jobs_job_run_id on t_k8s_jobs (fk_job_run_id);


create table t_job_run_state_transition
(
    pk_job_run_state_transition_id integer generated always as identity primary key,
    fk_job_run_id                  integer                  not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    state                          text                     not null,
    transitioned_at                timestamp with time zone not null
);
create index idx_job_run_state_transition_job_run_id on t_job_run_state_transition (fk_job_run_id);
create index idx_job_run_state_transition_state on t_job_run_state_transition (state);
create index idx_job_run_state_transition_transitioned_at_desc on t_job_run_state_transition (transitioned_at desc);

create view v_latest_job_run as
select distinct on (fk_job_id) *
from t_job_run
order by fk_job_id, pk_job_run_id desc;

create view v_latest_finished_job_run as
select distinct on (fk_job_id) *
from t_job_run
where finished_at is not null
order by fk_job_id, pk_job_run_id desc;

CREATE TABLE t_job_run_queue
(
    pk_job_run_queue_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_job_id           INTEGER     NOT NULL REFERENCES t_job (pk_job_id) ON DELETE CASCADE,
    version_tag         TEXT        NOT NULL,
    priority            INTEGER,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    queue_state         VARCHAR(32) NOT NULL,

    fk_job_run_id       INTEGER REFERENCES t_job_run (pk_job_run_id) ON DELETE CASCADE,
    runner              TEXT,
    heartbeat_at        TIMESTAMPTZ,

    taints              JSONB       NOT NULL,
    job_run_arguments   JSONB       NOT NULL
);
CREATE INDEX idx_job_run_queue_job_id ON t_job_run_queue (fk_job_id);

CREATE OR REPLACE FUNCTION notify_on_job_run_queue_insert()
    RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    PERFORM pg_notify(
            'new_job_queued_run',
            NEW.fk_job_id::text || '|' || NEW.pk_job_run_queue_id::text
            );
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trigger_notify_on_job_run_queue_insert
    AFTER INSERT
    ON t_job_run_queue
    FOR EACH ROW
EXECUTE FUNCTION notify_on_job_run_queue_insert();
