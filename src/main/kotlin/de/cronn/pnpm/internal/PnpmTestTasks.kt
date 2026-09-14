package de.cronn.pnpm.internal

import de.cronn.pnpm.PlaywrightExtension
import de.cronn.pnpm.internal.test.PlaywrightTasks
import de.cronn.pnpm.internal.test.RegisteredTestTasks
import java.io.File
import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Registers the tasks of every Node test tool of a single pnpm package and wires them into the
 * `test` lifecycle task. Each tool defines its own tasks; what is left here is what crosses tool
 * boundaries.
 *
 * `check` deliberately does not depend on `test`: an end-to-end suite is slow and usually needs a
 * server the build does not start. A build that wants it adds the edge itself.
 */
internal class PnpmTestTasks(
  private val target: Project,
  playwright: PlaywrightExtension,
  lockfile: File?,
) {

  private val playwrightTasks = PlaywrightTasks(target, playwright, lockfile)

  /**
   * Registers the tasks of every test tool the project is configured for, and wires them into
   * `test`. A tool whose config file is absent contributes no task at all.
   */
  fun register(playwright: Boolean) {
    // A further test tool is registered here and added to the list below.
    val playwrightTasks = playwrightTasks.registerAll(playwright)

    val registered = listOfNotNull(playwrightTasks)
    wireTest(registered)
  }

  /**
   * Adds the test task of every tool to `test`, which is registered unless the project already has
   * a task of that name -- the Java plugin brings its own, and a project applying both should end
   * up with one `test` running everything.
   *
   * `test` is registered whatever the project is configured for, so that a build script can always
   * name it. A project with no test tool gets one with nothing to do.
   */
  private fun wireTest(registered: List<RegisteredTestTasks>) {
    val test =
      if (target.tasks.names.contains(TEST_TASK_NAME)) {
        target.logger.debug(
          "pnpm: {} already has a {} task, adding the test tools to it",
          target.path,
          TEST_TASK_NAME,
        )
        target.tasks.named(TEST_TASK_NAME)
      } else {
        target.tasks.register(TEST_TASK_NAME) { task ->
          task.group = LifecycleBasePlugin.VERIFICATION_GROUP
          task.description = "Runs the test suites of the configured tools"
        }
      }

    test.configure { task ->
      registered.forEach { tool -> task.dependsOn(tool.test) }
    }
  }

  companion object {
    const val TEST_TASK_NAME: String = "test"
  }
}
