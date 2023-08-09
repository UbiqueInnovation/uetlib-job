CREATE TABLE t_job_trigger
(
    pk_job_trigger_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_job_id         INTEGER NOT NULL REFERENCES t_job (pk_job_id) ON DELETE CASCADE ON UPDATE CASCADE,
    type              VARCHAR NOT NULL,
    data              TEXT    NOT NULL
);

CREATE INDEX idx_type ON t_job_trigger (type);
CREATE INDEX idx_job_id ON t_job_trigger (fk_job_id);

INSERT INTO t_job_trigger (fk_job_id, type, data)
SELECT pk_job_id, 'CRON', concat('{"cronExpression":"', cron_expression, '"}')
FROM t_job
WHERE cron_expression IS NOT NULL;

ALTER TABLE t_job
    DROP COLUMN cron_expression;