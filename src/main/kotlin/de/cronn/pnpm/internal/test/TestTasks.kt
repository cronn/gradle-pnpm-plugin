package de.cronn.pnpm.internal.test

import de.cronn.pnpm.internal.extension.PnpmTestExtension
import de.cronn.pnpm.internal.task.PnpmTestTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * The tasks of one Node test tool.
 *
 * The test-tool counterpart of [CheckTasks][de.cronn.pnpm.internal.check.CheckTasks]: a tool
 * contributes a task type, an extension and a subclass of this class that names its tasks;
 * everything the test tools have in common lives here. Adding a test tool means adding those three
 * and one entry to the list in [PnpmTestTasks][de.cronn.pnpm.internal.PnpmTestTasks].
 */
internal abstract class TestTasks<T : PnpmTestTask>(
  protected val target: Project,
  val extension: PnpmTestExtension,
  private val taskType: Class<T>,
  private val defaultIncludes: List<String>,
  private val defaultExcludes: List<String>,
) {

  fun register(): RegisteredTestTasks {
    // Values, not conventions: adding to a property that only has a convention discards it, which
    // would make the additive includes(...) and excludes(...) methods replace the defaults.
    extension.includes.set(defaultIncludes)
    extension.excludes.set(defaultExcludes)
    target.logger.debug(
      "Default patterns of {} in {}: {}, excluding {}",
      taskType.simpleName,
      target.path,
      defaultIncludes,
      defaultExcludes,
    )

    // Locals, so that the task configuration captures the extension properties instead of this
    // registrar, which holds the Project and would fail to serialize into the configuration cache.
    val enabled = extension.enabled
    val includes = extension.includes
    val excludes = extension.excludes
    val extraArguments = extension.extraArguments
    val alwaysRerun = extension.alwaysRerun

    target.tasks.withType(taskType).configureEach { task ->
      task.group = LifecycleBasePlugin.VERIFICATION_GROUP
      task.includes.convention(includes)
      task.excludes.convention(excludes)
      task.extraArguments.convention(extraArguments)
      task.alwaysRerun.convention(alwaysRerun)
      task.onlyIf("the tool is enabled") { enabled.get() }
      // The spec must not capture anything: the configuration cache serializes it, so the decision
      // is read off the task the way PnpmSetupTask carries its own.
      task.outputs.upToDateWhen { candidate -> !(candidate as PnpmTestTask).rerunRequested() }
      configureTask(task)
    }

    return RegisteredTestTasks(extension, test = registerTestTask())
  }

  /** The task of this tool that takes part in `test`. */
  protected abstract fun registerTestTask(): TaskProvider<T>

  /** Applied to every task of this tool, the ones registered by a build script included. */
  protected open fun configureTask(task: T) {}

  protected fun registerTestTask(name: String, description: String): TaskProvider<T> =
    target.tasks.register(name, taskType) { task -> task.description = description }
}

/** What a test tool contributes to the `test` lifecycle task. */
internal class RegisteredTestTasks(
  val extension: PnpmTestExtension,
  val test: TaskProvider<out PnpmTestTask>,
)
