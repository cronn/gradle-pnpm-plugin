package de.cronn.pnpm.task

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Extracts a self-contained pnpm distribution into [installDirectory]. */
@DisableCachingByDefault(
  because =
    "Extracting a pnpm distribution that already sits in the dependency cache is not worth " +
      "transporting through a build cache."
)
public abstract class PnpmSetupTask : DefaultTask() {

  @get:Inject protected abstract val fileSystemOperations: FileSystemOperations

  @get:Inject protected abstract val archiveOperations: ArchiveOperations

  /**
   * The pnpm distribution archive, normally resolved from the repository the plugin registers, or
   * from the one the build declares in `settings.gradle.kts`.
   *
   * Only the content of the archive matters: it lives in the shared dependency cache, under a path
   * that differs from machine to machine, so the path itself is not part of the input.
   */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  public abstract val distributionArchive: ConfigurableFileCollection

  /** Name of the pnpm executable inside the archive. */
  @get:Input public abstract val executableName: Property<String>

  /**
   * Whether pnpm actually has to be installed. Read by an `onlyIf` spec, which the configuration
   * cache serializes -- so the decision is carried by the task rather than captured in the spec.
   */
  @get:Internal public abstract val required: Property<Boolean>

  /** Directory the archive is extracted into. */
  @get:OutputDirectory public abstract val installDirectory: DirectoryProperty

  init {
    required.convention(true)
  }

  @TaskAction
  public fun install() {
    val archive = resolveArchive()
    logger.info("Extracting {} into {}", archive, installDirectory.get().asFile)
    extract(archive)

    val executable = findExecutable(archive)
    if (!executable.setExecutable(true) && !executable.canExecute()) {
      logger.warn("Could not mark {} as executable", executable)
    }
    logger.info("Installed pnpm at {}", executable)
  }

  private fun resolveArchive(): File {
    val archives = distributionArchive.files
    return when (archives.size) {
      1 -> archives.single()
      0 ->
        throw GradleException(
          "No pnpm distribution archive was resolved. Declare the repository that serves it in " +
            "`settings.gradle.kts`, or point the build at an existing pnpm with " +
            "`pnpm { executable = ... }`."
        )
      else ->
        throw GradleException(
          "Expected a single pnpm distribution archive, but resolved " +
            archives.joinToString(", ") { it.name }
        )
    }
  }

  private fun extract(archive: File) {
    val archiveTree =
      if (archive.name.endsWith(".zip")) {
        archiveOperations.zipTree(archive)
      } else {
        archiveOperations.tarTree(archiveOperations.gzip(archive))
      }

    // The archive is never deleted afterwards: it belongs to the dependency cache now.
    fileSystemOperations.sync { spec ->
      spec.from(archiveTree)
      spec.into(installDirectory)
    }
  }

  /**
   * pnpm currently publishes flat archives, but the layout is not part of any contract, so the
   * executable is searched for instead of assumed at the root.
   */
  private fun findExecutable(archive: File): File {
    val name = executableName.get()
    val root = installDirectory.get().asFile
    val direct = File(root, name)
    if (direct.isFile) {
      return direct
    }

    val nested = root.walkTopDown().firstOrNull { it.isFile && it.name == name }
    if (nested != null) {
      return nested
    }

    val extracted =
      root
        .walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(root).path }
        .sorted()
        .joinToString(", ")
        .ifEmpty { "<nothing>" }
    throw GradleException(
      "Expected a pnpm executable named '$name' after extracting ${archive.name}, " +
        "but the archive contained: $extracted"
    )
  }
}
