package de.cronn.pnpm.internal.tool

import de.cronn.pnpm.PnpmToolExtension
import de.cronn.pnpm.task.PnpmToolTask
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * The tasks of one Node tool.
 *
 * A tool contributes a task type, an extension and a subclass of this class that names its tasks
 * and their arguments; everything the tools have in common lives here. Adding a tool means adding
 * those three and one entry to the list in [PnpmToolTasks][de.cronn.pnpm.internal.PnpmToolTasks].
 */
internal abstract class ToolTasks<T : PnpmToolTask>(
  protected val target: Project,
  val extension: PnpmToolExtension,
  private val taskType: Class<T>,
  private val defaultIncludes: List<String>,
) {

  fun register(): RegisteredToolTasks {
    // A value, not a convention: adding to a property that only has a convention discards it,
    // which would make the additive includes(...) method replace the defaults instead.
    extension.includes.set(defaultIncludes)

    // A local, so that the onlyIf spec captures the extension instead of this registrar, which
    // holds the Project and would fail to serialize into the configuration cache.
    val enabled = extension.enabled

    target.tasks.withType(taskType).configureEach { task ->
      task.group = LifecycleBasePlugin.VERIFICATION_GROUP
      task.sources.convention(sourceFiles())
      task.extraArguments.convention(extension.extraArguments)
      task.onlyIf("the tool is enabled") { enabled.get() }
      configureTask(task)
    }

    return RegisteredToolTasks(extension, check = registerCheckTask(), fix = registerFixTask())
  }

  /** The task of this tool that takes part in `check`. */
  protected abstract fun registerCheckTask(): TaskProvider<T>

  /** The task of this tool that takes part in `fix`, if it has one. */
  protected open fun registerFixTask(): TaskProvider<T>? = null

  /** Applied to every task of this tool, the ones registered by a build script included. */
  protected open fun configureTask(task: T) {}

  protected fun registerToolTask(
    name: String,
    description: String,
    arguments: List<String>,
    mutatesSources: Boolean = false,
  ): TaskProvider<T> =
    target.tasks.register(name, taskType) { task ->
      task.description = description
      task.arguments.set(arguments)
      // A fix task rewrites its own inputs, so its result is not described by an output location
      // Gradle could compare: it always runs, the way the other in-place maintenance tasks do.
      task.outputs.upToDateWhen { !mutatesSources }
    }

  /**
   * The files the tool inspects, resolved when a task is configured so that `prettier { ... }`
   * blocks anywhere in the build script are taken into account.
   */
  private fun sourceFiles(): FileTree {
    val includes = extension.includes.get()
    val excludes = extension.excludes.get()
    target.logger.debug(
      "Sources of {}: including {}, excluding {}",
      target.path,
      includes,
      excludes,
    )
    return target.fileTree(target.projectDir) { tree ->
      tree.include(includes)
      tree.exclude(excludes)
    }
  }

  companion object {
    /** The TypeScript sources every tool looks at. */
    val BASE_INCLUDES: Array<String> = arrayOf("*.ts", "src/**/*.ts", "src/**/*.tsx")
  }
}

/** What a tool contributes to the `check` and `fix` lifecycle tasks. */
internal class RegisteredToolTasks(
  val extension: PnpmToolExtension,
  val check: TaskProvider<out PnpmToolTask>,
  val fix: TaskProvider<out PnpmToolTask>?,
)
