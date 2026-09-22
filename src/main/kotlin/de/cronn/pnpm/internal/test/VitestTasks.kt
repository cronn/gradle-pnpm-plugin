package de.cronn.pnpm.internal.test

import de.cronn.pnpm.VitestExtension
import de.cronn.pnpm.internal.ToolConfigFiles
import de.cronn.pnpm.task.VitestTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * The Vitest tasks of a pnpm package.
 *
 * Unlike Playwright, Vitest needs nothing provisioned, so the suite is all there is -- and it takes
 * part in `check`: a unit suite is fast and reaches nothing the build does not start, so there is
 * no reason to keep it out of `build`.
 */
internal class VitestTasks(target: Project, private val vitest: VitestExtension) :
  TestTasks<VitestTask>(
    target,
    vitest,
    VitestTask::class.java,
    INCLUDES,
    EXCLUDES,
    contributesToTest = true,
  ) {

  override fun configureTask(task: VitestTask) {
    val projectDirectory = target.layout.projectDirectory
    val buildDirectory = target.layout.buildDirectory

    task.configFiles.convention(
      ToolConfigFiles.VITEST.map { name -> projectDirectory.file(name) }.filter { it.asFile.isFile }
    )
    task.reportDirectory.convention(
      vitest.reportDirectory.orElse(buildDirectory.dir("reports/vitest"))
    )
  }

  override fun registerTestTask(): TaskProvider<VitestTask> =
    registerTestTask(name = TEST_TASK_NAME, description = "Runs the Vitest test suite")

  companion object {
    const val TEST_TASK_NAME: String = "vitestTest"

    /**
     * The sources the suite is a function of, anchored: an unanchored `**` pattern would walk
     * `node_modules`, which is a symlink farm of every dependency of the workspace.
     */
    val INCLUDES: List<String> = listOf("src/**/*.ts", "src/**/*.tsx")

    /** What Vitest itself writes, which must not be an input of the task writing it. */
    val EXCLUDES: List<String> = listOf("node_modules/**", "build/**", "coverage/**")
  }
}
