insert into t_job(clazz, enabled, created_at, updated_at)
values ('ch.uetlib.job.testjob.TestJob', true, now() - interval 11 hour, now() - interval 10 hour);

insert
into t_job_run(fk_job_id, version_tag, started_at, finished_at, job_run_arguments,
               runner_key,
               created_at, updated_at, latest_state)
values (1, '1.0.0', now() - interval 8 hour, null, '{}', 'CURRENT', now() - interval 9 hour,
        now() - interval 9 hour, 'SCHEDULED'),
       (1, '1.0.0', now() - interval 7 hour, null, '{}', 'CURRENT', now() - interval 8 hour,
        now() - interval 6 hour, 'BUSY'),
       (1, '1.0.0', now() - interval 6 hour, now() - interval 2 hour, '{}', 'CURRENT',
        now() - interval 7 hour,
        now() - interval 4 hour, 'DONE'),
       (1, '1.0.0', now() - interval 5 hour, now() - interval 1 hour, '{}', 'CURRENT',
        now() - interval 6 hour,
        now() - interval 4 hour, 'ERROR');

insert into t_job_run_state_transition(fk_job_run_id, state, transitioned_at)
values (1, 'SCHEDULED', now() - interval 8 hour),
       (2, 'SCHEDULED', now() - interval 7 hour),
       (2, 'BUSY', now() - interval 6 hour),
       (3, 'SCHEDULED', now() - interval 5 hour),
       (3, 'BUSY', now() - interval 4 hour),
       (3, 'DONE', now() - interval 3 hour),
       (4, 'SCHEDULED', now() - interval 4 hour),
       (4, 'BUSY', now() - interval 3 hour),
       (4, 'ERROR', now() - interval 2 hour);
