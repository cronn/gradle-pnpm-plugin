package de.cronn.pnpm

import de.cronn.pnpm.internal.extension.PnpmTestExtension
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/**
 * Configuration of the Vitest tasks, added by [PnpmPlugin] as the `vitest` extension.
 *
 * Pre-defined tasks are registered only when the project contains a `vitest.config.*` or a
 * `vite.config.*` file.
 *
 * [alwaysRerun] defaults to `false` here, unlike [PlaywrightExtension]: a unit suite really is a
 * function of the files it runs over, so an unchanged source tree is a good reason to skip it --
 * which matters all the more because `vitestTest` takes part in `check`, and therefore in `build`.
 * Set it to `true` for a suite that reaches something the build does not describe.
 *
 * [coverage], [passWithNoTests], [dir] and [outputFile] are the defaults of every `VitestRunTask`,
 * which a task can override with a value of its own. Unset, the task leaves the option to Vitest.
 */
public abstract class VitestExtension : PnpmTestExtension() {

  /**
   * Directory the coverage report is written to. Defaults to `build/reports/vitest`, and is passed
   * as `--coverage.reportsDirectory`.
   */
  public abstract val coverageReportsDirectory: DirectoryProperty

  /** Whether Vitest collects coverage while the suite runs, as `--coverage`. */
  public abstract val coverage: Property<Boolean>

  /** Whether Vitest keeps from failing when it finds no test, as `--passWithNoTests`. */
  public abstract val passWithNoTests: Property<Boolean>

  /** The directory Vitest looks for test files below, as `--dir`. */
  public abstract val dir: DirectoryProperty

  /** The file the reporters write their results to, as `--outputFile`. */
  public abstract val outputFile: RegularFileProperty
}
