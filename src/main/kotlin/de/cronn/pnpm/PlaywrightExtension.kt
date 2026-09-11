package de.cronn.pnpm

import de.cronn.pnpm.internal.extension.PnpmTestExtension
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Configuration of the Playwright tasks, added by [PnpmPlugin] as the `playwright` extension.
 *
 * Enabled by default when the project contains a `playwright.config.*` file. Playwright selects the
 * tests it runs itself, from that file and from the command line options of the task, so no pattern
 * ever reaches it: [includes] and [excludes] only describe the Gradle inputs of `playwrightTest`.
 *
 * [alwaysRerun] defaults to `true` here: a browser suite reaches a backend, a database or a fixture
 * server, and none of those is a Gradle input, so unchanged inputs say nothing about whether the
 * last result still holds. Set it to `false` for a suite that really is a function of the files it
 * runs over.
 */
public abstract class PlaywrightExtension : PnpmTestExtension() {

  /**
   * The browsers `playwrightInstall` downloads, for example `chromium`. Empty by default, which
   * installs the browsers the Playwright configuration asks for.
   */
  public abstract val browsers: ListProperty<String>

  /**
   * Whether `playwrightTest` depends on `playwrightInstall`. Defaults to `true`. Set it to `false`
   * where the browsers are provisioned some other way, by a container image or a CI step.
   */
  public abstract val installBrowsers: Property<Boolean>

  /**
   * Whether `playwrightInstall` also installs the system libraries the browsers need, as
   * `--with-deps`. Defaults to `false`, because it needs root on Linux and is a no-op elsewhere.
   */
  public abstract val installSystemDependencies: Property<Boolean>

  /**
   * Directory Playwright writes the artifacts of a failing test to -- traces, screenshots, videos.
   * Defaults to `build/playwright/test-results`, and is passed as `--output`.
   */
  public abstract val outputDirectory: DirectoryProperty

  /**
   * Directory the HTML reporter writes to. Defaults to `build/reports/playwright`, and is passed in
   * the environment of the task.
   */
  public abstract val reportDirectory: DirectoryProperty

  /** Adds [names] to [browsers], keeping the ones already there. */
  public fun browsers(vararg names: String) {
    browsers.addAll(*names)
  }
}
