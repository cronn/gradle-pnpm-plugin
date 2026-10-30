package de.cronn.pnpm.task

import de.cronn.pnpm.internal.task.PnpmTestTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Runs a Vitest test suite through `pnpm exec vitest run`.
 *
 * The common options are command line options of the task as well:
 * ```bash
 * ./gradlew :frontend:vitestTest --coverage
 * ./gradlew :frontend:vitestTest --update
 * ./gradlew :frontend:vitestTest --testNamePattern=login --bail=1
 * ```
 *
 * Any other option goes into the [arguments] of the task, or into the `extraArguments` of the
 * `vitest` extension.
 */
@DisableCachingByDefault(
  because = "Runs a Vitest suite; its effects are not fully described by declared outputs.",
)
public abstract class VitestRunTask : PnpmTestTask() {

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

  override fun contribute(commandLine: CommandLine) {
    super.contribute(commandLine)
    // Ahead of any --coverage.* option: Vitest parses a bare --coverage as the whole coverage
    // object, so after one it would drop the options set before it.
    commandLine.flag("--coverage", coverage)
    // Passed whatever the options say, so that a configuration switching coverage on by itself
    // still writes into the directory this task declares as its output. Read at execution time
    // rather than through a provider feeding an input, which would make the task depend on the
    // task producing coverageReportsDirectory -- this very task.
    commandLine.option("--coverage.reportsDirectory", coverageReportsDirectory, joined = true)
    commandLine.flag("--update", update)
    commandLine.option("--testNamePattern", testNamePattern, joined = true)
    commandLine.option("--dir", dir, joined = true)
    commandLine.option("--outputFile", outputFile, joined = true)
    commandLine.flag("--passWithNoTests", passWithNoTests)
    commandLine.option("--bail", bail, joined = true)
  }
}
