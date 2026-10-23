package de.cronn.pnpm.task

import de.cronn.pnpm.internal.task.PnpmTestTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Runs a Vitest test suite through `pnpm exec vitest run`.
 *
 * What a test run is usually varied by is a command line option of the task, so that an ad-hoc run
 * needs no edit to the build script:
 * ```bash
 * ./gradlew :frontend:vitestTest --coverage
 * ./gradlew :frontend:vitestTest --update
 * ./gradlew :frontend:vitestTest --testNamePattern=login --bail=1
 * ```
 *
 * `./gradlew help --task vitestTest` lists them all. Each of them is handed to Vitest as it is;
 * which of them combine is up to Vitest. Anything else Vitest takes goes into the [arguments] of
 * the task, or into the `extraArguments` of the `vitest` extension.
 */
@DisableCachingByDefault(
  because = "Runs a Vitest suite; its effects are not fully described by declared outputs.",
)
public abstract class VitestRunTask : PnpmTestTask() {

  override val configFileOption: String = "--config"

  /** The Vitest configuration file, passed as `--config`. */
  @get:InputFile
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:Option(option = "config", description = "Runs the suite with this configuration file")
  abstract override val configFile: RegularFileProperty

  /** Directory the coverage report goes to, passed as `--coverage.reportsDirectory`. */
  @get:OutputDirectory
  @get:Option(
    option = "coverageReportsDirectory",
    description = "Writes the coverage report to this directory",
  )
  public abstract val coverageReportsDirectory: DirectoryProperty

  /** Collects coverage while the suite runs. */
  @get:Input
  @get:Optional
  @get:Option(option = "coverage", description = "Collects coverage while the suite runs")
  public abstract val coverage: Property<Boolean>

  /** Rewrites the snapshots the suite compares against. */
  @get:Input
  @get:Optional
  @get:Option(option = "update", description = "Rewrites the expected snapshots")
  public abstract val update: Property<Boolean>

  /**
   * Runs only the tests whose name matches this regular expression, passed as `--testNamePattern`.
   */
  @get:Input
  @get:Optional
  @get:Option(
    option = "testNamePattern",
    description = "Runs only the tests whose name matches this regex",
  )
  public abstract val testNamePattern: Property<String>

  /** The directory Vitest looks for test files below, passed as `--dir`. */
  @get:Internal
  @get:Option(option = "dir", description = "Looks for test files below this directory")
  public abstract val dir: DirectoryProperty

  /**
   * The [dir] relative to the [workingDirectory], which is the input it amounts to: the files below
   * it are inputs through the [sourceFiles] already, and fingerprinting all of them would take
   * `node_modules` in when it is the package directory.
   */
  @get:Input
  @get:Optional
  protected val dirPath: Provider<String>
    get() = dir.map {
      it.asFile.relativeToOrSelf(workingDirectory.get().asFile).invariantSeparatorsPath
    }

  /** The file the reporters write their results to, passed as `--outputFile`. */
  @get:OutputFile
  @get:Optional
  @get:Option(option = "outputFile", description = "Writes the test results to this file")
  public abstract val outputFile: RegularFileProperty

  /** Keeps Vitest from failing when it finds no test, passed as `--passWithNoTests`. */
  @get:Input
  @get:Optional
  @get:Option(option = "passWithNoTests", description = "Does not fail when no test is found")
  public abstract val passWithNoTests: Property<Boolean>

  /** The number of failed tests after which Vitest stops, passed as `--bail`. */
  @get:Input
  @get:Optional
  @get:Option(option = "bail", description = "Stops the suite after this number of failed tests")
  public abstract val bail: Property<Int>

  /**
   * `run`, because a bare `vitest` starts the watch mode, which would never hand the build back.
   */
  override val subcommand: String = "run"

  init {
    command.convention("vitest")
  }

  override fun testArguments(): List<String> = buildList {
    // Passed whatever the options say, so that a configuration switching coverage on by itself
    // still writes into the directory this task declares as its output. Read at execution time
    // rather than through a provider feeding an input, which would make the task depend on the
    // task producing coverageReportsDirectory -- this very task.
    add("--coverage.reportsDirectory=${coverageReportsDirectory.get().asFile.absolutePath}")
    if (coverage.getOrElse(false)) add("--coverage")
    if (update.getOrElse(false)) add("--update")
    testNamePattern.orNull?.let { add("--testNamePattern=$it") }
    dir.orNull?.let { add("--dir=${it.asFile.absolutePath}") }
    outputFile.orNull?.let { add("--outputFile=${it.asFile.absolutePath}") }
    if (passWithNoTests.getOrElse(false)) add("--passWithNoTests")
    bail.orNull?.let { add("--bail=$it") }
  }
}
