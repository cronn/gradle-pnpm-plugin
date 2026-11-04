package de.cronn.pnpm.internal.test

import de.cronn.pnpm.internal.ToolTasks
import de.cronn.pnpm.internal.extension.PnpmTestExtension
import de.cronn.pnpm.internal.task.PnpmTestTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * The tasks of one Node test tool.
 *
 * A tool contributes a task type, an extension and a subclass of this class that names its tasks.
 * Adding a test tool means adding those three and registering it in
 * [PnpmToolTasks][de.cronn.pnpm.internal.PnpmToolTasks], which decides whether its suite joins
 * `test`.
 */
internal abstract class TestTasks<T : PnpmTestTask, E : PnpmTestExtension>(
  target: Project,
  extension: E,
  taskType: Class<T>,
  configFileNames: List<String>,
  defaultIncludes: List<String>,
  defaultExcludes: List<String>,
) :
  ToolTasks<T, E>(target, extension, taskType, configFileNames, defaultIncludes, defaultExcludes) {

  /**
   * Configures every task of this tool, and registers the predefined ones when the project has a
   * config file for the tool. Returns the suite, or `null` when the tool contributes none.
   */
  open fun register(): TaskProvider<T>? {
    val discovered = configureTasks()

    target.tasks.withType(taskType).configureEach { task ->
      task.alwaysRerun.convention(extension.alwaysRerun)
      // The spec must not capture anything: the configuration cache serializes it, so the decision
      // is read off the task the way PnpmSetupTask carries its own.
      task.outputs.upToDateWhen { candidate -> !(candidate as PnpmTestTask).rerunRequested() }
    }

    if (!discovered) {
      return null
    }
    return registerTestTask()
  }

  /** The task of this tool that takes part in `test`. */
  protected abstract fun registerTestTask(): TaskProvider<T>
}
