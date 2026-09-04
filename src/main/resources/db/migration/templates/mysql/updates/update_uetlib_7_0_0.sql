CREATE TABLE t_job_run_queue
(
    pk_job_run_queue_id INTEGER    AUTO_INCREMENT NOT NULL PRIMARY KEY,
    fk_job_id           INTEGER    NOT NULL REFERENCES t_job (pk_job_id) ON DELETE CASCADE,
    version_tag         TEXT       NOT NULL,
    priority            INTEGER,
    created_at          TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    queue_state         VARCHAR(32) NOT NULL,

    fk_job_run_id       INTEGER REFERENCES t_job_run (pk_job_run_id) ON DELETE CASCADE,
    runner              TEXT,

    taints              JSON       NOT NULL,
    job_run_arguments   JSON       NOT NULL,
    INDEX (fk_job_id),
    INDEX (queue_state),
    INDEX (fk_job_run_id)
) charset = utf8mb4;
