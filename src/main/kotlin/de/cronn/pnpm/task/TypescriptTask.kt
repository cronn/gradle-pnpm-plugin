package de.cronn.pnpm.task

import de.cronn.pnpm.internal.task.PnpmSourceTask
import java.io.File
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Runs the TypeScript compiler through `pnpm exec tsc`.
 *
 * `tsc` is not handed the patterns, which would make it ignore the `tsconfig.json`: the [includes]
 * and [excludes] only decide when the task is up to date.
 *
 * The common options are command line options of the task as well:
 * ```bash
 * ./gradlew :frontend:compileTypescript --noEmit
 * ./gradlew :frontend:compileTypescript --config=tsconfig.build.json --outDir=dist
 * ```
 *
 * Any other option goes into the [arguments] of the task, or into the `extraArguments` of the
 * `typescript` extension.
 */
@DisableCachingByDefault(
  because = "Runs tsc; its effects are not fully described by declared outputs.",
)
public abstract class TypescriptTask : PnpmSourceTask() {

  override val configFileOption: String = "--project"

  /** Runs `tsc` in build mode, passed as `--build`. */
  @get:Input
  @get:Optional
  @get:Option(option = "build", description = "Builds the project and its references")
  public abstract val build: Property<Boolean>

  /** Deletes the outputs of the build mode, passed as `--clean`. */
  @get:Input
  @get:Optional
  @get:Option(option = "clean", description = "Deletes the outputs of all projects")
  public abstract val clean: Property<Boolean>

  /** Checks the sources without writing any output, passed as `--noEmit`. */
  @get:Input
  @get:Optional
  @get:Option(option = "noEmit", description = "Checks the sources without emitting any output")
  public abstract val noEmit: Property<Boolean>

  /** Directory `tsc` writes its output to, passed as `--outDir`. */
  @get:OutputDirectory
  @get:Optional
  @get:Option(option = "outDir", description = "Writes the output to this directory")
  public abstract val outDir: DirectoryProperty

  init {
    command.convention("tsc")
  }

  /**
   * `--build` leads, because `tsc` only recognizes it as the first argument. Build mode takes no
   * `--project`, but the projects to build as the last arguments, so the [configFile] trails then.
   */
  override fun contribute(commandLine: CommandLine) {
    super.contribute(commandLine)
    val buildMode = build.getOrElse(false)
    if (buildMode) commandLine.head("--build")
    commandLine.flag("--clean", clean)
    commandLine.flag("--noEmit", noEmit)
    commandLine.option("--outDir", outDir)
    if (buildMode) configFile.orNull?.let { commandLine.tail(listOf(it)) }
  }

  override fun configFileArguments(file: File): List<Any> =
    if (build.getOrElse(false)) emptyList() else super.configFileArguments(file)
}
