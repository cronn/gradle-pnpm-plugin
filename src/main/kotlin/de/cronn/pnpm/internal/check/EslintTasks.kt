package de.cronn.pnpm.internal.check

import de.cronn.pnpm.EslintExtension
import de.cronn.pnpm.internal.ToolConfigFiles
import de.cronn.pnpm.task.EslintTask
import de.cronn.pnpm.task.TypescriptTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/** The ESLint tasks of a pnpm package. */
internal class EslintTasks(target: Project, private val eslint: EslintExtension) :
  CheckTasks<EslintTask>(
    target,
    eslint,
    EslintTask::class.java,
    ToolConfigFiles.ESLINT,
    INCLUDES,
  ) {

  /**
   * ESLint reports the type errors `tsc` would report as well, so type checking runs first to get
   * the better message. This holds for the tasks of a build script too, not just the predefined
   * ones.
   */
  override fun configureTask(task: EslintTask) {
    task.dependsOn(target.tasks.withType(TypescriptTask::class.java))
    task.maxWarnings.convention(eslint.maxWarnings)
    task.noErrorOnUnmatchedPattern.convention(eslint.noErrorOnUnmatchedPattern)
    task.cache.convention(eslint.cache)
    task.cacheLocation.convention(eslint.cacheLocation)
    task.suppressionsLocation.convention(eslint.suppressionsLocation)
    task.passOnNoPatterns.convention(eslint.passOnNoPatterns)
  }

  override fun registerCheckTask(): TaskProvider<EslintTask> =
    registerTask(
      name = "eslintCheck",
      description = "Checks the sources with ESLint",
    )

  override fun registerFixTask(): TaskProvider<EslintTask> =
    registerTask(
      name = "eslintFix",
      description = "Applies the automatic fixes of ESLint to the sources",
    ) { task ->
      task.fix.set(true)
    }

  companion object {
    val INCLUDES: List<String> = listOf(*BASE_INCLUDES)
  }
}
