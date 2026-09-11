package de.cronn.pnpm.task

import de.cronn.pnpm.internal.task.PnpmTestTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Runs a Playwright test suite through `pnpm exec playwright test`.
 *
 * What a test run is usually varied by is a command line option of the task, so that an ad-hoc run
 * needs no edit to the build script:
 * ```bash
 * ./gradlew :e2e:playwrightTest --grep=login --update-snapshots
 * ./gradlew :e2e:playwrightTest --filter=tests/login.spec.ts:42
 * ./gradlew :e2e:playwrightTest --ui
 * ```
 *
 * `./gradlew help --task playwrightTest` lists them all. Anything else Playwright takes goes into
 * the [arguments] of the task, or into the `extraArguments` of the `playwright` extension.
 */
@DisableCachingByDefault(
  because = "Runs a Playwright suite; its effects are not fully described by declared outputs."
)
public abstract class PlaywrightTask : PnpmTestTask() {

  /**
   * The Playwright configuration, which decides what the suite is. Defaults to the
   * `playwright.config.*` files of the project.
   */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val configFiles: ConfigurableFileCollection

  /** Directory of the artifacts of a failing test, passed as `--output`. */
  @get:OutputDirectory public abstract val outputDirectory: DirectoryProperty

  /** Directory of the HTML report, passed in the environment of the task. */
  @get:OutputDirectory public abstract val reportDirectory: DirectoryProperty

  /** Opens the Playwright UI mode instead of running the suite once. */
  @get:Input
  @get:Optional
  @get:Option(option = "ui", description = "Runs the suite in the Playwright UI mode")
  public abstract val ui: Property<Boolean>

  /** Runs the browsers with a visible window. */
  @get:Input
  @get:Optional
  @get:Option(option = "headed", description = "Runs the browsers with a visible window")
  public abstract val headed: Property<Boolean>

  /** Rewrites the snapshots the suite compares against. */
  @get:Input
  @get:Optional
  @get:Option(option = "update-snapshots", description = "Rewrites the expected snapshots")
  public abstract val updateSnapshots: Property<Boolean>

  /** Stops after the first failure, as `-x`. */
  @get:Input
  @get:Optional
  @get:Option(option = "fail-fast", description = "Stops the suite after the first failure")
  public abstract val failFast: Property<Boolean>

  /** Runs only the tests whose title matches this regular expression. */
  @get:Input
  @get:Optional
  @get:Option(option = "grep", description = "Runs only the tests whose title matches this regex")
  public abstract val grep: Property<String>

  /**
   * The test filters Playwright takes as its operands: each one is matched against the path of a
   * test file, as a regular expression, and a file matching any of them is run. A filter of the
   * form `<path>:<line>` runs the one test declared on that line.
   */
  @get:Input
  @get:Optional
  @get:Option(
    option = "filter",
    description =
      "Runs only the test files whose path matches this regex, optionally suffixed with " +
        "\":<line>\"; repeat for more than one",
  )
  public abstract val filters: ListProperty<String>

  /** How often each test is repeated. */
  @get:Input
  @get:Optional
  @get:Option(option = "repeat-each", description = "Runs each test this many times")
  public abstract val repeatEach: Property<String>

  init {
    command.convention("playwright")
    arguments.convention(listOf("test"))
    // An interactive run is driven by a person, so it is handed the standard input of the build.
    forwardStandardInput.convention(
      ui.orElse(false).zip(headed.orElse(false)) { ui, headed -> ui || headed }
    )
  }

  /**
   * An interactive run must never be skipped as up to date: there is a person waiting for the
   * window it opens.
   */
  override fun rerunRequested(): Boolean =
    super.rerunRequested() || ui.getOrElse(false) || headed.getOrElse(false)

  override fun testArguments(): List<String> = buildList {
    addAll(filters.getOrElse(emptyList()))
    add("--output=${outputDirectory.get().asFile.absolutePath}")
    if (ui.getOrElse(false)) add("--ui")
    if (headed.getOrElse(false)) add("--headed")
    if (updateSnapshots.getOrElse(false)) add("--update-snapshots")
    // Repeating a test is how a flaky one is hunted down, and the run is over as soon as it fails
    // once -- so asking for the repetitions asks for stopping at the first failure as well.
    if (failFast.getOrElse(false) || repeatEach.isPresent) add("-x")
    grep.orNull?.let { add("--grep=$it") }
    repeatEach.orNull?.let { add("--repeat-each=$it") }
  }
}
