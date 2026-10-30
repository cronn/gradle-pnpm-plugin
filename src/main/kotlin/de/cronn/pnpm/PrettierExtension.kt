package de.cronn.pnpm

import de.cronn.pnpm.internal.extension.PnpmCheckExtension
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/**
 * Configuration of the Prettier check and fix tasks, added by [PnpmPlugin] as the `prettier`
 * extension.
 *
 * Pre-defined tasks are registered only when the project contains a `prettier.config.*` or
 * `.prettierrc*` file.
 */
public abstract class PrettierExtension : PnpmCheckExtension() {

  /** Whether Prettier ignores files it cannot parse, as `--ignore-unknown`. */
  public abstract val ignoreUnknown: Property<Boolean>

  /**
   * Whether Prettier keeps from failing over a pattern that matches nothing, as
   * `--no-error-on-unmatched-pattern`. Defaults to `true`, since a default pattern of the plugin
   * easily matches nothing.
   */
  public abstract val noErrorOnUnmatchedPattern: Property<Boolean>

  /** Whether Prettier only formats the files changed since its last run, as `--cache`. */
  public abstract val cache: Property<Boolean>

  /** The file Prettier keeps its cache in, as `--cache-location`. */
  public abstract val cacheLocation: RegularFileProperty
}
