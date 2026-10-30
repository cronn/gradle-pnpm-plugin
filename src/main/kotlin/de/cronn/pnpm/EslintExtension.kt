package de.cronn.pnpm

import de.cronn.pnpm.internal.extension.PnpmCheckExtension
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/**
 * Configuration of the ESLint check and fix tasks, added by [PnpmPlugin] as the `eslint` extension.
 *
 * Pre-defined tasks are registered only when the project contains an `eslint.config.*` file.
 */
public abstract class EslintExtension : PnpmCheckExtension() {

  /**
   * The number of warnings above which ESLint fails, as `--max-warnings`. Defaults to `0`, so that
   * a warning fails the build.
   */
  public abstract val maxWarnings: Property<Int>

  /**
   * Whether ESLint keeps from failing over a pattern that matches nothing, as
   * `--no-error-on-unmatched-pattern`. Defaults to `true`, since a default pattern of the plugin
   * easily matches nothing.
   */
  public abstract val noErrorOnUnmatchedPattern: Property<Boolean>

  /** Whether ESLint only lints the files changed since its last run, as `--cache`. */
  public abstract val cache: Property<Boolean>

  /** The file ESLint keeps its cache in, as `--cache-location`. */
  public abstract val cacheLocation: RegularFileProperty

  /** The file ESLint keeps its suppressions in, as `--suppressions-location`. */
  public abstract val suppressionsLocation: RegularFileProperty

  /** Whether ESLint keeps from failing when it is given no pattern, as `--pass-on-no-patterns`. */
  public abstract val passOnNoPatterns: Property<Boolean>
}
