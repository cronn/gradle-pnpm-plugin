package de.cronn.pnpm.internal.extension

import org.gradle.api.provider.Property

/**
 * Configuration of a Node test tool.
 *
 * The [includes] and [excludes] only decide when the suite is up to date; which tests run is up to
 * the configuration of the tool and the options of the task.
 *
 * `vitestTest` takes part in `test`, and with it in `check`. `playwrightTest` does not, because an
 * end-to-end suite is slow and usually needs a server the build does not start. Add it where it
 * should:
 * ```kotlin
 * tasks.named("test") { dependsOn("playwrightTest") }
 * ```
 */
public abstract class PnpmTestExtension : PnpmSourceExtension() {

  /**
   * Whether the test tasks of this tool run on every invocation, instead of being skipped when
   * their inputs and outputs are unchanged. The extension of each tool documents its default.
   */
  public abstract val alwaysRerun: Property<Boolean>
}
