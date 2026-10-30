package de.cronn.pnpm.task

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault

/** Runs `pnpm exec <command>`, which executes a binary provided by a workspace dependency. */
@DisableCachingByDefault(
  because =
    "Runs an arbitrary workspace binary; its effects are not fully described by declared outputs.",
)
public abstract class PnpmExecTask : PnpmTask() {

  /** The binary to execute, for example `eslint`. */
  @get:Input public abstract val command: Property<String>

  /**
   * The subcommand of [command] this task runs, such as the `run` of `vitest run`, owned by the
   * concrete task type. `null` for a binary that is invoked without one.
   */
  @get:Internal protected open val subcommand: String? = null

  override fun contribute(commandLine: CommandLine) {
    commandLine.head("exec", command.get())
    subcommand?.let { commandLine.head(it) }
    super.contribute(commandLine)
  }
}
