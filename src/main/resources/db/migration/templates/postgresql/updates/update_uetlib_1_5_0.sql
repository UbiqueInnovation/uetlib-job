drop view v_job_run_latest_state;

alter table t_job_run
    add column version_tag varchar not null default 'unknown';

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
