insert into t_job (clazz, created_at, updated_at)
values ('ch.ubique.backend.job.testjob.TestJob', now() - interval '10 hours', now())
returning (pk_job_id);

insert
into t_job_run(fk_job_id, job_version, started_at, finished_at, job_run_arguments, runner_key, created_at, updated_at)
values (1, 0, now() - interval '8 hours', null, '{}', 'CURRENT', now() - interval '9 hours',
        now() - interval '9 hours'),
       (1, 0, now() - interval '7 hours', null, '{}', 'CURRENT', now() - interval '8 hours',
        now() - interval '6 hours'),
       (1, 0, now() - interval '6 hours', now() - interval '2 hours', '{}', 'CURRENT', now() - interval '7 hours',
        now() - interval '4 hours'),
       (1, 0, now() - interval '5 hours', now() - interval '1 hours', '{}', 'CURRENT', now() - interval '6 hours',
        now() - interval '4 hours');

insert into t_job_run_state_transition(fk_job_run_id, state, transitioned_at)
values (1, 'SCHEDULED', now() - interval '8 hours'),
       (2, 'SCHEDULED', now() - interval '7 hours'),
       (2, 'BUSY', now() - interval '6 hours'),
       (3, 'SCHEDULED', now() - interval '5 hours'),
       (3, 'BUSY', now() - interval '4 hours'),
       (3, 'DONE', now() - interval '3 hours'),
       (4, 'SCHEDULED', now() - interval '4 hours'),
       (4, 'BUSY', now() - interval '3 hours'),
       (4, 'ERROR', now() - interval '2 hours');