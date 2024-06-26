with job_runs as (insert into t_job_run (fk_job_id, job_version, started_at, finished_at, runner_key)
    values (1, 1, '2023-12-19 08:59:00', '2023-12-19 09:00:00', 'CURRENT'),
           (1, 1, '2023-12-19 09:01:00', '2023-12-19 09:59:00', 'CURRENT'),
           (1, 1, '2023-12-19 09:59:00', '2023-12-19 10:00:00', 'CURRENT'),
           (1, 1, '2023-12-19 11:00:00', '2023-12-19 18:05:00', 'CURRENT'),
           (1, 1, '2023-12-19 11:01:00', '2023-12-19 11:02:00', 'CURRENT'),
           (1, 1, '2023-12-19 11:59:00', '2023-12-19 12:00:00', 'CURRENT'),
           (1, 1, '2023-12-19 12:00:00', '2023-12-19 12:01:00', 'CURRENT'),
           (1, 1, '2023-12-19 12:01:00', '2023-12-19 12:02:00', 'CURRENT'),
           (1, 1, '2023-12-19 12:59:00', '2023-12-19 12:59:30', 'CURRENT'),
           (1, 1, '2023-12-19 13:00:00', '2023-12-19 13:00:30', 'CURRENT'),
           (1, 1, '2023-12-19 13:01:00', '2023-12-19 13:58:00', 'CURRENT'),
           (1, 1, '2023-12-19 14:59:00', '2023-12-19 15:00:00', 'CURRENT'),
           (1, 1, '2023-12-19 15:00:00', '2023-12-19 15:01:00', 'CURRENT'),
           (1, 1, '2023-12-19 15:01:00', '2023-12-19 15:59:00', 'CURRENT'),
           (1, 1, '2023-12-19 15:59:00', '2023-12-19 16:00:00', 'CURRENT'),
           (1, 1, '2023-12-19 16:00:00', '2023-12-19 16:01:00', 'CURRENT'),
           (1, 1, '2023-12-19 16:01:00', '2023-12-19 16:02:30', 'CURRENT')
    returning (pk_job_run_id, started_at, finished_at))
insert
into t_job_run_state_transition(pfk_job_run_id, state, transitioned_at)
select jr.pk_job_run_id, 'BUSY', jr.started_at
from job_runs jr
union
select jr.pk_job_run_id, 'DONE', jr.finished_at
from job_runs jr;

with job_runs as (insert into t_job_run (fk_job_id, job_version, started_at, finished_at, runner_key)
    values (1, 1, '2023-12-19 10:00:00', null, 'CURRENT'),
           (1, 1, '2023-12-19 10:01:00', null, 'CURRENT'),
           (1, 1, '2023-12-19 10:59:00', null, 'CURRENT')
    returning (pk_job_run_id, started_at, finished_at))
insert
into t_job_run_state_transition(pfk_job_run_id, state, transitioned_at)
select jr.pk_job_run_id, 'BUSY', jr.started_at
from job_runs jr;

