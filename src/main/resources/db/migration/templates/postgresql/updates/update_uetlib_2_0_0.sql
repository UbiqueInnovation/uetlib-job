drop view v_job_run_latest_state;

alter table t_job_run
    add column version_tag varchar not null default 'unknown';

alter table t_job_run
    add column trigger_type varchar;

alter table t_job_run
    drop column job_version;

create view v_job_run_latest_state as
select r.*,
       (select state
        from t_job_run_state_transition t
        where t.fk_job_run_id = r.pk_job_run_id
        order by t.transitioned_at desc, t.pk_job_run_state_transition_id desc
        limit 1) as latest_state
from t_job_run r;

-- simplify logging tables:
create table t_logging_event
(
    pk_logging_event_id integer generated always as identity primary key,
    fk_job_run_id       integer                  not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    timestamp           timestamp with time zone not null,
    formatted_message   text                     not null
);

create index idx_logging_event_job_run_id_timestamp on t_logging_event (fk_job_run_id, timestamp asc);

begin;
insert into t_logging_event (fk_job_run_id, timestamp, formatted_message)
select job_run_id,
       to_timestamp(timestmp / 1000) at time zone 'UTC', -- Convert from BIGINT to timestamp with time zone
       formatted_message
from logging_event;
commit;

drop table logging_event_exception;
drop table logging_event_property;
drop table logging_event;