CREATE TABLE t_job_trigger
(
    pk_job_trigger_id  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_job_id          INTEGER NOT NULL REFERENCES t_job (pk_job_id) ON DELETE CASCADE ON UPDATE CASCADE,
    type               TEXT    NOT NULL,
    trigger_definition JSONB   NOT NULL
);

CREATE INDEX idx_type ON t_job_trigger (type);
CREATE INDEX idx_job_id ON t_job_trigger (fk_job_id);

INSERT INTO t_job_trigger (fk_job_id, type, trigger_definition)
SELECT pk_job_id, 'CRON', concat('{"type":"CRON", "cronExpression":"', cron_expression, '"}')
FROM t_job
WHERE coalesce(trim(cron_expression), '') != '';

ALTER TABLE t_job
    DROP COLUMN cron_expression;

DELETE
FROM t_job_properties
WHERE key IN ('K8S_LIMITS_CPU', 'K8S_LIMITS_MEMORY');