package de.cronn.pnpm.internal.tool

import de.cronn.pnpm.PrettierExtension
import de.cronn.pnpm.task.PrettierTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/** The Prettier tasks of a pnpm package. */
internal class PrettierTasks(target: Project, extension: PrettierExtension) :
  ToolTasks<PrettierTask>(target, extension, PrettierTask::class.java, INCLUDES) {

  override fun registerCheckTask(): TaskProvider<PrettierTask> =
    registerToolTask(
      name = "prettierCheck",
      description = "Checks the formatting of the sources with Prettier",
      arguments = listOf("--check"),
    )

  override fun registerFixTask(): TaskProvider<PrettierTask> =
    registerToolTask(
      name = "prettierFix",
      description = "Reformats the sources with Prettier",
      arguments = listOf("--write", "--list-different"),
      mutatesSources = true,
    )

  companion object {
    val INCLUDES: List<String> = listOf(*BASE_INCLUDES, "*.json", "*.md")
  }
}
