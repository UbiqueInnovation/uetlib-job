ALTER TABLE logging_event
    ADD COLUMN job_run_id integer;

UPDATE logging_event le
SET job_run_id = lep.mapped_value::integer
FROM logging_event_property lep
WHERE le.event_id = lep.event_id
  and lep.mapped_key = 'jobRunId'
  and lep.mapped_value is not null;

ALTER TABLE logging_event
    ALTER COLUMN job_run_id SET NOT NULL;

CREATE INDEX logging_event_job_run_id_index
    ON logging_event (job_run_id);
