package de.cronn.pnpm.task

import de.cronn.pnpm.internal.task.PnpmTestTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
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
  because = "Runs a Vitest suite; its effects are not fully described by declared outputs."
)
public abstract class VitestTask : PnpmTestTask() {

  /**
   * The Vitest configuration, which decides what the suite is. Defaults to the `vitest.config.*`
   * files of the project.
   */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val configFiles: ConfigurableFileCollection

  /** Directory the coverage report goes to, passed as `--coverage.reportsDirectory`. */
  @get:OutputDirectory public abstract val reportDirectory: DirectoryProperty

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

  init {
    command.convention("vitest")
    // `run`, because a bare `vitest` starts the watch mode, which would never hand the build back.
    arguments.convention(listOf("run"))
  }

  override fun testArguments(): List<String> = buildList {
    // Passed whatever the options say, so that a configuration switching coverage on by itself
    // still writes into the directory this task declares as its output. Read at execution time
    // rather than through a provider feeding an input, which would make the task depend on the
    // task producing reportDirectory -- this very task.
    add("--coverage.reportsDirectory=${reportDirectory.get().asFile.absolutePath}")
    if (coverage.getOrElse(false)) add("--coverage")
    if (update.getOrElse(false)) add("--update")
  }
}
