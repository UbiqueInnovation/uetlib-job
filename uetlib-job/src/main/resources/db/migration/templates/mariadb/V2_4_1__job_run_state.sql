CREATE TABLE t_job_run_state_transition
(
    pk_job_run_state_transition_id INT auto_increment NOT NULL PRIMARY KEY,
    fk_job_run_id                  INT                NOT NULL REFERENCES t_job_run (pk_job_run_id) ON DELETE CASCADE ON UPDATE CASCADE,
    state                          TEXT               NOT NULL,
    transitioned_at                TIMESTAMP          NOT NULL,
    index (transitioned_at),
    index (state(10))
);

INSERT INTO t_job_run_state_transition (fk_job_run_id, state, transitioned_at)
SELECT tjr.pk_job_run_id, tjr.state, tjr.updated_at
FROM t_job_run tjr;

ALTER TABLE t_job_run
    DROP COLUMN state;

CREATE VIEW v_job_run_latest_state AS
SELECT tjr.*,
       (SELECT state
        FROM t_job_run_state_transition tjrs
        WHERE tjrs.fk_job_run_id = tjr.pk_job_run_id
        ORDER BY tjrs.transitioned_at DESC
        LIMIT 1) AS latest_state
from t_job_run tjr;

create index idx_started_at on t_job_run (started_at);
