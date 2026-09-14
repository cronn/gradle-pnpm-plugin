---
"gradle-pnpm-plugin": minor
---

Add Vitest support. A project containing a `vitest.config.*` gets a `vitestTest` task and a `vitest` extension configuring it.

Unlike `playwrightTest`, `vitestTest` takes part in `check` as well as `test`, so `build` runs the unit suite: a unit suite is fast and reaches nothing the build does not start. `vitest.alwaysRerun` therefore defaults to `false`, so an unchanged source tree skips the suite.

`VitestTask` accepts `--coverage` and `--update`. The coverage report goes to `vitest.reportDirectory`, `build/reports/vitest` by default, which the task declares as an output.
