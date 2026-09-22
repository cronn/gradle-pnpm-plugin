package de.cronn.pnpm.internal

import de.cronn.pnpm.PlaywrightExtension
import de.cronn.pnpm.VitestExtension
import de.cronn.pnpm.internal.test.PlaywrightTasks
import de.cronn.pnpm.internal.test.RegisteredTestTasks
import de.cronn.pnpm.internal.test.VitestTasks
import java.io.File
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Registers the tasks of every Node test tool of a single pnpm package and wires them into the
 * `test` and `check` lifecycle tasks. Each tool defines its own tasks; what is left here is what
 * crosses tool boundaries.
 *
 * `check` always depends on `test`, so that any suite the build script adds to `test` itself also
 * runs as part of `check`. Whether a tool's own suite is added to `test` is the tool's own
 * decision, carried by [RegisteredTestTasks.contributesToTest]: a unit suite belongs in `build`,
 * while an end-to-end suite is slow and usually needs a server the build does not start.
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
    val test = wireTest(registered.filter { tool -> tool.contributesToTest })
    wireCheck(test)
  }

  /**
   * Adds the test task of every tool that belongs in `test` to it. `test` is registered unless the
   * project already has a task of that name -- the Java plugin brings its own, and a project
   * applying both should end up with one `test` running everything.
   *
   * `test` is registered whatever the project is configured for, so that a build script can always
   * name it. A project with no suite contributing to it gets one with nothing to do.
   */
  private fun wireTest(registered: List<RegisteredTestTasks>): TaskProvider<Task> {
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
    return test
  }

  /**
   * Adds `test` to `check`. `check` is always there: `PnpmPlugin` applies the Base Plugin before
   * any tool is registered. This is unconditional, so a suite a build script adds to `test` itself
   * also runs as part of `check`.
   */
  private fun wireCheck(test: TaskProvider<Task>) {
    target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { task ->
      task.dependsOn(test)
    }
  }

  companion object {
    const val TEST_TASK_NAME: String = "test"
  }
}
