create table t_job_run_state_transition
(
    pk_job_run_state_transition_id integer generated always as identity primary key,
    fk_job_run_id                  integer                  not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    state                          text                     not null,
    transitioned_at                timestamp with time zone not null
);

insert into t_job_run_state_transition(fk_job_run_id, state, transitioned_at)
select tjr.pk_job_run_id, tjr.state, tjr.updated_at
from t_job_run tjr;

alter table t_job_run
    drop column state;

create index idx_started_at on t_job_run (started_at);
create index idx_state on t_job_run_state_transition (state);
create index idx_transitioned_at on t_job_run_state_transition (transitioned_at);

create view v_job_run_latest_state as
select r.*,
       (select state
        from t_job_run_state_transition t
        where t.fk_job_run_id = r.pk_job_run_id
        order by t.transitioned_at desc
           limit 1) as latest_state
from t_job_run r;