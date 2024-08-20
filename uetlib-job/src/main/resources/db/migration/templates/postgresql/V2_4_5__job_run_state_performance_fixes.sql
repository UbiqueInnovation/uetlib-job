create index idx_job_run_started_at on t_job_run (started_at);
create index idx_job_run_state_transition_state on t_job_run_state_transition (state);
create index idx_job_run_state_transition_transitioned_at_desc on t_job_run_state_transition (transitioned_at DESC);

drop view if exists v_job_run_latest_state;

create view v_job_run_latest_state as
select r.*,
       (select state
        from t_job_run_state_transition t
        where t.fk_job_run_id = r.pk_job_run_id
        order by t.transitioned_at desc
        limit 1) as latest_state
from t_job_run r;