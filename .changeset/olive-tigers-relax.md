---
"gradle-pnpm-plugin": minor
---

**Breaking:** `check` now always depends on `test`, so that a suite a build script adds to `test` also runs as part of `check`.

`playwrightTest` no longer takes part in `test`. Run `playwrightTest` directly, or add the dependency to `test` in the build script.

Closes #52.
