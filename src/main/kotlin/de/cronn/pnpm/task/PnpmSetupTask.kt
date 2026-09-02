package de.cronn.pnpm.task

import java.io.File
import java.math.BigInteger
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Downloads a self-contained pnpm distribution and extracts it into [installDirectory]. */
@DisableCachingByDefault(
  because =
    "Downloading and extracting a pnpm distribution is not worth transporting through a build cache."
)
public abstract class PnpmSetupTask : DefaultTask() {

  @get:Inject protected abstract val fileSystemOperations: FileSystemOperations

  @get:Inject protected abstract val archiveOperations: ArchiveOperations

  /** URL of the pnpm release archive to download. */
  @get:Input public abstract val archiveUrl: Property<String>

  /** Expected SHA-256 checksum of the archive. Verified after download when present. */
  @get:Input @get:Optional public abstract val archiveSha256: Property<String>

  /** Name of the pnpm executable inside the archive. */
  @get:Input public abstract val executableName: Property<String>

  /**
   * Whether pnpm actually has to be downloaded. Read by an `onlyIf` spec, which the configuration
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
    val url = archiveUrl.get()
    val archive = File(temporaryDir, url.substringAfterLast('/'))

    download(url, archive)
    verifyChecksum(url, archive)
    extract(archive)
    archive.delete()

    val executable = findExecutable()
    if (!executable.setExecutable(true) && !executable.canExecute()) {
      logger.warn("Could not mark {} as executable", executable)
    }
    logger.info("Installed pnpm at {}", executable)
  }

  private fun download(url: String, archive: File) {
    logger.lifecycle("Downloading $url")
    // Downloaded to a side file first: an aborted download must not leave a truncated archive
    // behind that a later invocation would happily try to extract.
    val partial = File(archive.parentFile, "${archive.name}.part")
    partial.delete()

    val connection = URI(url).toURL().openConnection()
    connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
    connection.readTimeout = READ_TIMEOUT_MILLIS
    try {
      connection.getInputStream().use { input ->
        partial.outputStream().use { output -> input.copyTo(output) }
      }
    } catch (e: java.io.IOException) {
      partial.delete()
      throw GradleException("Failed to download pnpm from $url", e)
    }

    archive.delete()
    if (!partial.renameTo(archive)) {
      throw GradleException("Failed to move $partial to $archive")
    }
  }

  private fun verifyChecksum(url: String, archive: File) {
    val expected = archiveSha256.orNull?.lowercase() ?: return
    val digest = MessageDigest.getInstance("SHA-256")
    archive.inputStream().use { input ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
      }
    }
    val actual = BigInteger(1, digest.digest()).toString(HEX_RADIX).padStart(SHA256_LENGTH, '0')
    if (actual != expected) {
      archive.delete()
      throw GradleException(
        "Checksum mismatch for $url: expected SHA-256 $expected but was $actual"
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

    fileSystemOperations.sync { spec ->
      spec.from(archiveTree)
      spec.into(installDirectory)
    }
  }

  /**
   * pnpm currently publishes flat archives, but the layout is not part of any contract, so the
   * executable is searched for instead of assumed at the root.
   */
  private fun findExecutable(): File {
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
      "Expected a pnpm executable named '$name' after extracting ${archiveUrl.get()}, " +
        "but the archive contained: $extracted"
    )
  }

  private companion object {
    const val CONNECT_TIMEOUT_MILLIS = 30_000
    const val READ_TIMEOUT_MILLIS = 120_000
    const val HEX_RADIX = 16
    const val SHA256_LENGTH = 64
  }
}
