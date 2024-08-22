-- Logging tables
alter table logging_event add constraint fk_job_run_id foreign key (job_run_id) references t_job_run (pk_job_run_id) on delete cascade on update cascade;
create index if not exists fk_idx_logging_event_job_run_id on logging_event (job_run_id);
create index if not exists idx_logging_event_timestmp on logging_event (timestmp);

create index if not exists fk_idx_logging_event_exception_event_id on logging_event_exception (event_id);
create index if not exists idx_logging_event_exception_i on logging_event_exception (i);

create index if not exists fk_idx_logging_event_property_event_id on logging_event_property (event_id);


-- Job tables
create index if not exists fk_idx_job_properties_job_id on t_job_properties (fk_job_id);

create index if not exists fk_idx_job_run_job_id on t_job_run (fk_job_id);

create index if not exists fk_idx_job_run_database_submission_job_id on t_job_run_database_submission (fk_job_id);

create index if not exists fk_idx_job_run_input_job_run_id on t_job_run_input (fk_job_run_id);

create index if not exists fk_idx_job_run_state_transition_job_run_id on t_job_run_state_transition (fk_job_run_id);

create index if not exists fk_idx_job_trigger_job_id on t_job_trigger (fk_job_id);

create index if not exists fk_idx_k8s_jobs_job_run_id on t_k8s_jobs (fk_job_run_id);

-- Recreate view, with additional ordering by PK
create view v_job_run_latest_state as
select r.*,
       (select state
        from t_job_run_state_transition t
        where t.fk_job_run_id = r.pk_job_run_id
        order by t.transitioned_at desc, t.pk_job_run_state_transition_id desc
        limit 1) as latest_state
from t_job_run r;