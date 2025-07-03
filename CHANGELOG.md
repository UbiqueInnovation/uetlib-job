# CHANGELOG

## 1.3.5

### Fixes

- Added `-job` suffix to dedicated k8s-job `app`-label to avoid selectors for the main
  app to also affect the job pods.

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
    - `/v1/status/**` specific `SecurityFilterChain` and `JwtDecoder` moved to Uetlib. This requires
      implementing
      projects to remove the `JwtDecoder` and `securityMatcher("/v1/status/**")` from their security
      config, e.g.:
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
    - Additionally, you have to add a separate filter chain for the actuator endpoints. The
      following three filter
      chains suffice for the minimal job module security config with no additional exposed
      endpoints:
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

