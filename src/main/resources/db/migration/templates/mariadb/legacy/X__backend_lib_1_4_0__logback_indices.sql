-- Remove logs where job run was already deleted
DELETE
FROM logging_event
WHERE event_id IN
      (SELECT event_id
       FROM logging_event_property
       WHERE mapped_key = 'jobRunId'
         AND mapped_value NOT IN (SELECT cast(pk_job_run_id AS char) FROM t_job_run));