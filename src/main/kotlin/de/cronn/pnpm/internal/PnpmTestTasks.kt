package de.cronn.pnpm.internal

import de.cronn.pnpm.PlaywrightExtension
import de.cronn.pnpm.VitestExtension
import de.cronn.pnpm.internal.test.PlaywrightTasks
import de.cronn.pnpm.internal.test.RegisteredTestTasks
import de.cronn.pnpm.internal.test.VitestTasks
import java.io.File
import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Registers the tasks of every Node test tool of a single pnpm package and wires them into the
 * `test` and `check` lifecycle tasks. Each tool defines its own tasks; what is left here is what
 * crosses tool boundaries.
 *
 * Every test tool contributes its suite to `test`. `check` is the tool's own decision, carried by
 * [RegisteredTestTasks.contributesToCheck]: a unit suite belongs in `build`, while an end-to-end
 * suite is slow and usually needs a server the build does not start. `check` therefore runs
 * `vitestTest` but not `playwrightTest`; a build that wants the latter adds the edge itself.
 */
internal class PnpmTestTasks(
  private val target: Project,
  playwright: PlaywrightExtension,
  vitest: VitestExtension,
  lockfile: File?,
) {

  private val playwrightTasks = PlaywrightTasks(target, playwright, lockfile)
  private val vitestTasks = VitestTasks(target, vitest)

  /**
   * Registers the tasks of every test tool the project is configured for, and wires them into
   * `test` and `check`. A tool whose config file is absent contributes no task at all.
   */
  fun register(playwright: Boolean, vitest: Boolean) {
    // A further test tool is registered here and added to the list below.
    val playwrightTasks = playwrightTasks.registerAll(playwright)
    val vitestTasks = vitestTasks.register(vitest)

    val registered = listOfNotNull(playwrightTasks, vitestTasks)
    wireTest(registered)
    wireCheck(registered.filter { tool -> tool.contributesToCheck })
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

  /**
   * Adds the test task of every tool that belongs in `check` to it. `check` is always there:
   * `PnpmPlugin` applies the Base Plugin before any tool is registered.
   */
  private fun wireCheck(registered: List<RegisteredTestTasks>) {
    if (registered.isEmpty()) {
      return
    }

    target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { task ->
      registered.forEach { tool -> task.dependsOn(tool.test) }
    }
  }

  companion object {
    const val TEST_TASK_NAME: String = "test"
  }
}
