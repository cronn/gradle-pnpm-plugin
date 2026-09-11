package de.cronn.pnpm.internal.extension

import org.gradle.api.provider.Property

/**
 * Configuration shared by every test tool wired into the Gradle lifecycle by `PnpmPlugin`.
 *
 * A test tool runs a suite rather than inspecting a file set, which is what sets this apart from
 * the source tools deriving from [PnpmCheckExtension] directly: [includes] and [excludes] describe
 * the Gradle inputs of the tasks only, and no pattern reaches the tool itself. Which tests run is
 * the tool's own decision, steered by its configuration file and by the command line options of the
 * task.
 *
 * Every test tool contributes its test task to the `test` lifecycle task of the project. `check`
 * deliberately does not depend on `test`: an end-to-end suite is slow and usually needs a server
 * that the build does not start. Add the dependency where that is not so:
 * ```kotlin
 * tasks.named("check") { dependsOn(tasks.named("test")) }
 * ```
 *
 * `PlaywrightExtension` is the extension of the one test tool the plugin supports today.
 */
public abstract class PnpmTestExtension : PnpmSourceExtension() {

  /**
   * Whether the test tasks of this tool run on every invocation, instead of being skipped when
   * Gradle finds their inputs and outputs unchanged. The extension of each tool documents what it
   * defaults to.
   *
   * A suite whose result depends on something the task does not declare -- a server it talks to, a
   * database it reads -- is not described by its inputs, and only up to date by accident. Setting
   * this to `false` is worth it only for a suite that really is a function of the files it runs
   * over.
   */
  public abstract val alwaysRerun: Property<Boolean>
}
