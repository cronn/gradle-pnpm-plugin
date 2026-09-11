package de.cronn.pnpm.task

import org.gradle.work.DisableCachingByDefault

/**
 * Runs Prettier through `pnpm exec prettier`, over the files the [includes] and [excludes]
 * describe.
 *
 * Prettier has no option to ignore a pattern, so the excludes are passed as negated operands. A
 * negation only excludes what it matches literally, so an exclude naming a directory needs the
 * trailing globstar an Ant pattern can leave out.
 */
@DisableCachingByDefault(
  because = "Runs Prettier; its effects are not fully described by declared outputs."
)
public abstract class PrettierTask : PnpmCheckTask() {

  init {
    command.convention("prettier")
  }

  override fun patternArguments(includes: List<String>, excludes: List<String>): List<String> =
    listOf(NO_ERROR_ON_UNMATCHED_PATTERN) + includes + excludes.map { "!$it" }
}
