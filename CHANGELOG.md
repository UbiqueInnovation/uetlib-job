# CHANGELOG

## 1.1.2

### New Features

- Status Controller supports filtering by multiple states
- Added log endpoint to the status controller
- Added `LogService` to make logs more accessible to projects
- `JobExecutorService` returns a result

### Bugfix

- `TriggerCoordinatorService.onStartup` was called too early

## 1.1.1

- Updated `uetlib` dependencies

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

