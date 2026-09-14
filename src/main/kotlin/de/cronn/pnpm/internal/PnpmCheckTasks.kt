package de.cronn.pnpm.internal

import de.cronn.pnpm.EslintExtension
import de.cronn.pnpm.PrettierExtension
import de.cronn.pnpm.TypescriptExtension
import de.cronn.pnpm.internal.check.EslintTasks
import de.cronn.pnpm.internal.check.PrettierTasks
import de.cronn.pnpm.internal.check.RegisteredCheckTasks
import de.cronn.pnpm.internal.check.TypescriptTasks
import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Registers the tasks of every Node tool of a single pnpm package and wires them into the Gradle
 * lifecycle. Each tool defines its own tasks; what is left here is what crosses tool boundaries.
 *
 * A pnpm workspace root is a package like any other, so it gets the same tasks.
 */
internal class PnpmCheckTasks(
  private val target: Project,
  typescript: TypescriptExtension,
  prettier: PrettierExtension,
  eslint: EslintExtension,
) {

  private val typescriptTasks = TypescriptTasks(target, typescript)
  private val prettierTasks = PrettierTasks(target, prettier)
  private val eslintTasks = EslintTasks(target, eslint)

  /**
   * Registers the tasks of every tool the project is configured for, and wires them into `check`
   * and `fix`. A tool whose config file is absent contributes no task at all.
   */
  fun register(typescript: Boolean, prettier: Boolean, eslint: Boolean) {
    // A further tool is registered here and added to the list below.
    val typescriptTasks = typescriptTasks.register(typescript)
    val prettierTasks = prettierTasks.register(prettier)
    val eslintTasks = eslintTasks.register(eslint)

    // Prettier has the final say on formatting, so it must not run before ESLint's --fix. Either
    // tool may be missing entirely, so both sides of the edge are optional.
    val eslintFix = eslintTasks?.fix
    if (eslintFix != null) {
      prettierTasks?.fix?.configure { task -> task.mustRunAfter(eslintFix) }
    }

    val registered = listOfNotNull(typescriptTasks, prettierTasks, eslintTasks)
    wireCheck(registered)
    registerFixTask(registered)
  }

  private fun wireCheck(registered: List<RegisteredCheckTasks>) {
    target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { task ->
      registered.forEach { tool -> task.dependsOn(tool.check) }
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

  companion object {
    const val FIX_TASK_NAME: String = "fix"
  }
}
