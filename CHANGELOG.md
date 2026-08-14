# CHANGELOG

## 7.0.0

### New Features

- **Job dispatch is now queue-based.** Triggers no longer run a job directly - they insert a row
  into a new `t_job_run_queue`, and instances poll/claim/execute matching rows (every 5s +
  NOTIFY/LISTEN for PG for lower latency). Claiming is version-pinned: an instance only ever claims
  a row enqueued by its own exact build version, so a staggered/rolling release can't have one
  version pick up work meant for another. See `SCHEDULING.md` for the full picture.
- **Running job runs maintain a heartbeat** on their queue row - covers in-process runs, dedicated
  `--job` pods, and Kubernetes-dispatched jobs (pod status + log activity). A run whose heartbeat
  goes stale is marked `UNFINISHED` and its queue row removed.
- **A queued run nothing can claim** (e.g. no currently running instance matches its version or
  taints) is marked `CANCELLED` after `QUEUE_TIMEOUT` (default 30m) rather than left `QUEUED`
  forever, and purged after `CANCELLED_QUEUE_RETENTION` (default 7 days) unless acknowledged sooner.
  Distinct from a `JobRun`'s own `CANCELLED` state - this means the run was never picked up at all.
  Acknowledge (delete) immediately via `POST /queue/{jobQueuedRunId}/acknowledge` or `POST
  /queue/acknowledge-cancelled` (all).
- **`PUT /queue/{jobQueuedRunId}/priority`** reorders a still-queued run.
- **Dedicated `--job` runs are now also recorded in the queue as `RUNNING`**, so a job's own trigger
  firing again while it's already running doesn't churn its queue row.
- New job properties configuring the above (all optional, all with defaults that keep the queue
  working out of the box): `JOB_RUN_TAINTS`, `ALLOW_MULTIPLE_QUEUED_RUNS`, `CLAIM_TIMEOUT`,
  `HEARTBEAT_TIMEOUT`, `QUEUE_TIMEOUT`, `CANCELLED_QUEUE_RETENTION`.

### Fixes

- `@EnableJobs`'s "`ch.uetlib` is always included" doc claim wasn't actually implemented -
  now true. May add `CleanupJob`/`HealthCheckJob` for projects not already listing that package
  explicitly.

### Breaking Changes

- Database migration required: run `update_uetlib_7_0_0.sql` (adds `t_job_run_queue`, `t_job.priority`,
  and, on PostgreSQL, a `NOTIFY` trigger).
- New required property `ch.uetlib.job.runner.instance.name` - a unique identifier per
  running instance, used to track claims. Example: '${CLUSTER_NAME}-primary'
- A Job Application's primary instance (only one per job module, over all clusters!) must have a profile
  `job-primary-triggers`, this is the only instance that queues new job runs according to the configured triggers. All
  other instances can pick up these runs from the queue and must not have this profile.
- `POST /job/{id}/start` no longer returns the job's execution result, only whether it was
  successfully *queued*. Response body changed from `JobSubmissionResult` to the new sealed
  `QueueSubmissionResult`:
  ```diff
  - { "status": "SUBMITTED", "runnerKey": "CURRENT", "jobRunId": 42 }
  + { "queuedRun": { "jobQueuedRunId": 7, "jobId": 3, "queueState": "QUEUED", ... } }
  ```
  A failed request now returns `400` with `{ "reason": "..." }` (`QueueSubmissionFailure`).
- `PgChannelListenerRegistry` is no longer a single autowired bean; direct injections of it now
  need `@Qualifier("jobTriggerPgChannelListenerRegistry")` or
  `@Qualifier("jobQueuePgChannelListenerRegistry")` (see below).

### Necessary Adjustments in Projects

- Set `ch.uetlib.job.runner.instance.name` for every deployed instance.
- Apps with **more than one datasource**: the job library needs to know which datasource its own
  queue table lives on, separately from the datasource job-defined `PG_LISTEN` triggers listen on.
  Supply a bean (only for whichever one the ambient/primary datasource is wrong for,
  `PgListenJobTriggerDataSourceProvider` or `JobQueueDataSourceProvider`):
  ```java
  @Bean
  JobQueueDataSourceProvider jobQueueDataSourceProvider(final JdbcConnectionDetails jobDbConnectionDetails) {
      return () -> propertiesFor(jobDbConnectionDetails); // e.g. via DataSourceProperties setters
  }
  ```
  Single-datasource apps need no changes here; both default to the ambient datasource.

## 6.0.2

- Deprecate `ch.ubique.backend.job.interface.secret` in configuration metadata in favor of
  `ch.uetlib.job.interface.secret`. Keep the legacy property as a fallback, rather than using its name as a literal
  secret.

- Remove legacy backend database migration templates and their obsolete license notice.
- Correct job API Javadoc and formatting.

