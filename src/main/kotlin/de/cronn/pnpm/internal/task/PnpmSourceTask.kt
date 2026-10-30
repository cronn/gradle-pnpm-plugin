package de.cronn.pnpm.internal.task

import de.cronn.pnpm.internal.SourcePatterns
import de.cronn.pnpm.task.CommandLine
import de.cronn.pnpm.task.PnpmExecTask
import java.io.File
import javax.inject.Inject
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.work.DisableCachingByDefault

/**
 * Runs a Node tool over a set of sources described by Ant-style patterns.
 *
 * The base of every task the plugin registers for a tool: [PnpmCheckTask] for the tools that are
 * handed their patterns, [PnpmTestTask] for the ones that run a suite, and `TypescriptTask`, which
 * is handed no pattern but is no test task either. Everything the three have in common -- the
 * patterns, the [sourceFiles] they resolve to and the [extraArguments] appended to every invocation
 * -- lives here, so that a pattern means the same thing whichever task it is declared on.
 *
 * The patterns are always the Gradle inputs of the task, so they always have to be ones the Ant
 * matcher of Gradle can resolve; a pattern written for the globber of a tool would find nothing and
 * skip the task instead. Whether the tool is handed them as well is what the subclasses differ in.
 */
@DisableCachingByDefault(
  because =
    "Runs a Node tool over a source set; its effects are not fully described by declared " +
      "outputs.",
)
public abstract class PnpmSourceTask : PnpmExecTask() {

  @get:Inject protected abstract val objects: ObjectFactory

  /**
   * Ant-style patterns of the files this task works on, relative to the [workingDirectory] the tool
   * is invoked in. Defaults to the `includes` of the tool's extension.
   */
  @get:Input public abstract val includes: ListProperty<String>

  /** Ant-style patterns excluded from [includes]. Defaults to the `excludes` of the extension. */
  @get:Input public abstract val excludes: ListProperty<String>

  /**
   * The configuration file the tool is handed, through the [configFileOption] of the tool. Defaults
   * to the configuration file the plugin detected in the project, so that the file deciding whether
   * the tasks of a tool are registered is the one the tool runs with. Unset, the tool searches for
   * its configuration on its own.
   */
  @get:InputFile
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val configFile: RegularFileProperty

  /** The command line option the tool takes its configuration file with. */
  @get:Internal protected open val configFileOption: String = "--config"

  /**
   * The files [includes] and [excludes] resolve to, which are the inputs deciding when this task is
   * up to date. Derived from the patterns; configure those instead.
   *
   * A task whose sources are empty is skipped. The patterns are resolved in a single tree rooted at
   * the [workingDirectory], so a task that writes below it -- into the build directory, say --
   * needs an [excludes] entry keeping its own output out, the way it needs one for `node_modules`.
   *
   * Resolving the inputs is also where the patterns are validated, so that one no resolver
   * understands fails the build instead of leaving the task without a source.
   */
  @get:InputFiles
  @get:SkipWhenEmpty
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public val sourceFiles: FileTree
    get() {
      requireSupportedPatterns()
      val tree = objects.fileTree()
      tree.setDir(workingDirectory.get().asFile)
      tree.setIncludes(includes.get())
      tree.setExcludes(excludes.get())
      return tree
    }

  /**
   * Arguments appended after everything the task contributes itself. Defaults to the
   * `extraArguments` of the tool's extension, so that they apply to every task of this tool.
   */
  @get:Input public abstract val extraArguments: ListProperty<String>

  /**
   * Rejects a pattern that would not describe what it says it does. Every pattern has to be one the
   * Ant matcher of Gradle resolves; a subclass whose tool is handed the patterns adds what that
   * tool requires on top.
   */
  protected open fun requireSupportedPatterns() {
    SourcePatterns.requireResolvable(includes.get(), "includes", path)
    SourcePatterns.requireResolvable(excludes.get(), "excludes", path)
  }

  /**
   * The [configFile] leads, after the subcommand it is an option of; the [extraArguments] trail.
   */
  override fun contribute(commandLine: CommandLine) {
    super.contribute(commandLine)
    configFile.orNull?.let { commandLine.head(configFileArguments(it.asFile)) }
    commandLine.tail(extraArguments.get())
  }

  /** The arguments handing [file] to the tool as its configuration file. */
  protected open fun configFileArguments(file: File): List<Any> = listOf(configFileOption, file)
}
