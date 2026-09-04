package de.cronn.pnpm.internal

import de.cronn.pnpm.EslintExtension
import de.cronn.pnpm.PnpmToolExtension
import de.cronn.pnpm.PrettierExtension
import de.cronn.pnpm.TypescriptExtension
import de.cronn.pnpm.task.PnpmExecTask
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Registers the TypeScript, Prettier and ESLint tasks of a single pnpm package and wires them into
 * the Gradle lifecycle.
 *
 * A pnpm workspace root is a package like any other, so it gets the same tasks.
 */
internal class PnpmToolTasks(
  private val target: Project,
  private val typescript: TypescriptExtension,
  private val prettier: PrettierExtension,
  private val eslint: EslintExtension,
) {

  fun register() {
    // A value, not a convention: adding to a property that only has a convention discards it,
    // which would make the additive includes(...) method replace the defaults instead.
    typescript.includes.set(TYPESCRIPT_INCLUDES)
    prettier.includes.set(PRETTIER_INCLUDES)
    eslint.includes.set(ESLINT_INCLUDES)

    val compileTypescript = registerCompileTypescript()
    val prettierCheck = registerPrettierCheck()
    val prettierFix = registerPrettierFix()
    val eslintCheck = registerEslintCheck(compileTypescript)
    val eslintFix = registerEslintFix(compileTypescript)

    // Prettier has the final say on formatting, so it must not run before ESLint's --fix.
    prettierFix.configure { task -> task.mustRunAfter(eslintFix) }

    wireCheck(compileTypescript, prettierCheck, eslintCheck)
    registerFixTask(prettierFix, eslintFix)
  }

  /**
   * `tsc` takes the files to compile from `tsconfig.json`; passing them on the command line would
   * make it ignore that file, so this is the one tool that is not handed its sources.
   */
  private fun registerCompileTypescript(): TaskProvider<PnpmExecTask> =
    registerToolTask(
      typescript,
      name = "compileTypescript",
      description = "Checks the TypeScript sources with tsc",
      command = "tsc",
      arguments = listOf("--noEmit"),
    )

  private fun registerPrettierCheck(): TaskProvider<PnpmExecTask> =
    registerToolTask(
      prettier,
      name = "prettierCheck",
      description = "Checks the formatting of the sources with Prettier",
      command = "prettier",
      arguments = listOf("--check"),
      passesSources = true,
    )

  private fun registerPrettierFix(): TaskProvider<PnpmExecTask> =
    registerToolTask(
      prettier,
      name = "prettierFix",
      description = "Reformats the sources with Prettier",
      command = "prettier",
      arguments = listOf("--write", "--list-different"),
      passesSources = true,
      mutatesSources = true,
    )

  private fun registerEslintCheck(
    compileTypescript: TaskProvider<PnpmExecTask>
  ): TaskProvider<PnpmExecTask> =
    registerToolTask(
      eslint,
      name = "eslintCheck",
      description = "Checks the sources with ESLint",
      command = "eslint",
      arguments = ESLINT_ARGUMENTS,
      passesSources = true,
      dependsOn = compileTypescript,
    )

  private fun registerEslintFix(
    compileTypescript: TaskProvider<PnpmExecTask>
  ): TaskProvider<PnpmExecTask> =
    registerToolTask(
      eslint,
      name = "eslintFix",
      description = "Applies the automatic fixes of ESLint to the sources",
      command = "eslint",
      arguments = ESLINT_ARGUMENTS + "--fix",
      passesSources = true,
      dependsOn = compileTypescript,
      mutatesSources = true,
    )

  @Suppress("LongParameterList")
  private fun registerToolTask(
    tool: PnpmToolExtension,
    name: String,
    description: String,
    command: String,
    arguments: List<String>,
    passesSources: Boolean = false,
    dependsOn: TaskProvider<PnpmExecTask>? = null,
    mutatesSources: Boolean = false,
  ): TaskProvider<PnpmExecTask> =
    target.tasks.register(name, PnpmExecTask::class.java) { task ->
      task.group = LifecycleBasePlugin.VERIFICATION_GROUP
      task.description = description
      task.command.set(command)
      task.onlyIf("the tool is enabled") { tool.enabled.get() }
      if (dependsOn != null) {
        task.dependsOn(dependsOn)
      }

      val sources = sourceFiles(tool)
      task.inputs.files(sources).withPropertyName("sources")
      if (mutatesSources) {
        task.outputs.files(sources).withPropertyName("sources")
      } else {
        task.outputs.upToDateWhen { true }
      }

      task.arguments.set(
        sourceArguments(sources, passesSources).zip(tool.extraArguments) { files, extra ->
          files + arguments + extra
        }
      )
      if (passesSources) {
        // A tool invoked without any file operand fails instead of doing nothing.
        task.onlyIf("the tool has matching sources") { !it.inputs.files.isEmpty }
      }
    }

  /**
   * The sources as command line operands: project-relative, so that they are understood in the
   * working directory of the task, sorted so that the argument list is a stable task input.
   *
   * Resolved lazily, otherwise the configuration cache would keep serving the file listing of the
   * run that stored it.
   */
  private fun sourceArguments(sources: FileTree, passesSources: Boolean): Provider<List<String>> {
    if (!passesSources) {
      return target.provider { emptyList() }
    }
    // A File is safe to capture in a provider; a Project is not.
    val projectDirectory = target.projectDir
    return sources.elements.map { elements ->
      elements.map { it.asFile.relativeTo(projectDirectory).invariantSeparatorsPath }.sorted()
    }
  }

  /**
   * The files the tool inspects, resolved from the extension when the task is configured so that
   * `pnpm { ... }` blocks anywhere in the build script are taken into account.
   */
  private fun sourceFiles(tool: PnpmToolExtension): FileTree {
    val includes = tool.includes.get()
    val excludes = tool.excludes.get()
    target.logger.debug(
      "Sources of {}: including {}, excluding {}",
      target.path,
      includes,
      excludes,
    )
    return target.fileTree(target.projectDir) { tree ->
      tree.include(includes)
      tree.exclude(excludes)
    }
  }

  private fun wireCheck(
    compileTypescript: TaskProvider<PnpmExecTask>,
    prettierCheck: TaskProvider<PnpmExecTask>,
    eslintCheck: TaskProvider<PnpmExecTask>,
  ) {
    target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { task ->
      task.dependsOn(enabledTasks(typescript, compileTypescript))
      task.dependsOn(enabledTasks(prettier, prettierCheck))
      task.dependsOn(enabledTasks(eslint, eslintCheck))
    }
  }

  private fun registerFixTask(
    prettierFix: TaskProvider<PnpmExecTask>,
    eslintFix: TaskProvider<PnpmExecTask>,
  ) {
    target.tasks.register(FIX_TASK_NAME) { task ->
      task.group = LifecycleBasePlugin.VERIFICATION_GROUP
      task.description = "Applies all automatic source fixes of the configured tools"
      task.dependsOn(enabledTasks(prettier, prettierFix))
      task.dependsOn(enabledTasks(eslint, eslintFix))
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

  companion object {
    const val FIX_TASK_NAME: String = "fix"

    val BASE_INCLUDES: Array<String> = arrayOf("*.ts", "src/**/*.ts", "src/**/*.tsx")
    val TYPESCRIPT_INCLUDES: List<String> = listOf(*BASE_INCLUDES)
    val PRETTIER_INCLUDES: List<String> = listOf(*BASE_INCLUDES, "*.json", "*.md")
    val ESLINT_INCLUDES: List<String> = listOf(*BASE_INCLUDES)

    /**
     * ESLint skips a file matched by the `ignores` of its config, but warns about it when it was
     * named on the command line, which `--max-warnings=0` would turn into a failure.
     */
    val ESLINT_ARGUMENTS: List<String> = listOf("--max-warnings=0", "--no-warn-ignored")
  }
}
