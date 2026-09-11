package de.cronn.pnpm.task

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault

/**
 * Runs the test suite of a Node test tool.
 *
 * One of the three [PnpmSourceTask] kinds, and the counterpart of [PnpmCheckTask]: the plugin
 * configures every task of a subclass -- [PlaywrightTask] today -- with the
 * [includes][de.cronn.pnpm.PnpmSourceExtension.includes], the
 * [excludes][de.cronn.pnpm.PnpmSourceExtension.excludes], the
 * [extraArguments][de.cronn.pnpm.PnpmSourceExtension.extraArguments], the
 * [alwaysRerun][de.cronn.pnpm.PnpmTestExtension.alwaysRerun] and the
 * [enabled][de.cronn.pnpm.PnpmSourceExtension.enabled] state of the tool's extension, so a task a
 * build script registers behaves like the predefined one and only has to say what is different
 * about it.
 *
 * Like [TypescriptTask] and unlike a [PnpmCheckTask], a test tool is never handed the patterns:
 * which tests run is decided by its configuration file and by the command line options of the task.
 * [includes] and [excludes] therefore only describe the [sourceFiles] Gradle compares to decide
 * whether the suite has to run again. A test task declares where it writes, and those locations sit
 * in the build directory, so its [excludes] have to keep the build directory out of the sources the
 * way they keep `node_modules` out.
 */
@DisableCachingByDefault(
  because = "Runs a test suite; its effects are not fully described by declared outputs."
)
public abstract class PnpmTestTask : PnpmSourceTask() {

  /**
   * Whether this task runs on every invocation. Defaults to the `alwaysRerun` of the extension.
   *
   * Read by an `upToDateWhen` spec, which the configuration cache serializes -- so the decision is
   * carried by the task rather than captured in the spec.
   */
  @get:Internal public abstract val alwaysRerun: Property<Boolean>

  /**
   * Whether this run has to happen whatever Gradle makes of the inputs: because it was asked for
   * with [alwaysRerun], or because it is interactive and there is a person waiting for it.
   */
  public open fun rerunRequested(): Boolean = alwaysRerun.get()

  /** The arguments of the test command, which subclasses assemble from their own configuration. */
  protected abstract fun testArguments(): List<String>

  final override fun commandArguments(): List<String> =
    arguments.get() + testArguments() + extraArguments.get()
}
