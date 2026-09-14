package de.cronn.pnpm.task

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault

/**
 * Runs a Node program through `pnpm exec node`.
 *
 * The program runs on the Node version pnpm resolves for the workspace -- the one pinned in the
 * `devEngines.runtime` field of the `package.json` -- rather than on whatever `node` happens to be
 * on the `PATH`.
 */
@DisableCachingByDefault(
  because =
    "Runs an arbitrary Node program; its effects are not fully described by declared outputs."
)
public abstract class NodeTask : PnpmExecTask() {

  /** The program to run, handed to Node as an absolute path. */
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val entryPoint: RegularFileProperty

  /**
   * Options for Node itself, placed before [entryPoint]. Node stops reading its own options at the
   * first non-option argument, so these cannot go into [arguments], which the program receives.
   */
  @get:Input public abstract val nodeOptions: ListProperty<String>

  init {
    command.convention("node")
  }

  override fun commandArguments(): List<String> =
    nodeOptions.get() + entryPoint.get().asFile.absolutePath + arguments.get()
}
