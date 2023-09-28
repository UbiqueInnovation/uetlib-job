CREATE INDEX logging_event_property_mapped_key_index
    ON logging_event_property (mapped_key);

CREATE INDEX logging_event_property_mapped_value_index
    ON logging_event_property (mapped_value);

-- Remove logs where job run was already deleted
DELETE
FROM logging_event
WHERE event_id IN
      (SELECT event_id
       FROM logging_event_property
       WHERE mapped_key = 'jobRunId'
         AND mapped_value NOT IN (SELECT cast(pk_job_run_id AS varchar) FROM t_job_run));