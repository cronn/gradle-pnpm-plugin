---
"gradle-pnpm-plugin": minor
---

Every pnpm task now takes the workspace's `pnpm-lock.yaml` as a task input, so upgrading a tool's pinned version reruns the
tasks that depend on it instead of reporting them up to date.
