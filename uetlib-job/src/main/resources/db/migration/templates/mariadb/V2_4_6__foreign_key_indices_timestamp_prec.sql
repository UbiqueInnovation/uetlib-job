ALTER TABLE logging_event
    ADD CONSTRAINT fk_job_run_id
        FOREIGN KEY (job_run_id) REFERENCES t_job_run (pk_job_run_id)
            ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE logging_event
    ADD INDEX (timestmp);
ALTER TABLE logging_event_exception
    ADD INDEX (i);

ALTER TABLE t_job_run_state_transition
    MODIFY transitioned_at TIMESTAMP(3);