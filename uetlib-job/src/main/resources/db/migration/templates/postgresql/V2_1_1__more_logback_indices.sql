CREATE INDEX logging_event_property_mapped_value_job_run_id_index
    ON logging_event_property (mapped_value)
    WHERE logging_event_property.mapped_key = 'jobRunId';
