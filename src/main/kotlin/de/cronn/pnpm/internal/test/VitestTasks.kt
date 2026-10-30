package de.cronn.pnpm.internal.test

import de.cronn.pnpm.VitestExtension
import de.cronn.pnpm.internal.ToolConfigFiles
import de.cronn.pnpm.task.VitestRunTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * The Vitest tasks of a pnpm package.
 *
 * Unlike Playwright, Vitest needs nothing provisioned, so the suite is all there is -- and it takes
 * part in `check`: a unit suite is fast and reaches nothing the build does not start, so there is
 * no reason to keep it out of `build`.
 */
internal class VitestTasks(target: Project, vitest: VitestExtension) :
  TestTasks<VitestRunTask, VitestExtension>(
    target,
    vitest,
    VitestRunTask::class.java,
    ToolConfigFiles.VITEST,
    INCLUDES,
    EXCLUDES,
    contributesToTest = true,
  ) {

  override fun configureExtension() {
    // A unit suite really is a function of the files it runs over -- and it takes part in `check`,
    // so skipping it on an unchanged source tree is worth having.
    extension.alwaysRerun.convention(false)
  }

  override fun configureTask(task: VitestRunTask) {
    val buildDirectory = target.layout.buildDirectory

    task.coverageReportsDirectory.convention(
      extension.coverageReportsDirectory.orElse(buildDirectory.dir("reports/vitest")),
    )
    task.coverage.convention(extension.coverage)
    task.passWithNoTests.convention(extension.passWithNoTests)
    task.dir.convention(extension.dir)
    task.outputFile.convention(extension.outputFile)
  }

  override fun registerTestTask(): TaskProvider<VitestRunTask> =
    registerTask(name = TEST_TASK_NAME, description = "Runs the Vitest test suite")

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
