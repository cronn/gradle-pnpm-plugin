package de.cronn.pnpm.internal

import de.cronn.pnpm.EslintExtension
import de.cronn.pnpm.PrettierExtension
import de.cronn.pnpm.TypescriptExtension
import de.cronn.pnpm.internal.tool.EslintTasks
import de.cronn.pnpm.internal.tool.PrettierTasks
import de.cronn.pnpm.internal.tool.RegisteredToolTasks
import de.cronn.pnpm.internal.tool.TypescriptTasks
import de.cronn.pnpm.task.PnpmToolTask
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Registers the tasks of every Node tool of a single pnpm package and wires them into the Gradle
 * lifecycle. Each tool defines its own tasks; what is left here is what crosses tool boundaries.
 *
 * A pnpm workspace root is a package like any other, so it gets the same tasks.
 */
internal class PnpmToolTasks(
  private val target: Project,
  typescript: TypescriptExtension,
  prettier: PrettierExtension,
  eslint: EslintExtension,
) {

  private val typescriptTasks = TypescriptTasks(target, typescript)
  private val prettierTasks = PrettierTasks(target, prettier)
  private val eslintTasks = EslintTasks(target, eslint)

  fun register() {
    // A further tool is registered here and added to the list below.
    val typescript = typescriptTasks.register()
    val prettier = prettierTasks.register()
    val eslint = eslintTasks.register()

    // Prettier has the final say on formatting, so it must not run before ESLint's --fix.
    val eslintFix = eslint.fix
    if (eslintFix != null) {
      prettier.fix?.configure { task -> task.mustRunAfter(eslintFix) }
    }

    val registered = listOf(typescript, prettier, eslint)
    wireCheck(registered)
    registerFixTask(registered)
  }

  private fun wireCheck(registered: List<RegisteredToolTasks>) {
    target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { task ->
      registered.forEach { tool -> task.dependsOn(enabledTask(tool, tool.check)) }
    }
  }

  private fun registerFixTask(registered: List<RegisteredToolTasks>) {
    target.tasks.register(FIX_TASK_NAME) { task ->
      task.group = LifecycleBasePlugin.VERIFICATION_GROUP
      task.description = "Applies all automatic source fixes of the configured tools"
      registered.forEach { tool -> tool.fix?.let { fix -> task.dependsOn(enabledTask(tool, fix)) } }
    }
  }

  /**
   * A dependency on [task] that disappears when the tool is disabled. Resolving this lazily is what
   * lets `enabled` be configured after the plugin has been applied.
   */
  private fun enabledTask(
    tool: RegisteredToolTasks,
    task: TaskProvider<out PnpmToolTask>,
  ): Provider<List<TaskProvider<out PnpmToolTask>>> =
    tool.extension.enabled.map { enabled -> if (enabled) listOf(task) else emptyList() }

  companion object {
    const val FIX_TASK_NAME: String = "fix"
  }
}
