---
"gradle-pnpm-plugin": minor
---

**Breaking Change**: `reportDirectory` of the `vitest` extension and `VitestRunTask` is renamed to `coverageReportsDirectory`, matching the `--coverage.reportsDirectory` option of Vitest it is passed as:

```kotlin
vitest {
  coverageReportsDirectory = layout.buildDirectory.dir("reports/coverage")
}
```

`VitestRunTask` also accepts it as the `--coverageReportsDirectory` command line option.
