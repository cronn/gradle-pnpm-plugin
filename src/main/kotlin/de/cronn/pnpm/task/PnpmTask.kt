package de.cronn.pnpm.task

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

/**
 * Runs a pnpm command.
 *
 * The [executable] and [pnpmVersion] of every task of this type are configured by the
 * `de.cronn.gradle-pnpm-plugin` plugin, so subclasses and build scripts only have to provide
 * [arguments].
 */
@DisableCachingByDefault(
  because =
    "Runs an arbitrary pnpm command; its effects are not fully described by declared outputs."
)
public abstract class PnpmTask : DefaultTask() {

  @get:Inject protected abstract val execOperations: ExecOperations

  @get:Inject protected abstract val projectLayout: ProjectLayout

  /**
   * The pnpm executable to invoke. Defaults to the `pnpm` found on the `PATH`.
   *
   * This is deliberately not an input: it is an absolute, machine-specific path, which would make
   * task outputs unshareable between machines. [pnpmVersion] captures the part of the pnpm identity
   * that actually affects the result.
   */
  @get:Internal public abstract val executable: Property<String>

  /** Version of the pnpm being invoked, so that a pnpm upgrade invalidates the task. */
  @get:Input @get:Optional public abstract val pnpmVersion: Property<String>

  /** Arguments passed to pnpm, after any arguments contributed by the task type itself. */
  @get:Input public abstract val arguments: ListProperty<String>

  /** Directory pnpm is executed in. Defaults to the project directory. */
  @get:Internal public abstract val workingDirectory: DirectoryProperty

  /** Whether a non-zero pnpm exit code is tolerated. Defaults to `false`. */
  @get:Input public abstract val ignoreExitValue: Property<Boolean>

  init {
    executable.convention("pnpm")
    workingDirectory.convention(projectLayout.projectDirectory)
    ignoreExitValue.convention(false)
  }

  @TaskAction
  public fun run() {
    val commandLine = listOf(executable.get()) + buildArguments()
    val directory = workingDirectory.get().asFile
    logger.info("Running {} in {}", commandLine.joinToString(" "), directory)
    execOperations.exec { spec ->
      spec.commandLine(commandLine)
      spec.workingDir = directory
      spec.isIgnoreExitValue = ignoreExitValue.get()
    }
  }

  /** The full pnpm argument list, which subclasses prefix with their own command. */
  protected open fun buildArguments(): List<String> = arguments.get()
}
