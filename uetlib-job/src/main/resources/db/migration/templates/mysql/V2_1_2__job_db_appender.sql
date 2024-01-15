ALTER TABLE logging_event
    ADD COLUMN job_run_id integer;

CREATE INDEX logging_event_job_run_id_index
    ON logging_event (job_run_id);

UPDATE logging_event le
    JOIN logging_event_property lep ON le.event_id = lep.event_id
SET job_run_id = CAST(lep.mapped_value as SIGNED)
WHERE lep.mapped_key = 'jobRunId'
  and lep.mapped_value is not null;

