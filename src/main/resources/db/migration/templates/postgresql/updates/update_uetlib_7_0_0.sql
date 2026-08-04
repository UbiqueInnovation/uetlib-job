ALTER TABLE t_job
    ADD COLUMN priority INTEGER NOT NULL DEFAULT 0;

CREATE TABLE t_job_run_queue
(
    pk_job_run_queue_id INTEGER     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_job_id           INTEGER     NOT NULL REFERENCES t_job (pk_job_id) ON DELETE CASCADE,
    version_tag         TEXT        NOT NULL,
    priority            INTEGER,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    queue_state         VARCHAR(8)  NOT NULL,

    fk_job_run_id       INTEGER REFERENCES t_job_run (pk_job_run_id) ON DELETE CASCADE,
    runner              TEXT,
    heartbeat_at        TIMESTAMPTZ,

    taints              JSONB       NOT NULL,
    job_run_arguments   JSONB       NOT NULL
);
CREATE INDEX idx_job_run_queue_job_id ON t_job_run_queue (fk_job_id);

CREATE OR REPLACE FUNCTION notify_on_job_run_queue_insert()
    RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    PERFORM pg_notify(
            'new_job_queued_run',
            NEW.fk_job_id::text || '|' || NEW.pk_job_run_queue_id::text
            );
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trigger_notify_on_job_run_queue_insert
    AFTER INSERT
    ON t_job_run_queue
    FOR EACH ROW
EXECUTE FUNCTION notify_on_job_run_queue_insert();
