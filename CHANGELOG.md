# CHANGELOG

## 1.2.0

### Improvements

- Status logs are now served from base path `/v1/status` to be protected under the security config.
  - `/v1/status/**` specific `SecurityFilterChain` and `JwtDecoder` moved to Uetlib. This requires implementing projects to remove the `JwtDecoder` and `securityMatcher("/v1/status/**")` from their security config, e.g.:
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

## 1.1.8

### Improvements

- Improved jobs not being set to `UNFINISHED` if they still produced logs recently.

## 1.1.7

### Improvements

- Jobs that produced log outputs within the last minute are never set to unfinished.
  This should improve the ability to run multiple replicas.

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
    - Implement `JobRunInputProvider` in your Job if you want to customize the loading of input
      files.
- Disable triggers by activating the `job-disable-triggers` profile.

### Fixes

- Properly shutdown executor service in `FileTriggerService`
- Renamed DB migrations

## 1.0.0

First release. The code was moved
from [UbiqueInnovation/backend-jvm-lib](https://github.com/UbiqueInnovation/backend-jvm-lib).

