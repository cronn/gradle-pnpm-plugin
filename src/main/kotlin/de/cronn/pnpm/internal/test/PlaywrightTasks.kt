package de.cronn.pnpm.internal.test

import de.cronn.pnpm.PlaywrightExtension
import de.cronn.pnpm.PnpmPlugin
import de.cronn.pnpm.internal.ToolConfigFiles
import de.cronn.pnpm.internal.task.PlaywrightInstallTask
import de.cronn.pnpm.internal.writeStamp
import de.cronn.pnpm.task.PlaywrightTestTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/** The Playwright tasks of a pnpm package: the suite itself and the browser download it needs. */
internal class PlaywrightTasks(target: Project, playwright: PlaywrightExtension) :
  TestTasks<PlaywrightTestTask, PlaywrightExtension>(
    target,
    playwright,
    PlaywrightTestTask::class.java,
    ToolConfigFiles.PLAYWRIGHT,
    INCLUDES,
    EXCLUDES,
  ) {

  /**
   * Registers the Playwright tasks when the project has a `playwright.config.*`.
   *
   * The conventions of both task types are applied either way, so a suite a build script registers
   * behaves like the predefined one. The browser download is a predefined task like the suite, so a
   * project without a Playwright configuration gets neither, and there is then no edge to wire.
   */
  override fun register(): RegisteredTestTasks? {
    configureInstallTasks()
    val registered = super.register()
    if (registered == null) {
      return null
    }

    val install = registerInstallTask()
    target.tasks.withType(PlaywrightTestTask::class.java).configureEach { task ->
      // An empty list is no dependency at all, so switching installBrowsers off after the plugin
      // was applied still drops the edge.
      task.dependsOn(
        extension.installBrowsers.map { wanted -> if (wanted) listOf(install) else emptyList() },
      )
    }
    return registered
  }

  override fun configureExtension() {
    extension.installBrowsers.convention(true)
    extension.installSystemDependencies.convention(false)
    // A browser suite talks to a backend, a database, a fixture server -- none of which a Gradle
    // input describes, so its inputs being unchanged is no reason to believe its result still
    // holds.
    extension.alwaysRerun.convention(true)
  }

  override fun configureTask(task: PlaywrightTestTask) {
    val buildDirectory = target.layout.buildDirectory

    task.outputDirectory.convention(
      extension.outputDirectory.orElse(buildDirectory.dir("playwright/test-results")),
    )
    task.reportDirectory.convention(
      extension.reportDirectory.orElse(buildDirectory.dir("reports/playwright")),
    )
    // The report location becomes something the task declares rather than something the Playwright
    // configuration happens to choose. PLAYWRIGHT_HTML_REPORT is the name Playwright below 1.45
    // reads; both are set so either version lands in the declared output directory.
    // locationOnly, so that the environment -- an input of this task -- does not carry the
    // dependency on the task producing reportDirectory, which is this very task.
    val report = task.reportDirectory.locationOnly.map { it.asFile.absolutePath }
    task.environment.put("PLAYWRIGHT_HTML_OUTPUT_DIR", report)
    task.environment.put("PLAYWRIGHT_HTML_REPORT", report)
  }

  override fun registerTestTask(): TaskProvider<PlaywrightTestTask> =
    registerTask(name = TEST_TASK_NAME, description = "Runs the Playwright test suite")

  /** Applies to every browser download task, the ones a build script registers included. */
  private fun configureInstallTasks() {
    target.tasks.withType(PlaywrightInstallTask::class.java).configureEach { task ->
      task.group = PnpmPlugin.TASK_GROUP
      task.browsers.convention(extension.browsers)
      task.withDependencies.convention(extension.installSystemDependencies)
      task.stampFile.convention(target.layout.buildDirectory.file("playwright/install.stamp"))
      // The stamp is what gives the task an output to be up to date about; the browsers themselves
      // land in a cache outside the project. Written in an action rather than in the task class, so
      // that a task a build script registers gets it too.
      task.doLast("write the install stamp") { candidate ->
        writeStamp(
          (candidate as PlaywrightInstallTask).stampFile.get().asFile,
          "playwright install completed\n",
        )
      }
    }
  }

  private fun registerInstallTask(): TaskProvider<PlaywrightInstallTask> =
    target.tasks.register(INSTALL_TASK_NAME, PlaywrightInstallTask::class.java) { task ->
      task.description = "Downloads the browsers Playwright drives"
    }

  companion object {
    const val TEST_TASK_NAME: String = "playwrightTest"
    const val INSTALL_TASK_NAME: String = "playwrightInstall"

    /**
     * The test sources, anchored: an unanchored `**` pattern would walk `node_modules`, which is a
     * symlink farm of every dependency of the workspace.
     */
    val INCLUDES: List<String> = listOf("tests/**/*.ts", "src/**/*.ts")

    /** What Playwright itself writes, which must not be an input of the task writing it. */
    val EXCLUDES: List<String> =
      listOf("node_modules/**", "build/**", "test-results/**", "playwright-report/**")
  }
}
