package de.cronn.pnpm.internal.check

import de.cronn.pnpm.PrettierExtension
import de.cronn.pnpm.internal.ToolConfigFiles
import de.cronn.pnpm.task.PrettierTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/** The Prettier tasks of a pnpm package. */
internal class PrettierTasks(target: Project, private val prettier: PrettierExtension) :
  CheckTasks<PrettierTask>(
    target,
    prettier,
    PrettierTask::class.java,
    ToolConfigFiles.PRETTIER,
    INCLUDES,
  ) {

  override fun configureTask(task: PrettierTask) {
    task.ignoreUnknown.convention(prettier.ignoreUnknown)
    task.noErrorOnUnmatchedPattern.convention(prettier.noErrorOnUnmatchedPattern)
    task.cache.convention(prettier.cache)
    task.cacheLocation.convention(prettier.cacheLocation)
  }

  override fun registerCheckTask(): TaskProvider<PrettierTask> =
    registerTask(
      name = "prettierCheck",
      description = "Checks the formatting of the sources with Prettier",
    ) { task ->
      task.check.set(true)
    }

  override fun registerFixTask(): TaskProvider<PrettierTask> =
    registerTask(
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
