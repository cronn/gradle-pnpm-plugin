package de.cronn.pnpm.internal.check

import de.cronn.pnpm.internal.extension.PnpmSourceExtension
import de.cronn.pnpm.internal.task.PnpmSourceTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * The tasks of one Node tool.
 *
 * A tool contributes a task type, an extension and a subclass of this class that names its tasks
 * and their arguments; everything the tools have in common lives here. Adding a tool means adding
 * those three and one entry to the list in [PnpmCheckTasks][de.cronn.pnpm.internal.PnpmCheckTasks].
 */
internal abstract class CheckTasks<T : PnpmSourceTask>(
  protected val target: Project,
  val extension: PnpmSourceExtension,
  private val taskType: Class<T>,
  private val defaultIncludes: List<String>,
) {

  /**
   * Configures every task of this tool, and registers the predefined ones when [discovered] says
   * the project is configured for the tool.
   *
   * The conventions are applied whatever [discovered] is, so that a task a build script registers
   * behaves like a predefined one even in a project that has no config file for the tool. Only the
   * predefined tasks depend on the discovery; `null` means the tool contributes none.
   */
  fun register(discovered: Boolean): RegisteredCheckTasks? {
    // A value, not a convention: adding to a property that only has a convention discards it,
    // which would make the additive includes(...) method replace the defaults instead.
    extension.includes.set(defaultIncludes)
    target.logger.debug(
      "Default patterns of {} in {}: {}",
      taskType.simpleName,
      target.path,
      defaultIncludes,
    )

    // Locals, so that the task configuration captures the extension properties instead of this
    // registrar, which holds the Project and would fail to serialize into the configuration cache.
    val includes = extension.includes
    val excludes = extension.excludes

    target.tasks.withType(taskType).configureEach { task ->
      task.group = LifecycleBasePlugin.VERIFICATION_GROUP
      task.includes.convention(includes)
      task.excludes.convention(excludes)
      task.extraArguments.convention(extension.extraArguments)
      configureTask(task)
    }

    if (!discovered) {
      return null
    }

    return RegisteredCheckTasks(check = registerCheckTask(), fix = registerFixTask())
  }

  /** The task of this tool that takes part in `check`. */
  protected abstract fun registerCheckTask(): TaskProvider<T>

  /** The task of this tool that takes part in `fix`, if it has one. */
  protected open fun registerFixTask(): TaskProvider<T>? = null

  /** Applied to every task of this tool, the ones registered by a build script included. */
  protected open fun configureTask(task: T) {}

  protected fun registerTask(
    name: String,
    description: String,
    arguments: List<String> = emptyList(),
    mutatesSources: Boolean = false,
  ): TaskProvider<T> =
    target.tasks.register(name, taskType) { task ->
      task.description = description
      task.arguments.set(arguments)
      // A fix task rewrites its own inputs, so its result is not described by an output location
      // Gradle could compare: it always runs, the way the other in-place maintenance tasks do.
      task.outputs.upToDateWhen { !mutatesSources }
    }

  companion object {
    /** The TypeScript sources every tool looks at. */
    val BASE_INCLUDES: Array<String> = arrayOf("*.ts", "src/**/*.ts", "src/**/*.tsx")
  }
}

/** What a tool contributes to the `check` and `fix` lifecycle tasks. */
internal class RegisteredCheckTasks(
  val check: TaskProvider<out PnpmSourceTask>,
  /** The task taking part in `fix`, or `null` for a tool that fixes nothing, such as TypeScript. */
  val fix: TaskProvider<out PnpmSourceTask>?,
)
