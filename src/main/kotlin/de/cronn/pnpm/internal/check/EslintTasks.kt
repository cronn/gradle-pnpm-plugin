package de.cronn.pnpm.internal.check

import de.cronn.pnpm.EslintExtension
import de.cronn.pnpm.internal.ToolConfigFiles
import de.cronn.pnpm.task.EslintTask
import de.cronn.pnpm.task.TypescriptTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/** The ESLint tasks of a pnpm package. */
internal class EslintTasks(target: Project, eslint: EslintExtension) :
  CheckTasks<EslintTask, EslintExtension>(
    target,
    eslint,
    EslintTask::class.java,
    ToolConfigFiles.ESLINT,
    INCLUDES,
  ) {

  override fun configureExtension() {
    // A lint warning is worth fixing as much as an error, so it fails the task.
    extension.maxWarnings.convention(0)
    // A pattern of the plugin rather than of the project easily matches nothing.
    extension.noErrorOnUnmatchedPattern.convention(true)
  }

  /**
   * ESLint reports the type errors `tsc` would report as well, so type checking runs first to get
   * the better message. This holds for the tasks of a build script too, not just the predefined
   * ones.
   */
  override fun configureTask(task: EslintTask) {
    task.dependsOn(target.tasks.withType(TypescriptTask::class.java))
    task.maxWarnings.convention(extension.maxWarnings)
    task.noErrorOnUnmatchedPattern.convention(extension.noErrorOnUnmatchedPattern)
    task.cache.convention(extension.cache)
    task.cacheLocation.convention(extension.cacheLocation)
    task.suppressionsLocation.convention(extension.suppressionsLocation)
    task.passOnNoPatterns.convention(extension.passOnNoPatterns)
  }

  override fun registerCheckTask(): TaskProvider<EslintTask> =
    registerPredefinedTask(
      name = "eslintCheck",
      description = "Checks the sources with ESLint",
    )

  override fun registerFixTask(): TaskProvider<EslintTask> =
    registerPredefinedTask(
      name = "eslintFix",
      description = "Applies the automatic fixes of ESLint to the sources",
    ) { task ->
      task.fix.set(true)
    }

  companion object {
    val INCLUDES: List<String> = listOf(*BASE_INCLUDES)
  }
}
