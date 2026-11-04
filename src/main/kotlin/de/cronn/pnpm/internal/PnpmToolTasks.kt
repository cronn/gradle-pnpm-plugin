package de.cronn.pnpm.internal

import de.cronn.pnpm.EslintExtension
import de.cronn.pnpm.PlaywrightExtension
import de.cronn.pnpm.PrettierExtension
import de.cronn.pnpm.TypescriptExtension
import de.cronn.pnpm.VitestExtension
import de.cronn.pnpm.internal.check.EslintTasks
import de.cronn.pnpm.internal.check.PrettierTasks
import de.cronn.pnpm.internal.check.RegisteredCheckTasks
import de.cronn.pnpm.internal.check.TypescriptTasks
import de.cronn.pnpm.internal.task.PnpmTestTask
import de.cronn.pnpm.internal.test.PlaywrightTasks
import de.cronn.pnpm.internal.test.VitestTasks
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Registers the tasks of every Node tool of a pnpm package and wires them into `check`, `fix` and
 * `test`. Each tool defines its own tasks; what is left here crosses tool boundaries.
 */
internal class PnpmToolTasks(
  private val target: Project,
  private val typescript: TypescriptExtension,
  private val prettier: PrettierExtension,
  private val eslint: EslintExtension,
  private val playwright: PlaywrightExtension,
  private val vitest: VitestExtension,
) {

  /**
   * Registers the tasks of every tool the project is configured for, and wires them into the
   * lifecycle tasks. A tool whose config file is absent contributes no task at all.
   */
  fun register() {
    // A further tool is registered here and added to the lists below.
    val typescriptTasks = TypescriptTasks(target, typescript).register()
    val prettierTasks = PrettierTasks(target, prettier).register()
    val eslintTasks = EslintTasks(target, eslint).register()
    // An end-to-end suite is slow and usually needs a server the build does not start, so it stays
    // out of `test`.
    PlaywrightTasks(target, playwright).register()
    val vitestTest = VitestTasks(target, vitest).register()

    // Prettier has the final say on formatting, so it must not run before ESLint's --fix. Either
    // tool may be missing entirely, so both sides of the edge are optional.
    val eslintFix = eslintTasks?.fix
    if (eslintFix != null) {
      prettierTasks?.fix?.configure { task -> task.mustRunAfter(eslintFix) }
    }

    val checkTools = listOfNotNull(typescriptTasks, prettierTasks, eslintTasks)
    val test = wireTest(listOfNotNull(vitestTest))
    wireCheck(checkTools, test)
    registerFixTask(checkTools)
  }

  /**
   * Adds the check task of every tool to `check`, and `test` as a whole, so that a suite a build
   * script adds to `test` runs there as well.
   */
  private fun wireCheck(registered: List<RegisteredCheckTasks>, test: TaskProvider<Task>) {
    target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { task ->
      registered.forEach { tool -> task.dependsOn(tool.check) }
      task.dependsOn(test)
    }
  }

  /**
   * Registers `fix` whatever the project is configured for, so that a build script can always name
   * it. A project with no fix-capable tool gets one with nothing to do.
   */
  private fun registerFixTask(registered: List<RegisteredCheckTasks>) {
    target.tasks.register(FIX_TASK_NAME) { task ->
      task.group = LifecycleBasePlugin.VERIFICATION_GROUP
      task.description = "Applies all automatic source fixes of the configured tools"
      registered.forEach { tool -> tool.fix?.let { fix -> task.dependsOn(fix) } }
    }
  }

  /**
   * Adds the [suites] to `test`. `test` is registered unless the project already has a task of that
   * name -- the Java plugin brings its own, and a project applying both should end up with one
   * `test` running everything.
   *
   * `test` is registered whatever the project is configured for, so that a build script can always
   * name it. A project with no suite contributing to it gets one with nothing to do.
   */
  private fun wireTest(suites: List<TaskProvider<out PnpmTestTask>>): TaskProvider<Task> {
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

    test.configure { task -> suites.forEach { suite -> task.dependsOn(suite) } }
    return test
  }

  companion object {
    const val FIX_TASK_NAME: String = "fix"
    const val TEST_TASK_NAME: String = "test"
  }
}
