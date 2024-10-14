DELETE
FROM logging_event
WHERE job_run_id NOT IN (SELECT pk_job_run_id FROM t_job_run);
ALTER TABLE logging_event
    ADD CONSTRAINT fk_job_run_id
        FOREIGN KEY (job_run_id) REFERENCES t_job_run (pk_job_run_id)
            ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE logging_event
    ADD INDEX (timestmp);
ALTER TABLE logging_event_exception
    ADD INDEX (i);

ALTER TABLE t_job_run_state_transition
    MODIFY transitioned_at TIMESTAMP(3) null;

DROP VIEW IF EXISTS v_job_run_latest_state;
CREATE VIEW v_job_run_latest_state AS
SELECT tjr.*,
       (SELECT state
        FROM t_job_run_state_transition tjrs
        WHERE tjrs.fk_job_run_id = tjr.pk_job_run_id
        ORDER BY tjrs.transitioned_at DESC, tjrs.pk_job_run_state_transition_id DESC
        LIMIT 1) AS latest_state
FROM t_job_run tjr;