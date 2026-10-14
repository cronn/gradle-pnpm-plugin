---
"gradle-pnpm-plugin": minor
---

**Breaking Change**: `VitestTask` is renamed to `VitestRunTask` to make the `vitest run` subcommand explicit. Update
custom tasks:

```kotlin
import de.cronn.pnpm.task.VitestRunTask

tasks.register<VitestRunTask>("vitestUnitTest")
```
