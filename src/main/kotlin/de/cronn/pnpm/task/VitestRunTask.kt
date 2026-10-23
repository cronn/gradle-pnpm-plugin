package de.cronn.pnpm.task

import de.cronn.pnpm.internal.task.PnpmTestTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
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
 * ```
 *
 * `./gradlew help --task vitestTest` lists them all. Anything else Vitest takes goes into the
 * [arguments] of the task, or into the `extraArguments` of the `vitest` extension.
 */
@DisableCachingByDefault(
  because = "Runs a Vitest suite; its effects are not fully described by declared outputs.",
)
public abstract class VitestRunTask : PnpmTestTask() {

  override val configFileOption: String = "--config"

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
  }
}
