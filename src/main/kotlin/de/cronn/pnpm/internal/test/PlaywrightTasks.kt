package de.cronn.pnpm.internal.test

import de.cronn.pnpm.PlaywrightExtension
import de.cronn.pnpm.internal.ToolConfigFiles
import de.cronn.pnpm.task.PlaywrightInstallTask
import de.cronn.pnpm.task.PlaywrightTask
import java.io.File
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * The Playwright tasks of a pnpm package: the suite itself and the browser download it needs.
 *
 * [lockfile] is passed in rather than derived here, so that no provider created by this class
 * captures the project it belongs to.
 */
internal class PlaywrightTasks(
  target: Project,
  private val playwright: PlaywrightExtension,
  private val lockfile: File?,
) : TestTasks<PlaywrightTask>(target, playwright, PlaywrightTask::class.java, INCLUDES, EXCLUDES) {

  fun registerAll(): RegisteredTestTasks {
    configureInstallTasks()
    val install = registerInstallTask()
    val registered = register()
    // Locals again, so the configuration captures the property and not this registrar.
    val installBrowsers = playwright.installBrowsers
    target.tasks.withType(PlaywrightTask::class.java).configureEach { task ->
      // An empty list is no dependency at all, so switching installBrowsers off after the plugin
      // was applied still drops the edge.
      task.dependsOn(installBrowsers.map { wanted -> if (wanted) listOf(install) else emptyList() })
    }
    return registered
  }

  override fun configureTask(task: PlaywrightTask) {
    val projectDirectory = target.layout.projectDirectory
    val buildDirectory = target.layout.buildDirectory

    task.configFiles.convention(
      ToolConfigFiles.PLAYWRIGHT.map { name -> projectDirectory.file(name) }
        .filter { it.asFile.isFile }
    )
    task.outputDirectory.convention(
      playwright.outputDirectory.orElse(buildDirectory.dir("playwright/test-results"))
    )
    task.reportDirectory.convention(
      playwright.reportDirectory.orElse(buildDirectory.dir("reports/playwright"))
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

  override fun registerTestTask(): TaskProvider<PlaywrightTask> =
    registerTestTask(name = TEST_TASK_NAME, description = "Runs the Playwright test suite")

  /** Applies to every browser download task, the ones a build script registers included. */
  private fun configureInstallTasks() {
    val enabled = playwright.enabled
    val browsers = playwright.browsers
    val withDependencies = playwright.installSystemDependencies
    val stamp = target.layout.buildDirectory.file("playwright/install.stamp")
    val lockfile = this.lockfile?.let { target.objects.fileProperty().fileValue(it) }

    target.tasks.withType(PlaywrightInstallTask::class.java).configureEach { task ->
      task.group = TASK_GROUP
      task.browsers.convention(browsers)
      task.withDependencies.convention(withDependencies)
      task.stampFile.convention(stamp)
      if (lockfile != null) {
        task.lockfile.convention(lockfile)
      }
      task.onlyIf("the tool is enabled") { enabled.get() }
      // The stamp is what gives the task an output to be up to date about; the browsers themselves
      // land in a cache outside the project. Written in an action rather than in the task class, so
      // that a task a build script registers gets it too.
      task.doLast("write the install stamp") { candidate ->
        val file = (candidate as PlaywrightInstallTask).stampFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText("playwright install completed\n")
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

    /** The group the browser download sits in; the suite itself is a verification task. */
    private const val TASK_GROUP: String = "pnpm"

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
