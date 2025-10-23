drop view v_job_run_latest_state;

alter table t_job_run
    add column version_tag varchar(32) not null default 'unknown';

alter table t_job_run
    drop column job_version;

CREATE VIEW v_job_run_latest_state AS
SELECT tjr.*,
       (SELECT state
        FROM t_job_run_state_transition tjrs
        WHERE tjrs.fk_job_run_id = tjr.pk_job_run_id
        ORDER BY tjrs.transitioned_at DESC, tjrs.pk_job_run_state_transition_id DESC
        LIMIT 1) AS latest_state
from t_job_run tjr;