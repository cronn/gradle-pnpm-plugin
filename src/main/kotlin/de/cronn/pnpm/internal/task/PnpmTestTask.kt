package de.cronn.pnpm.internal.task

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault

/**
 * Runs the test suite of a Node test tool.
 *
 * The tool is not handed the patterns: its configuration and the options of the task decide which
 * tests run, and the patterns only when the suite is up to date.
 */
@DisableCachingByDefault(
  because = "Runs a test suite; its effects are not fully described by declared outputs.",
)
public abstract class PnpmTestTask : PnpmSourceTask() {

  /**
   * Whether this task runs on every invocation. Defaults to the `alwaysRerun` of the extension.
   * Carried by the task, because the `upToDateWhen` spec reading it must not capture anything.
   */
  @get:Internal public abstract val alwaysRerun: Property<Boolean>

  /**
   * Whether this run has to happen whatever Gradle makes of the inputs: because it was asked for
   * with [alwaysRerun], or because it is interactive and there is a person waiting for it.
   */
  public open fun rerunRequested(): Boolean = alwaysRerun.get()
}
