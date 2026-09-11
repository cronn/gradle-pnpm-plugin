package de.cronn.pnpm.internal.check

import org.gradle.api.GradleException

/**
 * Validation of the source patterns of a check task.
 *
 * The patterns are resolved twice -- by the Ant matcher of Gradle, into the inputs of the task, and
 * by the globber of the tool, which is handed the patterns themselves. A pattern only one of the
 * two understands is not an error in either: it silently describes a different set of files on each
 * side, and one that resolves to nothing in Gradle skips the task altogether. The constructs below
 * are therefore rejected before that can happen.
 *
 * A Windows separator stays supported: every pattern is normalized into a glob first, the way the
 * task normalizes it before the tool sees it.
 */
internal object CheckPatterns {

  /** The prefixes that turn a `(` into an extglob rather than a character of a file name. */
  private val EXTGLOB = Regex("""[?*+@!]\(""")

  /** A Windows drive letter, which makes a pattern absolute without a leading separator. */
  private val DRIVE_PREFIX = Regex("""^[A-Za-z]:""")

  /**
   * Throws when one of [patterns] is understood by only one of the two resolvers, naming the
   * offending pattern, the [property] it was declared in and the task [owner] it belongs to.
   */
  fun requireSupported(patterns: List<String>, property: String, owner: String) {
    patterns.forEach { pattern ->
      val reason = reasonUnsupported(pattern) ?: return@forEach
      throw GradleException(
        "The $property pattern \"$pattern\" of $owner is not supported: $reason. The patterns are " +
          "both the Gradle inputs of the task and what the tool is invoked with, so every one of " +
          "them has to be understood by the Ant matcher of Gradle and by the tool; the README " +
          "lists the constructs that are not."
      )
    }
  }

  /** Why [pattern] is understood by only one of the two resolvers, or null when it is supported. */
  fun reasonUnsupported(pattern: String): String? {
    val glob = pattern.replace('\\', '/')
    return when {
      EXTGLOB.containsMatchIn(glob) ->
        "an extglob is understood by the globber of the tool, but matched literally by the Ant " +
          "matcher of Gradle. Write one pattern per alternative"
      glob.startsWith("!") ->
        "a leading \"!\" is a negation the Ant matcher of Gradle does not have, and the excludes " +
          "of a Prettier task are passed as one already. Declare the pattern as an exclude instead"
      '{' in glob || '}' in glob ->
        "brace expansion is understood by the globber of the tool, but resolves to no file at all " +
          "in the Ant matcher of Gradle. Write one pattern per alternative"
      '[' in glob || ']' in glob ->
        "a character class is understood by the globber of the tool, but matched literally by the " +
          "Ant matcher of Gradle. Write one pattern per character"
      glob.startsWith("/") || DRIVE_PREFIX.containsMatchIn(glob) ->
        "an absolute pattern matches nothing; the patterns are relative to the directory the tool " +
          "is invoked in"
      glob.split('/').any { segment -> segment == ".." } ->
        "a \"..\" segment leaves the directory the tool is invoked in, which the patterns are " +
          "relative to"
      glob.endsWith("/") ->
        "a trailing \"/\" matches everything in the directory for the Ant matcher of Gradle, but " +
          "only the directory entry itself for the tool. Write \"$glob**\""
      else -> null
    }
  }
}
