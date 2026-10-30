package de.cronn.pnpm.internal.task

import de.cronn.pnpm.internal.SourcePatterns
import de.cronn.pnpm.task.CommandLine
import org.gradle.work.DisableCachingByDefault

/**
 * Inspects the sources with a Node tool that is handed the patterns.
 *
 * The tool gets the patterns rather than the files they resolve to, which would overrun the command
 * line length limit of Windows on a large source set. A pattern therefore has to mean the same to
 * the tool as to Gradle, and one the two read differently is rejected.
 */
@DisableCachingByDefault(
  because = "Runs an arbitrary Node tool; its effects are not fully described by declared outputs.",
)
public abstract class PnpmCheckTask : PnpmSourceTask() {

  /**
   * The [includes] and [excludes] as arguments of the tool, which each tool excludes its own way.
   */
  protected abstract fun patternArguments(
    includes: List<String>,
    excludes: List<String>,
  ): List<String>

  /** The patterns trail every option of the tool, ahead of the [extraArguments]. */
  override fun contribute(commandLine: CommandLine) {
    commandLine.tail(patternArguments(includes.get().map(::toGlob), excludes.get().map(::toGlob)))
    super.contribute(commandLine)
  }

  /** Also rejects a pattern the tool reads differently than Gradle. */
  final override fun requireSupportedPatterns() {
    super.requireSupportedPatterns()
    SourcePatterns.requireSupportedByTool(includes.get(), "includes", path)
    SourcePatterns.requireSupportedByTool(excludes.get(), "excludes", path)
  }

  /** Gradle accepts a Windows separator in a pattern, the tools do not. */
  private fun toGlob(pattern: String): String = pattern.replace('\\', '/')

  protected companion object {
    /** Keeps a tool from failing over a pattern that matches nothing. */
    public const val NO_ERROR_ON_UNMATCHED_PATTERN: String = "--no-error-on-unmatched-pattern"
  }
}
