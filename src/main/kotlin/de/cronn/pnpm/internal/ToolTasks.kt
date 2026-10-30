package de.cronn.pnpm.internal

import de.cronn.pnpm.internal.extension.PnpmSourceExtension
import de.cronn.pnpm.internal.task.PnpmSourceTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * The tasks of one Node tool: what every tool has in common, whether it is a check or a test tool.
 */
internal abstract class ToolTasks<T : PnpmSourceTask, E : PnpmSourceExtension>(
  protected val target: Project,
  val extension: E,
  protected val taskType: Class<T>,
  private val configFileNames: List<String>,
  private val defaultIncludes: List<String>,
  private val defaultExcludes: List<String>,
) {

  /**
   * Applies the defaults of the tool to its extension, and the extension to every task of the tool,
   * including the ones a build script registers.
   *
   * Returns whether the project has a config file for the tool, which decides whether the
   * predefined tasks are registered.
   */
  protected fun configureTasks(): Boolean {
    configureExtension()

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

    val configFile = ToolConfigFiles.firstExisting(target.layout.projectDirectory, configFileNames)
    target.logger.debug(
      "Configuration file of {} in {}: {}",
      taskType.simpleName,
      target.path,
      configFile?.asFile ?: "none of ${configFileNames.joinToString(", ")}",
    )
    configFile?.let { extension.configFile.convention(it) }

    target.tasks.withType(taskType).configureEach { task ->
      task.group = LifecycleBasePlugin.VERIFICATION_GROUP
      task.includes.convention(extension.includes)
      task.excludes.convention(extension.excludes)
      task.configFile.convention(extension.configFile)
      task.extraArguments.convention(extension.extraArguments)
      configureTask(task)
    }
    return configFile != null
  }

  /** Applies the defaults of the tool-specific properties of the [extension]. */
  protected open fun configureExtension() {}

  /** Applied to every task of this tool, the ones registered by a build script included. */
  protected open fun configureTask(task: T) {}

  protected fun registerTask(
    name: String,
    description: String,
    configure: (T) -> Unit = {},
  ): TaskProvider<T> =
    target.tasks.register(name, taskType) { task ->
      task.description = description
      configure(task)
    }
}