## 6.0.1

- Rename uetlib configuration keys and property references to `ch.uetlib.*`, including Spring bindings, `@Value`,
  conditions, scheduling expressions and configuration files. Migrate external deployment configuration as well.

- Move Java packages and class references from `ch.ubique.uetlib` to `ch.uetlib`.

### Breaking changes

- Move Java packages from `ch.ubique.backend` to `ch.uetlib`, including class-name resources. The combined OpenRewrite
  migration also renames known Spring configuration keys to `ch.uetlib.*`.
- Remove the inherited Ubique Artifactory repository and deployment destination; private consumers must configure their
  own registry.
- Add Apache-2.0 licensing for original code, preserve third-party licenses, and add public project metadata and
  contribution/security guidance.

- Move Maven coordinates from `ch.ubique:uetlib-*` to `ch.uetlib:uetlib-*`.
- Align uetlib parent, BOM and library references with the migration releases.

### Migration

After the migration releases and `uetlib-rewrite` are published, run this from the consuming project, then review the
diff and run its tests:

```sh
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run \
  -Drewrite.recipeArtifactCoordinates=ch.uetlib:uetlib-rewrite:RELEASE \
  -Drewrite.activeRecipes=ch.uetlib.MigrateToPublicUetlib
```

The recipe updates Maven coordinates/versions, Java references and known class-name resources. Review application-owned
packages under the old namespace, Kotlin, reflection patterns, native-access module flags and persisted class names
separately. Use `:dryRun` instead of `:run` to preview a patch.

## 5.2.2

- Fix some `FutureReturnValueIgnored` warnings and catch some possible bugs while at it

## 5.2.1

- Fix exception in `JobControllerService#getJobProperties` when a job has duplicate property keys. This was undefined
  behavior but leads to runtime crashes when jobs are not configured correctly, so logs at `warn` level.

## 5.2.0

- `BaseStateJob`: Added support for customizing the behavior when the latest state could not be computed.

## 5.1.1

- Fix a non-null usage
- uetlib versions updated
- Keep file watchers running after job submission failures, such as temporary database outages,
  so later files are processed without restarting the application.

## Fixes

- `@Valid` on container elements, not wrappers
- Application Args support additional data
- Fix documentation: `@EnableJobs` does not include `ch.ubique.backend` by default
- Fix `@NotBlank` not valid on container

## 5.0.7

- latest uetlib parent
- version updates

## 5.0.6

### Fixes

- Fix `RunnerGroup` deserialization without `allowedJobs`
- Fix flaky `JobDBAppenderTest.testAppendPutsToQueue` by avoiding async background scheduler execution during tests

## 5.0.5

### Fixes

- Fix `SerializableMatchResult.fromMatcher` crashing with `StringIndexOutOfBoundsException` when the regex contains
  non-participating capturing groups (e.g. optional groups or unmatched alternation branches)
- Fix `SerializableMatchResult.group(int)` to use precomputed group values instead of recomputing via
  `text.substring()`, which also broke with lookbehind captures
- Remove unused `text` field from `SerializableMatchResult`

## 5.0.4

- uetlib versions updated
- updated actions to not run tests twice for sonar

## 5.0.3

- Fix `k8sJobName` nullability and NPE

## 5.0.2

- Fix `JobApplication` startup

## 5.0.1

### Breaking Changes

- `BaseStateJob#runnerForProcessing` takes `T latestState` as parameter instead of none
- null-marked

## 4.3.0

- null-marked
- update uetlib and github actions version

## 4.2.1

- `@Valid` on containers is deprecated

## 4.2.0

- When submission fails for a `BaseStateJob`, the new state is now set to `SCHEDULING_FAILED` instead of `SCHEDULED` to
  avoid starving the other Jobs in the runner group. `SCHEDULING_FAILED` is a newly introduces `JobRunState`.

## 4.1.2

### Improvements

- `JobSubmissionService` now only uses minimal bean dependencies.

## 4.1.1

### Improvements

- Latest uetlib versions

## Improvements

- `PgListenTriggerService` now requires a Bean `PgListenDataSourceProvider`, e.g.,
  `() -> dataSourceProperties`. A default that exposes the only available `DataSourceProperties`
  bean is provided if the user does not specify one.

## 4.0.6

### Fixes

- Fixed compatibility issue with newest `uetlib-job` version
- Improved timeout handling for `PgListenListener`

## 4.0.5

### Fixes

- `PG_LISTEN` triggers are now resilient to silently dropped (half-open) `LISTEN` connections: the connection enables
  TCP keepalive and a bounded socket timeout, and a periodic keepalive query forces a round-trip so a dead connection
  surfaces as an error and the listener reconnects instead of going permanently deaf. Added a `StatusCheck`
  (`PgListenListenerCheck`) that reports via the monitoring endpoint when a channel is no longer being actively
  monitored.

