package de.cronn.pnpm.task

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.work.DisableCachingByDefault

/**
 * Runs a Node tool over a set of sources.
 *
 * The plugin registers the predefined tasks of every tool as one of the subclasses --
 * [TypescriptTask], [PrettierTask] and [EslintTask] -- and configures every task of those types
 * with the [sources][de.cronn.pnpm.PnpmToolExtension.includes], the
 * [extraArguments][de.cronn.pnpm.PnpmToolExtension.extraArguments] and the
 * [enabled][de.cronn.pnpm.PnpmToolExtension.enabled] state of the tool's extension. A build script
 * that registers a task of one of those types therefore gets a task that behaves like the
 * predefined ones, and only has to say what is different about it.
 */
@DisableCachingByDefault(
  because = "Runs an arbitrary Node tool; its effects are not fully described by declared outputs."
)
public abstract class PnpmToolTask : PnpmExecTask() {

  /**
   * The files the tool inspects. Defaults to the files the tool extension's include and exclude
   * patterns resolve to.
   *
   * A task whose sources are empty is skipped, because a tool invoked without a file to work on
   * fails instead of doing nothing.
   */
  @get:InputFiles
  @get:SkipWhenEmpty
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val sources: ConfigurableFileCollection

  /**
   * Arguments appended after [arguments]. Defaults to the `extraArguments` of the tool's extension,
   * so that they apply to every task of this tool.
   */
  @get:Input public abstract val extraArguments: ListProperty<String>

  /**
   * The [sources] as command line operands, relative to the [workingDirectory] the tool is invoked
   * in and sorted, so that the command line is stable across runs and machines.
   *
   * Subclasses of tools that take their file set from a config file instead override this with an
   * empty list.
   */
  protected open fun sourceOperands(): List<String> {
    val directory = workingDirectory.get().asFile
    return sources.files.map { it.relativeTo(directory).invariantSeparatorsPath }.sorted()
  }

  override fun buildArguments(): List<String> =
    listOf("exec", command.get()) + sourceOperands() + arguments.get() + extraArguments.get()
}
