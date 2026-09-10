package de.cronn.pnpm.task

import org.gradle.work.DisableCachingByDefault

/**
 * Runs ESLint through `pnpm exec eslint`, over the files the [includes] and [excludes] describe.
 *
 * ESLint rejects a negated operand, so the excludes are passed as
 * [`--ignore-pattern`](https://eslint.org/docs/latest/use/command-line-interface#--ignore-pattern).
 * Those follow the gitignore syntax, in which a pattern without a slash matches at any depth --
 * unlike the Ant patterns of Gradle, which anchor it to the project directory.
 */
@DisableCachingByDefault(
  because = "Runs ESLint; its effects are not fully described by declared outputs."
)
public abstract class EslintTask : PnpmToolTask() {

  init {
    command.convention("eslint")
  }

  override fun patternArguments(includes: List<String>, excludes: List<String>): List<String> =
    listOf(NO_ERROR_ON_UNMATCHED_PATTERN) +
      excludes.flatMap { listOf("--ignore-pattern", it) } +
      includes
}