## 4.0.4

### Fixes

- Avoid retries for log DB insertion on non-transient errors, avoid spamming sentry indefinitely

## 4.0.3

### Fixes

- Filter illegal null character `0x00` in log messages before inserting them into the database.

## 4.0.2

### Fixes

- Fixed logging recursion in JobDBAppender

## 4.0.1

### Improvements

- Bulk insert of job run inputs, instead of one by one.

## 4.0.0

### Breaking Changes

- Altered `t_job_run` table to include `latest_state` for better performance and easier querying.

  When migrating, please apply `update_uetlib_4_0_0.sql`.

## 3.3.1

### Improvements

- Kubernetes support can be locallly disabled by setting the profile `disable-k8s`
- JDBC template used by this module was renamed to `jobJdbcTemplate` to make it easier for projects to configure this
  specific JDBC template

### Fixes

- JobService initialized `runnerGroups` to early.

## 3.3.0

- Major Performance improvements

## 3.2.2

- Made new `JobDBAppender` more robust.

## 3.2.1

- Improved performance of `JobDBAppender` by inserting log entries in batches.

## 3.2.0

- Drop `uetlib-spring-boot-validation` dependency in favor of `uetlib-spring-boot@v2.1.0`

## 3.1.0

### New Features

- Pulled `PgListenTrigger` from `2.2.0` into `3.x` release.

## 3.0.0

### Improvements

- Spring Boot 4 compatibility
- Add `CLEANUP_DAYS_TO_KEEP_SKIPPED` to clean skipped runs faster

### Breaking Changes

- Require all usages of `JobRunArguments.additionalData()` to implement the new interface
  `JobRunAdditionalData`

## 2.2.0

### New Features

- Added `PgListenTrigger` when running on PostgreSQL.

## 2.1.2

### Improvement

- Disabled Jobs are ignored for the `JobLastRunMaxAgeCheck`

## 2.1.1

### Fixes

- Job triggers can be updated again, not just added or removed

## 2.1.0

### Improvements

- Add response validation to `UetlibJobStatusController`. Configurable via
  ```properties
  ch.ubique.backend.spring.validation.response.fail.action=throw
  ch.ubique.backend.spring.validation.response.fail.log-level=error
  ```
  Defaults to `log` and `warn`, respectively.

## 2.0.1

### Fixes

- fix version of `io:kubernetes:client-java:24.0.0`

## 2.0.0

Retreat 2025 Edition

### New Features

- Added an option to kill jobs automatically after they exceeded their run-time or schedule-time limit. The behavior can
  be controlled using the job properties.
- Job Info endpoint now includes runner groups.
- Job runs persist the version tag of the image that was used to run the job.
- Added new `ACKNOWLEDGED` state and endpoint to acknowledge for job runs.
- Added new `CANCELLED` state to differentiate between a run that was willfully canceled from a run that could not
  complete (i.e. `UNFINISHED`).
- Added `RetryTrigger` to enable retrying failed job runs with a constant backoff.
- `JobStateCheck` can use new `StateCheckStrategy` for monitoring checks. The strategies can be set as JobProperty for
  each Job.
- Added option to disable jobs. A disabled job will never be triggered and cannot be started unless enabled again.

### Database migrations

- You will need to add the database migration `update_uetlib_2_0_0.sql` to your project.

### Breaking Changes

- Removed `Job.version` in favor of version tag.

## 1.4.6

### Fixes

- Job triggers can be updated again, not just added or removed

## 1.4.4

- Downgraded some log messages to debug

## Changes

- `JobRunStorage`: use built-in gzip handling instead of explicit gzip

## 1.4.2

### New Features

- Added `JobRunInputManager.save(Map)` method, to save multiple job run inputs at once.

## 1.4.1

### New Features

- It is now possible to download a zip archive containing all files from a Job Run.

## 1.4.0

### New Features

- The job module can now watch state changes and logs of Kubernetes jobs. This is particularly useful for running
  arbitrary images as jobs.

### Improvements

- If a dedicated Kubernetes job fails, a non 0 exit code is used.

## 1.3.5

### Fixes

- Added `-job` suffix to dedicated k8s-job `app`-label to avoid selectors for the main app to also affect the job pods.

## 1.3.4

### Improvements

- More concise logging

## 1.3.3

### Fixes

- Fixed log security over websockets

## 1.3.2

### Improvements

- Check level of all job checks can be configured using spring properties.

## 1.3.0

### Improvements

- Latest uetlib dependencies with Spring Boot 3.5 support

## 1.2.3

### Breaking Changes

