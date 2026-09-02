package de.cronn.pnpm

import de.cronn.pnpm.task.PnpmExecTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Adds the TypeScript, Prettier and ESLint tasks of a single pnpm workspace package and wires them
 * into the Gradle lifecycle.
 *
 * Each tool can be configured, and switched off, through the `pnpmPackage` extension:
 * ```
 * pnpmPackage {
 *   typescript { include("sources") }
 *   prettier {
 *     include("sources")
 *     exclude("generated")
 *     extraArguments("--cache")
 *   }
 *   eslint { enabled = false }
 * }
 * ```
 *
 * The patterns are Ant-style include and exclude patterns, resolved against the project directory,
 * and are added to the default patterns of the respective tool.
 */
public class PnpmPackagePlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.pluginManager.apply(PnpmBasePlugin::class.java)
    target.pluginManager.apply(BasePlugin::class.java)

    val extension = target.extensions.create(EXTENSION_NAME, PnpmPackageExtension::class.java)

    val compileTypescript = registerCompileTypescript(target, extension)
    val prettierCheck = registerPrettierCheck(target, extension)
    val prettierFix = registerPrettierFix(target, extension)
    val eslintCheck = registerEslintCheck(target, extension, compileTypescript)
    val eslintFix = registerEslintFix(target, extension, compileTypescript)

    // Prettier has the final say on formatting, so it must not run before ESLint's --fix.
    prettierFix.configure { task -> task.mustRunAfter(eslintFix) }

    wireCheck(target, extension, compileTypescript, prettierCheck, eslintCheck)
    registerFixTask(target, extension, prettierFix, eslintFix)
  }

  private fun registerCompileTypescript(
    target: Project,
    extension: PnpmPackageExtension,
  ): TaskProvider<PnpmExecTask> =
    registerToolTask(
      target,
      extension.typescript,
      name = "compileTypescript",
      description = "Checks the TypeScript sources with tsc",
      command = "tsc",
      arguments = listOf("--noEmit"),
      defaultIncludes = TYPESCRIPT_INCLUDES,
    )

  private fun registerPrettierCheck(
    target: Project,
    extension: PnpmPackageExtension,
  ): TaskProvider<PnpmExecTask> =
    registerToolTask(
      target,
      extension.prettier,
      name = "prettierCheck",
      description = "Checks the formatting of the sources with Prettier",
      command = "prettier",
      arguments = listOf(".", "--check"),
      defaultIncludes = PRETTIER_INCLUDES,
    )

  private fun registerPrettierFix(
    target: Project,
    extension: PnpmPackageExtension,
  ): TaskProvider<PnpmExecTask> =
    registerToolTask(
      target,
      extension.prettier,
      name = "prettierFix",
      description = "Reformats the sources with Prettier",
      command = "prettier",
      arguments = listOf(".", "--write", "--list-different"),
      defaultIncludes = PRETTIER_INCLUDES,
      mutatesSources = true,
    )

  private fun registerEslintCheck(
    target: Project,
    extension: PnpmPackageExtension,
    compileTypescript: TaskProvider<PnpmExecTask>,
  ): TaskProvider<PnpmExecTask> =
    registerToolTask(
      target,
      extension.eslint,
      name = "eslintCheck",
      description = "Checks the sources with ESLint",
      command = "eslint",
      arguments = listOf(".", "--max-warnings=0"),
      defaultIncludes = ESLINT_INCLUDES,
      dependsOn = compileTypescript,
    )

  private fun registerEslintFix(
    target: Project,
    extension: PnpmPackageExtension,
    compileTypescript: TaskProvider<PnpmExecTask>,
  ): TaskProvider<PnpmExecTask> =
    registerToolTask(
      target,
      extension.eslint,
      name = "eslintFix",
      description = "Applies the automatic fixes of ESLint to the sources",
      command = "eslint",
      arguments = listOf(".", "--max-warnings=0", "--fix"),
      defaultIncludes = ESLINT_INCLUDES,
      dependsOn = compileTypescript,
      mutatesSources = true,
    )

  @Suppress("LongParameterList")
  private fun registerToolTask(
    target: Project,
    tool: PnpmToolExtension,
    name: String,
    description: String,
    command: String,
    arguments: List<String>,
    defaultIncludes: List<String>,
    dependsOn: TaskProvider<PnpmExecTask>? = null,
    mutatesSources: Boolean = false,
  ): TaskProvider<PnpmExecTask> =
    target.tasks.register(name, PnpmExecTask::class.java) { task ->
      task.group = LifecycleBasePlugin.VERIFICATION_GROUP
      task.description = description
      task.command.set(command)
      task.arguments.set(tool.extraArguments.map { extra -> arguments + extra })
      task.onlyIf("the tool is enabled") { tool.enabled.get() }
      if (dependsOn != null) {
        task.dependsOn(dependsOn)
      }

      val sources = sourceFiles(target, tool, defaultIncludes)
      task.inputs.files(sources).withPropertyName("sources")
      if (mutatesSources) {
        // A formatter rewrites its own inputs, so declaring them as outputs too would make the
        // task either permanently out of date or, worse, wrongly up to date.
        task.outputs.upToDateWhen { false }
      } else {
        // The tool only reports problems; there is nothing to restore from the build cache, but
        // unchanged sources need not be checked twice.
        task.outputs.upToDateWhen { true }
      }
    }

  /**
   * The files the tool inspects, resolved from the extension when the task is configured so that
   * `pnpmPackage { ... }` blocks anywhere in the build script are taken into account.
   */
  private fun sourceFiles(
    target: Project,
    tool: PnpmToolExtension,
    defaultIncludes: List<String>,
  ): FileTree =
    target.fileTree(target.projectDir) { tree ->
      tree.include(defaultIncludes + tool.include.get())
      tree.exclude(tool.exclude.get())
    }

  private fun wireCheck(
    target: Project,
    extension: PnpmPackageExtension,
    compileTypescript: TaskProvider<PnpmExecTask>,
    prettierCheck: TaskProvider<PnpmExecTask>,
    eslintCheck: TaskProvider<PnpmExecTask>,
  ) {
    target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { task ->
      task.dependsOn(enabledTasks(extension.typescript, compileTypescript))
      task.dependsOn(enabledTasks(extension.prettier, prettierCheck))
      task.dependsOn(enabledTasks(extension.eslint, eslintCheck))
    }
  }

  private fun registerFixTask(
    target: Project,
    extension: PnpmPackageExtension,
    prettierFix: TaskProvider<PnpmExecTask>,
    eslintFix: TaskProvider<PnpmExecTask>,
  ) {
    target.tasks.register(FIX_TASK_NAME) { task ->
      task.group = LifecycleBasePlugin.VERIFICATION_GROUP
      task.description = "Applies all automatic source fixes of the configured tools"
      task.dependsOn(enabledTasks(extension.prettier, prettierFix))
      task.dependsOn(enabledTasks(extension.eslint, eslintFix))
    }
  }

  /**
   * A dependency on [task] that disappears when the tool is disabled. Resolving this lazily is what
   * lets `enabled` be configured after the plugin has been applied.
   */
  private fun enabledTasks(
    tool: PnpmToolExtension,
    task: TaskProvider<PnpmExecTask>,
  ): Provider<List<TaskProvider<PnpmExecTask>>> =
    tool.enabled.map { enabled -> if (enabled) listOf(task) else emptyList() }

  internal companion object {
    const val EXTENSION_NAME: String = "pnpmPackage"
    const val FIX_TASK_NAME: String = "fix"
    val TYPESCRIPT_INCLUDES: List<String> = listOf("eslint.config.ts", "prettier.config.ts")
    val PRETTIER_INCLUDES: List<String> = listOf("*.ts", "*.json", "*.md")
    val ESLINT_INCLUDES: List<String> = listOf("*.ts")
  }
}
