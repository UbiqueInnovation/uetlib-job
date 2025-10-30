drop view v_job_run_latest_state;

alter table t_job_run
    add column version_tag varchar(32) not null default 'unknown';

alter table t_job_run
    add column trigger_type varchar(32);

alter table t_job_run
    drop column job_version;

CREATE VIEW v_job_run_latest_state AS
SELECT tjr.*,
       (SELECT state
        FROM t_job_run_state_transition tjrs
        WHERE tjrs.fk_job_run_id = tjr.pk_job_run_id
        ORDER BY tjrs.transitioned_at DESC, tjrs.pk_job_run_state_transition_id DESC
        LIMIT 1) AS latest_state
from t_job_run tjr;

-- simplify logging tables:

create table t_logging_event
(
    pk_logging_event_id integer auto_increment not null primary key,
    fk_job_run_id       integer                not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    timestamp           timestamp              not null,
    formatted_message   text                   not null
) charset = utf8mb4;

create index idx_logging_event_job_run_id_timestamp on t_logging_event (fk_job_run_id, timestamp asc);

begin;
insert into t_logging_event (fk_job_run_id, timestamp, formatted_message)
select job_run_id,
       from_unixtime(timestmp / 1000),
       formatted_message
from logging_event;
commit;

drop table logging_event_exception;
drop table logging_event_property;
drop table logging_event;