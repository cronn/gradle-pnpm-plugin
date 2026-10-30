package de.cronn.pnpm.internal.check

import de.cronn.pnpm.PrettierExtension
import de.cronn.pnpm.internal.ToolConfigFiles
import de.cronn.pnpm.task.PrettierTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/** The Prettier tasks of a pnpm package. */
internal class PrettierTasks(target: Project, prettier: PrettierExtension) :
  CheckTasks<PrettierTask, PrettierExtension>(
    target,
    prettier,
    PrettierTask::class.java,
    ToolConfigFiles.PRETTIER,
    INCLUDES,
  ) {

  override fun configureExtension() {
    // A pattern of the plugin rather than of the project easily matches nothing.
    extension.noErrorOnUnmatchedPattern.convention(true)
  }

  override fun configureTask(task: PrettierTask) {
    task.ignoreUnknown.convention(extension.ignoreUnknown)
    task.noErrorOnUnmatchedPattern.convention(extension.noErrorOnUnmatchedPattern)
    task.cache.convention(extension.cache)
    task.cacheLocation.convention(extension.cacheLocation)
  }

  override fun registerCheckTask(): TaskProvider<PrettierTask> =
    registerPredefinedTask(
      name = "prettierCheck",
      description = "Checks the formatting of the sources with Prettier",
    ) { task ->
      task.check.set(true)
    }

  override fun registerFixTask(): TaskProvider<PrettierTask> =
    registerPredefinedTask(
      name = "prettierFix",
      description = "Reformats the sources with Prettier",
    ) { task ->
      task.write.set(true)
      task.listDifferent.set(true)
    }

  companion object {
    val INCLUDES: List<String> = listOf(*BASE_INCLUDES, "*.json", "*.md")
  }
}
