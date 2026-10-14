---
"gradle-pnpm-plugin": minor
---

**Breaking Change**: The `arguments` of `VitestRunTask` and `PlaywrightTestTask` no longer include the `run` and `test`
subcommands, which are now always passed by the tasks themselves. Remove them from custom `arguments`:

```kotlin
tasks.register<VitestRunTask>("vitestUnitTest") {
  arguments = listOf("src")
}
```
