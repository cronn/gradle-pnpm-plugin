---
"gradle-pnpm-plugin": minor
---

`prettierCheck`, `prettierFix`, `eslintCheck`, `eslintFix` and `compileTypescript` now
take the tool's configuration file as a task input, so editing it reruns the task
instead of it being reported up to date.

Closes #53.
