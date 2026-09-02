package de.cronn.pnpm.task

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.work.DisableCachingByDefault

/** Runs `pnpm exec <command>`, which executes a binary provided by a workspace dependency. */
@DisableCachingByDefault(
  because =
    "Runs an arbitrary workspace binary; its effects are not fully described by declared outputs."
)
public abstract class PnpmExecTask : PnpmTask() {

  /** The binary to execute, for example `eslint`. */
  @get:Input public abstract val command: Property<String>

  override fun buildArguments(): List<String> = listOf("exec", command.get()) + arguments.get()
}