- Status logs are now served from base path `/v1/status` to be protected under the security config.
    - `/v1/status/**` specific `SecurityFilterChain` and `JwtDecoder` moved to Uetlib. This requires implementing
      projects to remove the `JwtDecoder` and `securityMatcher("/v1/status/**")` from their security config, e.g.:
      ```diff
      - http.securityMatcher("/v1/status/**")
      -       .authorizeHttpRequests(
      -             authorize ->
      -                   authorize
      -                         .requestMatchers(HttpMethod.OPTIONS, statusEndpointPattern)
      -                         .permitAll()
      -                         .requestMatchers("/v1/status/**")
      -                         .authenticated())
      -       .oauth2ResourceServer(customizer -> customizer.jwt(withDefaults()));
      ```
      ```diff
      - @Bean
      - public JwtDecoder jwtDecoder(
      -         @Value("${ch.ubique.backend.job.interface.secret}") final String secret) {
      -     return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(), "HmacSHA256"))
      -             .macAlgorithm(MacAlgorithm.HS256)
      -             .build();
      - }
      ```
    - Additionally, you have to add a separate filter chain for the actuator endpoints. The following three filter
      chains suffice for the minimal job module security config with no additional exposed endpoints:
      ```
      /** Probably keep this one as-is to have the swagger documentation adequately protected */
      @Bean
      public SecurityFilterChain swaggerFilterChain(final HttpSecurity http) throws Exception {
          http.securityMatcher("/api-docs/**", "/api-docs*")
                  .authorizeHttpRequests(
                          customizer ->
                                  customizer
                                          .requestMatchers("/api-docs/**", "/api-docs*")
                                          .hasRole(SWAGGER))
                  .httpBasic(withDefaults());

          return http.build();
      }

      /**
       * Probably keep this one as-is to have only the necessary actuator endpoints exposed. The
       * management server usually runs on a separate port anyway, but still ;)
       */
      @Bean
      public SecurityFilterChain actuatorFilterChain(final HttpSecurity http) throws Exception {
          // EndpointRequest.toAnyEndpoint() matches all /actuator endpoints
          http.securityMatcher(EndpointRequest.toAnyEndpoint())
                  .authorizeHttpRequests(
                          customizer ->
                                  customizer
                                          .requestMatchers(
                                                  HttpMethod.GET,
                                                  "/actuator/health",
                                                  "/actuator/health/**",
                                                  "/actuator/prometheus")
                                          .permitAll());

          return http.build();
      }

      @Bean
      public SecurityFilterChain fallbackFilterChain(final HttpSecurity http) throws Exception {
          http.csrf(AbstractHttpConfigurer::disable);

          http.authorizeHttpRequests(
                  customizer ->
                          customizer
                                  .requestMatchers(HttpMethod.OPTIONS)
                                  .permitAll()
                                  .requestMatchers(HttpMethod.GET, "/v1/info", "/check")
                                  .permitAll()
                                  .anyRequest() // deny all others
                                  .denyAll());

          return http.build();
      }
      ```   

## 1.1.8

### Improvements

- Improved jobs not being set to `UNFINISHED` if they still produced logs recently.

## 1.1.7

### Improvements

- Jobs that produced log outputs within the last minute are never set to unfinished. This should improve the ability to
  run multiple replicas.

## 1.1.6

### Improvements

- `JobDBAppender` for exceptions does no longer throw an error for lines `>254` chars.

## 1.1.5

### Improvements

- Improved performance of reading log, by reducing number of DB queries from `n` to `1`, where `n`
  is the number of log lines.

## 1.1.4

### New Features

- File Triggers are now watched by Inotify if available.

## 1.1.3

### New Features

- Status Controller supports filtering by multiple states
- Added log endpoint to the status controller
- Added `LogService` to make logs more accessible to projects
- `JobExecutorService` returns a result

### Bugfix

- `TriggerCoordinatorService.onStartup` was called too early

## 1.1.2

- Updated `uetlib-parent` and `uetlib-storage-spring-boot` dependencies
- `JobRunArguments.additionalData` serialization and deserialization with the original object type
- Added `ReplayContext` to `JobRunArguments`

## 1.1.1

- Updated `uetlib-storage-spring-boot` dependency
- Removed `@PastOrPresent` validation from `JobRunEntity`

## 1.1.0

### New Features

- Support for job-run files. Any job-run files can be downloaded in the status interface now.
    - To use this feature, you can use the `JobRunInputManager` Bean.
    - Implement `JobRunInputProvider` in your Job if you want to customize the loading of input files.
- Disable triggers by activating the `job-disable-triggers` profile.

### Fixes

- Properly shutdown executor service in `FileTriggerService`
- Renamed DB migrations

## 1.0.0

First release. The code was moved
from [UbiqueInnovation/backend-jvm-lib](https://github.com/UbiqueInnovation/backend-jvm-lib).

