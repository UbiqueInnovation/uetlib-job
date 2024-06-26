create table t_job_run_state_transition
(
    pk_job_run_state_transition_id integer generated always as identity primary key,
    fk_job_run_id                  integer                  not null references t_job_run (pk_job_run_id) on delete cascade on update cascade,
    state                          text                     not null,
    transitioned_at                timestamp with time zone not null
);

create index on t_job_run_state_transition (transitioned_at);

insert into t_job_run_state_transition(fk_job_run_id, state, transitioned_at)
select tjr.pk_job_run_id, tjr.state, tjr.updated_at
from t_job_run tjr;

alter table t_job_run
    drop column state;

create view v_job_run_latest_state as
select distinct on (tjr.pk_job_run_id) tjr.*, tjrs.state as latest_state
from t_job_run tjr
         join t_job_run_state_transition tjrs on tjrs.fk_job_run_id = tjr.pk_job_run_id
order by tjr.pk_job_run_id, tjrs.transitioned_at desc;