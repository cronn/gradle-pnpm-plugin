---
"gradle-pnpm-plugin": patch
---

`TypescriptTask` passes its `configFile` as the last argument instead of `--project` when `build` is enabled

Closes #87.
