package de.cronn.pnpm.task

import de.cronn.pnpm.internal.task.PnpmCheckTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Runs Prettier through `pnpm exec prettier`, over the files the [includes] and [excludes]
 * describe.
 *
 * Prettier has no option to ignore a pattern, so the excludes are passed as negated operands. A
 * negation only excludes what it matches literally, so an exclude naming a directory needs the
 * trailing globstar an Ant pattern can leave out.
 *
 * The common options of Prettier are command line options of the task as well, so that an ad-hoc
 * run needs no edit to the build script:
 * ```bash
 * ./gradlew :frontend:prettierCheck --ignore-unknown
 * ./gradlew :frontend:prettierFix --cache --cache-location=build/prettier-cache
 * ```
 *
 * Each of them is handed to Prettier as it is; which of them combine is up to Prettier. Anything
 * else Prettier takes goes into the [arguments] of the task, or into the `extraArguments` of the
 * `prettier` extension.
 */
@DisableCachingByDefault(
  because = "Runs Prettier; its effects are not fully described by declared outputs.",
)
public abstract class PrettierTask : PnpmCheckTask() {

  /** The Prettier configuration file, passed as `--config`. */
  @get:InputFile
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:Option(option = "config", description = "Formats with this configuration file")
  abstract override val configFile: RegularFileProperty

  /** Checks whether the files are formatted, passed as `--check`. */
  @get:Input
  @get:Optional
  @get:Option(option = "check", description = "Checks whether the files are formatted")
  public abstract val check: Property<Boolean>

  /** Formats the files in place, passed as `--write`. */
  @get:Input
  @get:Optional
  @get:Option(option = "write", description = "Formats the files in place")
  public abstract val write: Property<Boolean>

  /** Prints the files whose formatting differs, passed as `--list-different`. */
  @get:Input
  @get:Optional
  @get:Option(option = "list-different", description = "Prints the files that are not formatted")
  public abstract val listDifferent: Property<Boolean>

  /** Ignores files Prettier cannot parse, passed as `--ignore-unknown`. */
  @get:Input
  @get:Optional
  @get:Option(option = "ignore-unknown", description = "Ignores files Prettier cannot parse")
  public abstract val ignoreUnknown: Property<Boolean>

  /**
   * Keeps Prettier from failing over a pattern that matches nothing, passed as
   * `--no-error-on-unmatched-pattern`.
   */
  @get:Input
  @get:Optional
  @get:Option(
    option = "no-error-on-unmatched-pattern",
    description = "Does not fail when a pattern matches no file",
  )
  public abstract val noErrorOnUnmatchedPattern: Property<Boolean>

  /** Only formats the files changed since the last run, passed as `--cache`. */
  @get:Input
  @get:Optional
  @get:Option(option = "cache", description = "Only formats the files changed since the last run")
  public abstract val cache: Property<Boolean>

  /** The file Prettier keeps its cache in, passed as `--cache-location`. */
  @get:OutputFile
  @get:Optional
  @get:Option(option = "cache-location", description = "Keeps the cache in this file")
  public abstract val cacheLocation: RegularFileProperty

  init {
    command.convention("prettier")
  }

  override fun contribute(commandLine: CommandLine) {
    super.contribute(commandLine)
    commandLine.flag("--check", check)
    commandLine.flag("--write", write)
    commandLine.flag("--list-different", listDifferent)
    commandLine.flag("--ignore-unknown", ignoreUnknown)
    commandLine.flag(NO_ERROR_ON_UNMATCHED_PATTERN, noErrorOnUnmatchedPattern)
    commandLine.flag("--cache", cache)
    commandLine.option("--cache-location", cacheLocation)
  }

  override fun patternArguments(includes: List<String>, excludes: List<String>): List<String> =
    includes + excludes.map { "!$it" }
}
