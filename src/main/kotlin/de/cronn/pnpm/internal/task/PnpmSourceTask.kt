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
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Runs a Node tool over the sources the Ant-style [includes] and [excludes] describe.
 *
 * The patterns are the Gradle inputs of the task, so they have to be ones Gradle can resolve,
 * whether the tool is handed them as well or not.
 */
@DisableCachingByDefault(
  because =
    "Runs a Node tool over a source set; its effects are not fully described by declared " +
      "outputs.",
)
public abstract class PnpmSourceTask : PnpmExecTask() {

  @get:Inject protected abstract val objects: ObjectFactory

  /**
   * Ant-style patterns of the sources, relative to the [workingDirectory]. Defaults to the
   * `includes` of the tool's extension.
   */
  @get:Input public abstract val includes: ListProperty<String>

  /** Ant-style patterns excluded from [includes]. Defaults to the `excludes` of the extension. */
  @get:Input public abstract val excludes: ListProperty<String>

  /**
   * The configuration file handed to the tool. Defaults to the one detected in the project; unset,
   * the tool searches for its own.
   */
  @get:InputFile
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:Option(option = "config", description = "Runs the tool with this configuration file")
  public abstract val configFile: RegularFileProperty

  /** The command line option the tool takes its configuration file with. */
  @get:Internal protected open val configFileOption: String = "--config"

  /**
   * The files the patterns resolve to, which decide when the task is up to date. A task without
   * sources is skipped, and a pattern that cannot be resolved fails the build.
   *
   * The patterns are resolved below the [workingDirectory], so a task writing below it needs an
   * [excludes] entry for its own output, the way it needs one for `node_modules`.
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

  /** Arguments after all others. Defaults to the `extraArguments` of the tool's extension. */
  @get:Input public abstract val extraArguments: ListProperty<String>

  /** Rejects a pattern Gradle cannot resolve. */
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
