ALTER TABLE t_job_run_queue
    ADD COLUMN allow_parallel_run BOOLEAN NOT NULL DEFAULT FALSE;
