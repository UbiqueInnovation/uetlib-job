alter table t_job_run
    add column latest_state text;

update t_job_run r
set latest_state = (select state
                    from t_job_run_state_transition t
                    where t.fk_job_run_id = r.pk_job_run_id
                    order by transitioned_at desc
                    limit 1)
where latest_state is null;

alter table t_job_run
    alter column latest_state set not null;

create index idx_latest_state on t_job_run (latest_state);

drop view v_job_run_latest_state;

create index idx_job_run_job_id_job_run_id on t_job_run (fk_job_id, pk_job_run_id desc);

create view v_latest_job_run as
select distinct on (fk_job_id) *
from t_job_run
order by fk_job_id, pk_job_run_id desc;

create view v_latest_finished_job_run as
select distinct on (fk_job_id) *
from t_job_run
where finished_at is not null
order by fk_job_id, pk_job_run_id desc;