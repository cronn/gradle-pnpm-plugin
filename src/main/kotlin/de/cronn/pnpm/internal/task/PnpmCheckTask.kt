package de.cronn.pnpm.internal.task

import de.cronn.pnpm.internal.SourcePatterns
import de.cronn.pnpm.internal.extension.PnpmSourceExtension
import org.gradle.work.DisableCachingByDefault

/**
 * Inspects a set of sources with a Node tool that is handed the patterns.
 *
 * One of the three [PnpmSourceTask] kinds: a task of this type reports on sources that are already
 * there -- lint findings, formatting -- and takes part in `check` and `fix`, where a [PnpmTestTask]
 * runs a suite and takes part in `test`.
 *
 * The plugin registers the predefined tasks of every such tool as one of the subclasses --
 * `PrettierTask` and `EslintTask` -- and configures every task of those types with the
 * [includes][PnpmSourceExtension.includes], the [excludes][PnpmSourceExtension.excludes], the
 * [extraArguments][PnpmSourceExtension.extraArguments] and the
 * [enabled][PnpmSourceExtension.enabled] state of the tool's extension. A build script that
 * registers a task of one of those types therefore gets a task that behaves like the predefined
 * ones, and only has to say what is different about it.
 *
 * The patterns, not the files they resolve to, are what the tool is invoked with: naming every
 * source file on the command line overruns the command line length limit of Windows on a large
 * source set. The patterns therefore have to be understood by the tool on top of the Ant matcher of
 * Gradle, and a pattern the two read differently is rejected. Each tool translates the rest in
 * [patternArguments], because the command line syntax for exclusions differs between the tools.
 */
@DisableCachingByDefault(
  because = "Runs an arbitrary Node tool; its effects are not fully described by declared outputs."
)
public abstract class PnpmCheckTask : PnpmSourceTask() {

  /**
   * The [includes] and [excludes] as command line arguments of the tool, in the order they were
   * declared in. Every argument the tool contributes itself comes before the patterns, so that an
   * [extraArguments] entry can never be taken for one.
   *
   * Implemented per tool: the tools agree on passing the includes as operands, but not on how to
   * exclude.
   */
  protected abstract fun patternArguments(
    includes: List<String>,
    excludes: List<String>,
  ): List<String>

  final override fun commandArguments(): List<String> =
    patternArguments(includes.get().map(::toGlob), excludes.get().map(::toGlob)) +
      arguments.get() +
      extraArguments.get()

  /** Adds what the globber of the tool requires to what the Ant matcher of Gradle does. */
  final override fun requireSupportedPatterns() {
    super.requireSupportedPatterns()
    SourcePatterns.requireSupportedByTool(includes.get(), "includes", path)
    SourcePatterns.requireSupportedByTool(excludes.get(), "excludes", path)
  }

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
