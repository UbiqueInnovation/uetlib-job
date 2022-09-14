CREATE TABLE t_job
(
    pk_job_id       integer GENERATED ALWAYS AS IDENTITY,
    name            text        NOT NULL,
    clazz           text        NOT NULL,
    cron_expression varchar(20) NOT NULL,
    PRIMARY KEY (pk_job_id)
);

CREATE TABLE t_job_run
(
    pk_job_run_id integer GENERATED ALWAYS AS IDENTITY,
    fk_job_id     integer                  NOT NULL,
    job_version   integer                  NOT NULL,
    state         varchar(20)              NOT NULL,
    log_output    text                     NOT NULL,
    started_at    timestamp with time zone NOT NULL,
    finished_at   timestamp with time zone,
    PRIMARY KEY (pk_job_run_id),
    FOREIGN KEY (fk_job_id) REFERENCES t_job (pk_job_id)
);

CREATE TABLE t_job_run_input
(
    pk_job_run_input_id integer GENERATED ALWAYS AS IDENTITY,
    fk_job_run_id       integer NOT NULL,
    input_name          varchar(255) NOT NULL,
    input_hash          char(128) NOT NULL,
    PRIMARY KEY (pk_job_run_input_id),
    FOREIGN KEY (fk_job_run_id) REFERENCES t_job_run (pk_job_run_id)
);