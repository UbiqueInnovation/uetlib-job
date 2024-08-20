CREATE TABLE t_job_run_state_transition
(
    pk_job_run_state_transition_id INT auto_increment NOT NULL PRIMARY KEY,
    fk_job_run_id                  INT                NOT NULL REFERENCES t_job_run (pk_job_run_id) ON DELETE CASCADE ON UPDATE CASCADE,
    state                          TEXT               NOT NULL,
    transitioned_at                TIMESTAMP          NOT NULL,
    index (transitioned_at)
);

INSERT INTO t_job_run_state_transition (fk_job_run_id, state, transitioned_at)
SELECT tjr.pk_job_run_id, tjr.state, tjr.updated_at
FROM t_job_run tjr;

ALTER TABLE t_job_run
    DROP COLUMN state;

CREATE VIEW v_job_run_latest_state AS
WITH tjrs_latest AS (SELECT fk_job_run_id,
                            MAX(transitioned_at) AS latest_pk_job_run_state_transition_id
                     FROM t_job_run_state_transition
                     GROUP BY fk_job_run_id)
SELECT tjr.*, tjrs.state as latest_state
FROM t_job_run tjr
         JOIN tjrs_latest ON tjr.pk_job_run_id = tjrs_latest.fk_job_run_id
         JOIN t_job_run_state_transition tjrs ON tjrs.fk_job_run_id = tjr.pk_job_run_id
    AND tjrs.transitioned_at = tjrs_latest.latest_pk_job_run_state_transition_id;
