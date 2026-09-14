package de.cronn.pnpm

import de.cronn.pnpm.internal.extension.PnpmTestExtension
import org.gradle.api.file.DirectoryProperty

/**
 * Configuration of the Vitest tasks, added by [PnpmPlugin] as the `vitest` extension.
 *
 * Pre-defined tasks are registered only when the project contains a `vitest.config.*` file.
 *
 * [alwaysRerun] defaults to `false` here, unlike [PlaywrightExtension]: a unit suite really is a
 * function of the files it runs over, so an unchanged source tree is a good reason to skip it --
 * which matters all the more because `vitestTest` takes part in `check`, and therefore in `build`.
 * Set it to `true` for a suite that reaches something the build does not describe.
 */
public abstract class VitestExtension : PnpmTestExtension() {

  /**
   * Directory the coverage report is written to. Defaults to `build/reports/vitest`, and is passed
   * as `--coverage.reportsDirectory`.
   */
  public abstract val reportDirectory: DirectoryProperty
}
