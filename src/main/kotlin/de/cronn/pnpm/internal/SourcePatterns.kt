package de.cronn.pnpm.internal

import org.gradle.api.GradleException

/**
 * Validation of the source patterns of a task.
 *
 * The Ant matcher of Gradle knows `*`, `**` and `?` and matches every other construct literally, so
 * a pattern written for the globber of a tool resolves to no file at all rather than failing --
 * which leaves the task without a source and skips it. [reasonUnresolvable] rejects those, for
 * every task, because the patterns are always the Gradle inputs.
 *
 * A tool that is handed its patterns has to understand them as well, and disagrees with Gradle in
 * one more place. [reasonUnsupportedByTool] covers that one, and applies only where the patterns
 * reach a command line.
 *
 * Both are judged on the normalized form of the pattern, the way a task normalizes it before the
 * tool sees it, so that a Windows separator stays supported.
 */
internal object SourcePatterns {

  /** The prefixes that turn a `(` into an extglob rather than a character of a file name. */
  private val EXTGLOB = Regex("""[?*+@!]\(""")

  /** A Windows drive letter, which makes a pattern absolute without a leading separator. */
  private val DRIVE_PREFIX = Regex("""^[A-Za-z]:""")

  /** Throws when one of [patterns] is a pattern the Ant matcher of Gradle cannot resolve. */
  fun requireResolvable(patterns: List<String>, property: String, owner: String) {
    requireEach(patterns, property, owner, ::reasonUnresolvable)
  }

  /** Throws when one of [patterns] is a pattern the tool reads differently than Gradle. */
  fun requireSupportedByTool(patterns: List<String>, property: String, owner: String) {
    requireEach(patterns, property, owner, ::reasonUnsupportedByTool)
  }

  /**
   * Why the Ant matcher of Gradle cannot resolve [pattern], or null when it can. Every reason here
   * describes a construct it matches literally, which is why the pattern would find nothing.
   */
  fun reasonUnresolvable(pattern: String): String? {
    val glob = toGlob(pattern)
    return when {
      EXTGLOB.containsMatchIn(glob) ->
        "an extglob is understood by the globber of a tool, but matched literally by the Ant " +
          "matcher of Gradle, so the pattern finds nothing. Write one pattern per alternative"
      glob.startsWith("!") ->
        "a leading \"!\" is a negation the Ant matcher of Gradle does not have, so the pattern " +
          "finds nothing. Declare the pattern as an exclude instead"
      '{' in glob || '}' in glob ->
        "brace expansion is understood by the globber of a tool, but matched literally by the Ant " +
          "matcher of Gradle, so the pattern finds nothing. Write one pattern per alternative"
      '[' in glob || ']' in glob ->
        "a character class is understood by the globber of a tool, but matched literally by the " +
          "Ant matcher of Gradle, so the pattern finds nothing. Write one pattern per character"
      glob.startsWith("/") || DRIVE_PREFIX.containsMatchIn(glob) ->
        "an absolute pattern finds nothing; the patterns are relative to the directory the task " +
          "works in"
      glob.split('/').any { segment -> segment == ".." } ->
        "a \"..\" segment leaves the directory the task works in, which the patterns are relative to"
      else -> null
    }
  }

  /**
   * Why a tool reads [pattern] differently than the Ant matcher of Gradle, or null when it does
   * not.
   */
  fun reasonUnsupportedByTool(pattern: String): String? {
    val glob = toGlob(pattern)
    return when {
      glob.endsWith("/") ->
        "a trailing \"/\" matches everything in the directory for the Ant matcher of Gradle, but " +
          "only the directory entry itself for the tool. Write \"$glob**\""
      else -> null
    }
  }

  /** The Ant matcher of Gradle accepts a Windows separator in a pattern, a globber does not. */
  private fun toGlob(pattern: String): String = pattern.replace('\\', '/')

  private fun requireEach(
    patterns: List<String>,
    property: String,
    owner: String,
    reason: (String) -> String?,
  ) {
    patterns.forEach { pattern ->
      val unsupported = reason(pattern) ?: return@forEach
      throw GradleException(
        "The $property pattern \"$pattern\" of $owner is not supported: $unsupported. The patterns " +
          "are the Gradle inputs of the task, and of a tool that is handed them also the command " +
          "line, so every one of them has to be understood by the Ant matcher of Gradle."
      )
    }
  }
}
