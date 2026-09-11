package de.cronn.pnpm.task

import java.io.File
import javax.inject.Inject
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.work.DisableCachingByDefault

/**
 * Runs the test suite of a Node test tool.
 *
 * This is the test-tool counterpart of [PnpmCheckTask]: the plugin configures every task of a
 * subclass -- [PlaywrightTask] today -- with the
 * [includes][de.cronn.pnpm.PnpmCheckExtension.includes], the
 * [excludes][de.cronn.pnpm.PnpmCheckExtension.excludes], the
 * [extraArguments][de.cronn.pnpm.PnpmCheckExtension.extraArguments], the
 * [alwaysRerun][de.cronn.pnpm.PnpmTestExtension.alwaysRerun] and the
 * [enabled][de.cronn.pnpm.PnpmCheckExtension.enabled] state of the tool's extension, so a task a
 * build script registers behaves like the predefined one and only has to say what is different
 * about it.
 *
 * Unlike a source tool, a test tool is never handed the patterns: which tests run is decided by its
 * configuration file and by the command line options of the task. [includes] and [excludes]
 * therefore only describe the [sourceFiles] Gradle compares to decide whether the suite has to run
 * again.
 */
@DisableCachingByDefault(
  because = "Runs a test suite; its effects are not fully described by declared outputs."
)
public abstract class PnpmTestTask : PnpmExecTask() {

  @get:Inject protected abstract val objects: ObjectFactory

  /**
   * Ant-style patterns of the test sources, relative to the [workingDirectory] the tool is invoked
   * in. Defaults to the `includes` of the tool's extension. Gradle inputs only; no pattern is
   * passed to the tool.
   */
  @get:Input public abstract val includes: ListProperty<String>

  /** Ant-style patterns excluded from [includes]. Defaults to the `excludes` of the extension. */
  @get:Input public abstract val excludes: ListProperty<String>

  /**
   * The files [includes] and [excludes] resolve to, which are the inputs deciding when this task is
   * up to date. Derived from the patterns; configure those instead.
   *
   * A task whose sources are empty is skipped: a suite with no test file to run is nothing to fail
   * over, and the tool would fail over it.
   *
   * Each pattern is resolved in a tree of its own, rooted at the directories the pattern names
   * before its first wildcard -- `tests` for `tests/**/*.ts`. A test task declares where it writes,
   * and those locations sit in the build directory: a single tree rooted at the whole project
   * directory would contain them, and Gradle reads an input containing a task's own output as that
   * task depending on itself. Rooting each pattern where it actually points also keeps the
   * `node_modules` symlink farm out of the snapshot. A pattern that names no directory at all --
   * `*.spec.ts` -- is rooted at the [workingDirectory], and is why [excludes] has to keep the build
   * directory out.
   */
  @get:InputFiles
  @get:SkipWhenEmpty
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public val sourceFiles: FileCollection
    get() {
      val base = workingDirectory.get().asFile
      val excludePatterns = excludes.get()
      val files = objects.fileCollection()
      includes.get().forEach { pattern ->
        val root = patternRoot(pattern)
        val tree = objects.fileTree()
        tree.setDir(File(base, root))
        tree.setIncludes(listOf(pattern.removePrefix(root).removePrefix("/")))
        tree.setExcludes(excludePatterns.map { it.removePrefix(root).removePrefix("/") })
        files.from(tree)
      }
      return files
    }

  /** The leading segments of [pattern] that name a directory rather than matching one. */
  private fun patternRoot(pattern: String): String =
    pattern
      .split('/')
      .dropLast(1)
      .takeWhile { segment -> WILDCARDS.none(segment::contains) }
      .joinToString("/")

  /**
   * Arguments appended after everything the task contributes itself. Defaults to the
   * `extraArguments` of the tool's extension, so that they apply to every task of this tool.
   */
  @get:Input public abstract val extraArguments: ListProperty<String>

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

  private companion object {
    /** The characters that make a path segment a pattern instead of a directory name. */
    val WILDCARDS: List<Char> = listOf('*', '?')
  }
}
