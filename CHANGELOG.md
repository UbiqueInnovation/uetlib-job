# CHANGELOG

## 1.1.0

### New Features

- Support for job-run files. Any job-run files can be downloaded in the status interface now. 
  - To use this feature, you can use the `JobRunInputManager` Bean.
  - Implement `JobRunInputProvider` in your Job if you want to customize the loading of input files. 
- Disable triggers by activating the `job-disable-triggers` profile.

## 1.0.0

First release. The code was moved
from [UbiqueInnovation/backend-jvm-lib](https://github.com/UbiqueInnovation/backend-jvm-lib).

