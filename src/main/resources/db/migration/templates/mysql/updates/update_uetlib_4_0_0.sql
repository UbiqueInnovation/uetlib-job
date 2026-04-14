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
    modify column latest_state text not null;

create index idx_latest_state on t_job_run (latest_state);

drop view v_job_run_latest_state;

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