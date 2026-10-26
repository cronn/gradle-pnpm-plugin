package de.cronn.pnpm.task

import de.cronn.pnpm.internal.task.PnpmSourceTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Runs the TypeScript compiler through `pnpm exec tsc`.
 *
 * Unlike a `PnpmCheckTask`, `tsc` is not handed its sources: it takes the files it compiles from
 * the `tsconfig.json` the task hands it as `--project` -- or as the project to build, in build mode
 * -- and naming them on the command line would make it ignore that file. The [includes] and
 * [excludes] of the task therefore only describe its Gradle inputs, which is what decides when it
 * is up to date -- the same as for a `PnpmTestTask`, and why this is a plain [PnpmSourceTask].
 *
 * The common options of `tsc` are command line options of the task as well, so that an ad-hoc run
 * needs no edit to the build script:
 * ```bash
 * ./gradlew :frontend:compileTypescript --noEmit
 * ./gradlew :frontend:compileTypescript --project=tsconfig.build.json --outDir=dist
 * ```
 *
 * Each of them is handed to `tsc` as it is; which of them combine is up to `tsc`. Anything else
 * `tsc` takes goes into the [arguments] of the task, or into the `extraArguments` of the
 * `typescript` extension.
 */
@DisableCachingByDefault(
  because = "Runs tsc; its effects are not fully described by declared outputs.",
)
public abstract class TypescriptTask : PnpmSourceTask() {

  override val configFileOption: String = "--project"

  /** The `tsconfig.json` handed to `tsc` as `--project`, or as the project to [build]. */
  @get:InputFile
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:Option(option = "project", description = "Compiles the project of this tsconfig file")
  abstract override val configFile: RegularFileProperty

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
  override fun commandArguments(): List<String> = buildList {
    val buildMode = build.getOrElse(false)
    if (buildMode) add("--build") else addAll(leadingArguments())
    if (clean.getOrElse(false)) add("--clean")
    if (noEmit.getOrElse(false)) add("--noEmit")
    outDir.orNull?.let { addAll(listOf("--outDir", it.asFile.absolutePath)) }
    addAll(arguments.get())
    addAll(extraArguments.get())
    if (buildMode) configFile.orNull?.let { add(it.asFile.absolutePath) }
  }
}
