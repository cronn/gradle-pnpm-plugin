---
"gradle-pnpm-plugin": minor
---

**Breaking Change**: pnpm is no longer resolved from `PATH`. The plugin always provisions the pinned pnpm version unless
`pnpm.executable` is set.
