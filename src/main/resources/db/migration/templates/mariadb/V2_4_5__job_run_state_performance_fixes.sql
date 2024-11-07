DROP VIEW IF EXISTS v_job_run_latest_state;
CREATE VIEW v_job_run_latest_state AS
SELECT tjr.*,
       (SELECT state
        FROM t_job_run_state_transition tjrs
        WHERE tjrs.fk_job_run_id = tjr.pk_job_run_id
        ORDER BY tjrs.transitioned_at DESC
        LIMIT 1) AS latest_state
FROM t_job_run tjr;

CREATE INDEX idx_job_run_started_at ON t_job_run (started_at);
CREATE INDEX idx_job_run_state_transition_state ON t_job_run_state_transition (state(10));
CREATE INDEX idx_job_run_state_transition_transitioned_at_desc ON t_job_run_state_transition (transitioned_at DESC);