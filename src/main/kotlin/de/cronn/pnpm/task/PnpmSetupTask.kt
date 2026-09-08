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
  because = "Extracting a pnpm distribution is not worth transporting through a build cache."
)
public abstract class PnpmSetupTask : DefaultTask() {

  @get:Inject protected abstract val fileSystemOperations: FileSystemOperations

  @get:Inject protected abstract val archiveOperations: ArchiveOperations

  /**
   * The pnpm distribution archive to extract, downloaded by Gradle from the pnpm distribution
   * repository.
   *
   * A file collection rather than a single file, because that is what resolving a configuration
   * yields, and because it is empty when pnpm does not have to be provisioned at all. Only the file
   * name is fingerprinted: the archive lives in Gradle's module cache, whose location is machine
   * specific, while its contents are hashed either way.
   */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  public abstract val distributionArchive: ConfigurableFileCollection

  /**
   * Format of the archive, `tar.gz` or `zip`. Declared rather than derived from the file name,
   * which a mirror is free to change.
   */
  @get:Input public abstract val archiveExtension: Property<String>

  /** Name of the pnpm executable inside the archive. */
  @get:Input public abstract val executableName: Property<String>

  /**
   * Whether pnpm actually has to be provisioned. Read by an `onlyIf` spec, which the configuration
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
    val archive = singleArchive()
    extract(archive)

    val executable = findExecutable(archive)
    if (!executable.setExecutable(true) && !executable.canExecute()) {
      logger.warn("Could not mark {} as executable", executable)
    }
    logger.info("Installed pnpm at {}", executable)
  }

  private fun singleArchive(): File {
    val archives = distributionArchive.files
    if (archives.size != 1) {
      throw GradleException(
        "Expected exactly one pnpm distribution archive to extract, but resolved " +
          archives.joinToString(", ").ifEmpty { "none" }
      )
    }
    return archives.single()
  }

  private fun extract(archive: File) {
    logger.info("Extracting {} into {}", archive, installDirectory.get())

    val archiveTree =
      if (archiveExtension.get() == ZIP_EXTENSION) {
        archiveOperations.zipTree(archive)
      } else {
        archiveOperations.tarTree(archiveOperations.gzip(archive))
      }

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
      "Expected a pnpm executable named '$name' after extracting $archive, " +
        "but the archive contained: $extracted"
    )
  }

  private companion object {
    const val ZIP_EXTENSION = "zip"
  }
}
