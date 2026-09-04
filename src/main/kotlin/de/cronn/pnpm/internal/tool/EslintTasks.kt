package de.cronn.pnpm.internal.tool

import de.cronn.pnpm.EslintExtension
import de.cronn.pnpm.task.EslintTask
import de.cronn.pnpm.task.TypescriptTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/** The ESLint tasks of a pnpm package. */
internal class EslintTasks(target: Project, extension: EslintExtension) :
  ToolTasks<EslintTask>(target, extension, EslintTask::class.java, INCLUDES) {

  /**
   * ESLint reports the type errors `tsc` would report as well, so type checking runs first to get
   * the better message. This holds for the tasks of a build script too, not just the predefined
   * ones.
   */
  override fun configureTask(task: EslintTask) {
    task.dependsOn(target.tasks.withType(TypescriptTask::class.java))
  }

  override fun registerCheckTask(): TaskProvider<EslintTask> =
    registerToolTask(
      name = "eslintCheck",
      description = "Checks the sources with ESLint",
      arguments = listOf("--max-warnings=0"),
    )

  override fun registerFixTask(): TaskProvider<EslintTask> =
    registerToolTask(
      name = "eslintFix",
      description = "Applies the automatic fixes of ESLint to the sources",
      arguments = listOf("--max-warnings=0", "--fix"),
      mutatesSources = true,
    )

  companion object {
    val INCLUDES: List<String> = listOf(*BASE_INCLUDES)
  }
}
