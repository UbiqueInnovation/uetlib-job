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

CREATE TABLE t_job_daemon
(
    pk_job_daemon_id   INTEGER      AUTO_INCREMENT NOT NULL PRIMARY KEY,
    daemon_name       VARCHAR(255) NOT NULL,
    version            TEXT         NOT NULL,
    taints             JSON         NOT NULL,
    primary_candidate  BOOLEAN      NOT NULL,
    trigger_count      INTEGER,
    -- The primary lease: 1 on the one instance holding it, NULL on all others. UNIQUE (NULLs never
    -- collide) makes a second holder impossible.
    primary_slot       INTEGER CHECK (primary_slot = 1),
    primary_since      TIMESTAMP    NULL,
    started_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,
    UNIQUE (daemon_name),
    UNIQUE (primary_slot)
) charset = utf8mb4;
