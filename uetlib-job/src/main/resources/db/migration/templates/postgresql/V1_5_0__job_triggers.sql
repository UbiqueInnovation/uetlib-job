CREATE TABLE t_job_trigger
(
    id     INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id INTEGER NOT NULL REFERENCES t_job (pk_job_id) ON DELETE CASCADE ON UPDATE CASCADE,
    type   VARCHAR NOT NULL,
    data   JSONB   NOT NULL
);

CREATE INDEX idx_type ON t_job_trigger (type);
CREATE INDEX idx_job_id ON t_job_trigger (job_id);

INSERT INTO t_job_trigger (job_id, type, data)
SELECT pk_job_id, 'cron', jsonb_build_object('cron_expression', cron_expression)
FROM t_job
WHERE cron_expression IS NOT NULL;

ALTER TABLE t_job
    DROP COLUMN cron_expression;