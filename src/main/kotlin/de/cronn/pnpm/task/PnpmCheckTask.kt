package de.cronn.pnpm.task

import javax.inject.Inject
import org.gradle.api.file.FileTree
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.work.DisableCachingByDefault

/**
 * Inspects a set of sources with a Node tool.
 *
 * The counterpart of [PnpmTestTask]: a task of this type reports on sources that are already there
 * -- type errors, lint findings, formatting -- and takes part in `check` and `fix`, where a test
 * task runs a suite and takes part in `test`.
 *
 * The plugin registers the predefined tasks of every tool as one of the subclasses --
 * [TypescriptTask], [PrettierTask] and [EslintTask] -- and configures every task of those types
 * with the [includes][de.cronn.pnpm.PnpmCheckExtension.includes], the
 * [excludes][de.cronn.pnpm.PnpmCheckExtension.excludes], the
 * [extraArguments][de.cronn.pnpm.PnpmCheckExtension.extraArguments] and the
 * [enabled][de.cronn.pnpm.PnpmCheckExtension.enabled] state of the tool's extension. A build script
 * that registers a task of one of those types therefore gets a task that behaves like the
 * predefined ones, and only has to say what is different about it.
 *
 * The patterns, not the files they resolve to, are what the tool is invoked with: naming every
 * source file on the command line overruns the command line length limit of Windows on a large
 * source set. The patterns therefore have to be understood both by the Ant matcher of Gradle, which
 * resolves them to the [sourceFiles] deciding when the task is up to date, and by the tool itself.
 * Each tool translates them in [patternArguments], because the command line syntax for exclusions
 * differs between the tools.
 */
@DisableCachingByDefault(
  because = "Runs an arbitrary Node tool; its effects are not fully described by declared outputs."
)
public abstract class PnpmCheckTask : PnpmExecTask() {

  @get:Inject protected abstract val objects: ObjectFactory

  /**
   * Ant-style patterns of the files the tool inspects, relative to the [workingDirectory] the tool
   * is invoked in. Defaults to the `includes` of the tool's extension.
   */
  @get:Input public abstract val includes: ListProperty<String>

  /** Ant-style patterns excluded from [includes]. Defaults to the `excludes` of the extension. */
  @get:Input public abstract val excludes: ListProperty<String>

  /**
   * The files [includes] and [excludes] resolve to, which are the inputs deciding when this task is
   * up to date. Derived from the patterns; configure those instead.
   *
   * A task whose sources are empty is skipped, because a tool invoked without a file to work on
   * fails instead of doing nothing. That covers the case of every pattern matching nothing; a tool
   * whose patterns match nothing only individually is left to the tool, which is why the tools are
   * invoked with `--no-error-on-unmatched-pattern`.
   */
  @get:InputFiles
  @get:SkipWhenEmpty
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public val sourceFiles: FileTree
    get() {
      val tree = objects.fileTree()
      tree.setDir(workingDirectory.get().asFile)
      tree.setIncludes(includes.get())
      tree.setExcludes(excludes.get())
      return tree
    }

  /**
   * Arguments appended after [arguments]. Defaults to the `extraArguments` of the tool's extension,
   * so that they apply to every task of this tool.
   */
  @get:Input public abstract val extraArguments: ListProperty<String>

  /**
   * The [includes] and [excludes] as command line arguments of the tool, in the order they were
   * declared in. Every argument the tool contributes itself comes before the patterns, so that an
   * [extraArguments] entry can never be taken for one.
   *
   * Implemented per tool: the tools agree on passing the includes as operands, but not on how to
   * exclude, and a tool that takes its file set from a config file wants no patterns at all.
   */
  protected abstract fun patternArguments(
    includes: List<String>,
    excludes: List<String>,
  ): List<String>

  override fun commandArguments(): List<String> =
    patternArguments(includes.get().map(::toGlob), excludes.get().map(::toGlob)) +
      arguments.get() +
      extraArguments.get()

  /**
   * The Ant matcher of Gradle accepts a Windows separator in a pattern, the globbers of the tools
   * do not.
   */
  private fun toGlob(pattern: String): String = pattern.replace('\\', '/')

  protected companion object {
    /**
     * Keeps a tool from failing over one of its patterns matching nothing, which a pattern that is
     * a default of the plugin rather than a choice of the project easily does.
     */
    public const val NO_ERROR_ON_UNMATCHED_PATTERN: String = "--no-error-on-unmatched-pattern"
  }
}
