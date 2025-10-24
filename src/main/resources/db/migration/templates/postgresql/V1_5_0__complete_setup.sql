create table t_job
(
    pk_job_id  integer generated always as identity primary key,
    clazz      text                     not null,
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
    PRIMARY KEY (fk_job_id, key)
);

create table t_job_run
(
    pk_job_run_id     integer generated always as identity primary key,
    fk_job_id         integer                  not null references t_job (pk_job_id) on delete cascade on update cascade,
    version_tag       varchar                  not null,
    runner_key        text                     not null,
    trigger_type      varchar,
    created_at        timestamp with time zone not null,
    updated_at        timestamp with time zone not null,
    started_at        timestamp with time zone not null,
    finished_at       timestamp with time zone,
    job_run_arguments jsonb
);

create index idx_finished_at on t_job_run (finished_at);

create table t_job_run_input
(
    pk_job_run_input_id integer generated always as identity primary key,
    fk_job_run_id       integer                  not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    input_name          text                     not null,
    input_hash          text                     not null,
    created_at          timestamp with time zone not null,
    updated_at          timestamp with time zone not null
);

create table t_logging_event
(
    pk_logging_event_id integer generated always as identity primary key,
    fk_job_run_id       integer                  not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    timestamp           timestamp with time zone not null,
    formatted_message   text                     not null
);

create index idx_logging_event_job_run_id_timestamp on t_logging_event (fk_job_run_id, timestamp asc);

CREATE TABLE t_job_trigger
(
    pk_job_trigger_id  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_job_id          INTEGER                  NOT NULL REFERENCES t_job (pk_job_id) ON DELETE CASCADE ON UPDATE CASCADE,
    type               TEXT                     NOT NULL,
    trigger_definition JSONB                    NOT NULL,
    created_at         timestamp with time zone not null,
    updated_at         timestamp with time zone not null
);

CREATE INDEX idx_type ON t_job_trigger (type);
CREATE INDEX idx_job_id ON t_job_trigger (fk_job_id);

create table t_job_run_database_submission
(
    pk_job_run_database_submission_id     integer generated always as identity primary key,
    fk_job_id                             integer                  not null references t_job (pk_job_id) on delete cascade on update cascade,
    created_at                            timestamp with time zone not null,
    updated_at                            timestamp with time zone not null,
    executed_at                           timestamp with time zone,
    job_run_database_submission_arguments jsonb                    not null
);


create table t_k8s_jobs
(
    pk_k8s_jobs_id integer generated always as identity primary key,
    fk_job_run_id  integer                  not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    k8s_job_name   text                     not null,
    created_at     timestamp with time zone not null,
    updated_at     timestamp with time zone not null
);

create table t_job_run_state_transition
(
    pk_job_run_state_transition_id integer generated always as identity primary key,
    fk_job_run_id                  integer                  not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    state                          text                     not null,
    transitioned_at                timestamp with time zone not null
);

create index idx_job_run_started_at on t_job_run (started_at);
create index idx_job_run_state_transition_state on t_job_run_state_transition (state);
create index idx_job_run_state_transition_transitioned_at on t_job_run_state_transition (transitioned_at);
create index idx_job_run_state_transition_transitioned_at_desc on t_job_run_state_transition (transitioned_at DESC);

create view v_job_run_latest_state as
select r.*,
       (select state
        from t_job_run_state_transition t
        where t.fk_job_run_id = r.pk_job_run_id
        order by t.transitioned_at desc, t.pk_job_run_state_transition_id desc
           limit 1) as latest_state
from t_job_run r;

create index fk_idx_job_properties_job_id on t_job_properties (fk_job_id);
create index fk_idx_job_run_job_id on t_job_run (fk_job_id);
create index fk_idx_job_run_database_submission_job_id on t_job_run_database_submission (fk_job_id);
create index fk_idx_job_run_input_job_run_id on t_job_run_input (fk_job_run_id);
create index fk_idx_job_run_state_transition_job_run_id on t_job_run_state_transition (fk_job_run_id);
create index fk_idx_job_trigger_job_id on t_job_trigger (fk_job_id);
create index fk_idx_k8s_jobs_job_run_id on t_k8s_jobs (fk_job_run_id);
