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

  final override fun buildArguments(): List<String> =
    listOf("exec", command.get()) + commandArguments()

  /**
   * The arguments of [command], which subclasses assemble from whatever they configure the binary
   * with. Overriding this rather than [buildArguments] is what keeps every subclass on the same
   * `pnpm exec <command>` prefix.
   */
  protected open fun commandArguments(): List<String> = arguments.get()
}
