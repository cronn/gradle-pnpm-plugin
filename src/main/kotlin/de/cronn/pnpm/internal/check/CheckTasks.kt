package de.cronn.pnpm.internal.check

import de.cronn.pnpm.internal.ToolTasks
import de.cronn.pnpm.internal.extension.PnpmSourceExtension
import de.cronn.pnpm.internal.task.PnpmSourceTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * The tasks of one Node tool that takes part in `check` and `fix`.
 *
 * A tool contributes a task type, an extension and a subclass of this class that names its tasks
 * and their arguments. Adding a tool means adding those three and one entry to the list in
 * [PnpmCheckTasks][de.cronn.pnpm.internal.PnpmCheckTasks].
 */
internal abstract class CheckTasks<T : PnpmSourceTask, E : PnpmSourceExtension>(
  target: Project,
  extension: E,
  taskType: Class<T>,
  configFileNames: List<String>,
  defaultIncludes: List<String>,
) : ToolTasks<T, E>(target, extension, taskType, configFileNames, defaultIncludes, emptyList()) {

  /**
   * Configures every task of this tool, and registers the predefined ones when [discovered] says
   * the project is configured for the tool. `null` means the tool contributes none.
   */
  fun register(discovered: Boolean): RegisteredCheckTasks? {
    configureTasks()
    if (!discovered) {
      return null
    }
    return RegisteredCheckTasks(check = registerCheckTask(), fix = registerFixTask())
  }

  /** The task of this tool that takes part in `check`. */
  protected abstract fun registerCheckTask(): TaskProvider<T>

  /** The task of this tool that takes part in `fix`, if it has one. */
  protected open fun registerFixTask(): TaskProvider<T>? = null

  protected fun registerPredefinedTask(
    name: String,
    description: String,
    configure: (T) -> Unit = {},
  ): TaskProvider<T> =
    registerTask(name, description) { task ->
      configure(task)
      // The task declares no output location, which would otherwise keep it from ever being up to
      // date. This holds for a fix task as well: its inputs are fingerprinted before it rewrites
      // them, so it is only skipped when the sources are exactly what it saw on its last run, and
      // runs once more after it changed a file.
      task.outputs.upToDateWhen { true }
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
