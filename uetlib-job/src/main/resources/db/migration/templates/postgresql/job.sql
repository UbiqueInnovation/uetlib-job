CREATE TABLE t_job_status
(
    pk_job_status_id       integer GENERATED ALWAYS AS IDENTITY,
    name            text        NOT NULL,
    clazz           text        NOT NULL,
    cron_expression varchar(20) NOT NULL,
    PRIMARY KEY (pk_job_status_id)
);

CREATE TABLE t_job_run
(
    pk_job_run_id integer GENERATED ALWAYS AS IDENTITY,
    fk_job_status_id     integer                  NOT NULL,
    state         varchar(20)              NOT NULL,
    log_output    text                     NOT NULL,
    started_at    timestamp with time zone NOT NULL,
    finished_at   timestamp with time zone,
    PRIMARY KEY (pk_job_run_id),
    FOREIGN KEY (fk_job_status_id) REFERENCES t_job_status (pk_job_status_id)
);

