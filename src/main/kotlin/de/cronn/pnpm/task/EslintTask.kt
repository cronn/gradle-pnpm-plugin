package de.cronn.pnpm.task

import de.cronn.pnpm.internal.task.PnpmCheckTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Runs ESLint through `pnpm exec eslint`, over the files the [includes] and [excludes] describe.
 *
 * ESLint rejects a negated operand, so the excludes are passed as
 * [`--ignore-pattern`](https://eslint.org/docs/latest/use/command-line-interface#--ignore-pattern).
 * Those follow the gitignore syntax, in which a pattern without a slash matches at any depth --
 * unlike the Ant patterns of Gradle, which anchor it to the project directory.
 *
 * The common options of ESLint are command line options of the task as well, so that an ad-hoc run
 * needs no edit to the build script:
 * ```bash
 * ./gradlew :frontend:eslintCheck --max-warnings=10
 * ./gradlew :frontend:eslintCheck --suppress-rule=no-console --suppress-rule=no-undef
 * ```
 *
 * Each of them is handed to ESLint as it is; which of them combine is up to ESLint. Anything else
 * ESLint takes goes into the [arguments] of the task, or into the `extraArguments` of the `eslint`
 * extension.
 */
@DisableCachingByDefault(
  because = "Runs ESLint; its effects are not fully described by declared outputs.",
)
public abstract class EslintTask : PnpmCheckTask() {

  override val configFileOption: String = "--config"

  /** The ESLint configuration file, passed as `--config`. */
  @get:InputFile
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:Option(option = "config", description = "Lints with this configuration file")
  abstract override val configFile: RegularFileProperty

  /** Applies the automatic fixes to the files, passed as `--fix`. */
  @get:Input
  @get:Optional
  @get:Option(option = "fix", description = "Applies the automatic fixes to the files")
  public abstract val fix: Property<Boolean>

  /** The number of warnings above which ESLint fails, passed as `--max-warnings`. */
  @get:Input
  @get:Optional
  @get:Option(option = "max-warnings", description = "Fails above this number of warnings")
  public abstract val maxWarnings: Property<Int>

  /** Only lints the files changed since the last run, passed as `--cache`. */
  @get:Input
  @get:Optional
  @get:Option(option = "cache", description = "Only lints the files changed since the last run")
  public abstract val cache: Property<Boolean>

  /** The file ESLint keeps its cache in, passed as `--cache-location`. */
  @get:OutputFile
  @get:Optional
  @get:Option(option = "cache-location", description = "Keeps the cache in this file")
  public abstract val cacheLocation: RegularFileProperty

  /** Suppresses every violation found, passed as `--suppress-all`. */
  @get:Input
  @get:Optional
  @get:Option(option = "suppress-all", description = "Suppresses every violation found")
  public abstract val suppressAll: Property<Boolean>

  /** The rules whose violations are suppressed, each passed as `--suppress-rule`. */
  @get:Input
  @get:Optional
  @get:Option(option = "suppress-rule", description = "Suppresses the violations of this rule")
  public abstract val suppressRules: ListProperty<String>

  /**
   * The file ESLint keeps its suppressions in, passed as `--suppressions-location`. It need not
   * exist yet, since ESLint creates it when suppressing.
   */
  @get:InputFiles
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:Option(option = "suppressions-location", description = "Keeps the suppressions in this file")
  public abstract val suppressionsLocation: RegularFileProperty

  /** Removes the suppressions that no longer occur, passed as `--prune-suppressions`. */
  @get:Input
  @get:Optional
  @get:Option(
    option = "prune-suppressions",
    description = "Removes the suppressions that no longer occur",
  )
  public abstract val pruneSuppressions: Property<Boolean>

  /** Keeps ESLint from failing when it is given no pattern, passed as `--pass-on-no-patterns`. */
  @get:Input
  @get:Optional
  @get:Option(option = "pass-on-no-patterns", description = "Does not fail when given no pattern")
  public abstract val passOnNoPatterns: Property<Boolean>

  /**
   * Keeps ESLint from failing over a pattern that matches nothing, passed as
   * `--no-error-on-unmatched-pattern`.
   */
  @get:Input
  @get:Optional
  @get:Option(
    option = "no-error-on-unmatched-pattern",
    description = "Does not fail when a pattern matches no file",
  )
  public abstract val noErrorOnUnmatchedPattern: Property<Boolean>

  init {
    command.convention("eslint")
  }

  override fun optionArguments(): List<String> = buildList {
    if (fix.getOrElse(false)) add("--fix")
    maxWarnings.orNull?.let { addAll(listOf("--max-warnings", it.toString())) }
    if (cache.getOrElse(false)) add("--cache")
    cacheLocation.orNull?.let { addAll(listOf("--cache-location", it.asFile.absolutePath)) }
    if (suppressAll.getOrElse(false)) add("--suppress-all")
    suppressRules.getOrElse(emptyList()).forEach { addAll(listOf("--suppress-rule", it)) }
    suppressionsLocation.orNull?.let {
      addAll(listOf("--suppressions-location", it.asFile.absolutePath))
    }
    if (pruneSuppressions.getOrElse(false)) add("--prune-suppressions")
    if (passOnNoPatterns.getOrElse(false)) add("--pass-on-no-patterns")
    if (noErrorOnUnmatchedPattern.getOrElse(false)) add(NO_ERROR_ON_UNMATCHED_PATTERN)
  }

  override fun patternArguments(includes: List<String>, excludes: List<String>): List<String> =
    excludes.flatMap { listOf("--ignore-pattern", it) } + includes
}
