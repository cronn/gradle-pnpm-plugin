---
"gradle-pnpm-plugin": minor
---

Pass the `arguments` of every tool task ahead of the options the task sets through its properties

A task type extending `PnpmTask` now adds its arguments by overriding `contribute(CommandLine)` instead of `buildArguments()` or `commandArguments()`:

```kotlin
abstract class AngularBuildTask : PnpmExecTask() {
  @get:Input abstract val configuration: Property<String>

  init {
    command.convention("ng")
  }

  override val subcommand: String = "build"

  override fun contribute(commandLine: CommandLine) {
    super.contribute(commandLine)
    commandLine.option("--configuration", configuration, joined = true)
  }
}
```
