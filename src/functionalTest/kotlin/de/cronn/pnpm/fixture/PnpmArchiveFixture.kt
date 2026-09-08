package de.cronn.pnpm.fixture

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream

/**
 * Creates a local mirror of the pnpm releases, so that the provisioning can be tested against a
 * `file:` URL instead of downloading a real pnpm distribution.
 *
 * The layout has to match the artifact pattern of the repository the plugin declares, because
 * Gradle -- not the plugin -- builds the asset path from the requested coordinates.
 */
object PnpmArchiveFixture {

  /**
   * Writes a pnpm release for [version] into [directory], in the layout the plugin resolves from.
   *
   * @param entries file name to content; defaults to a recording pnpm stub.
   * @return the URL to configure as the pnpm distribution base url.
   */
  fun writeRelease(
    directory: File,
    version: String,
    entries: Map<String, String> = defaultEntries(),
  ): String {
    val releaseDirectory = File(directory, "v$version").apply { mkdirs() }
    val archive = File(releaseDirectory, "pnpm-$IDENTIFIER.$ARCHIVE_EXTENSION")
    if (PnpmStub.isWindows) writeZip(archive, entries) else writeTarGz(archive, entries)
    return directory.toURI().toString()
  }

  /**
   * The platform part of a pnpm release asset name, duplicated from
   * `de.cronn.pnpm.internal.PnpmPlatform` -- which is `internal`, so it is not visible from this
   * source set. `PnpmPlatform` remains the source of truth; this only has to agree with it for the
   * platform the tests actually run on.
   */
  private val IDENTIFIER: String = buildString {
    append(
      when {
        PnpmStub.isWindows -> "win32"
        System.getProperty("os.name").orEmpty().lowercase().startsWith("mac") -> "darwin"
        else -> "linux"
      }
    )
    append('-')
    append(
      when (System.getProperty("os.arch").orEmpty().lowercase()) {
        "aarch64",
        "arm64" -> "arm64"
        else -> "x64"
      }
    )
  }

  private val ARCHIVE_EXTENSION: String = if (PnpmStub.isWindows) "zip" else "tar.gz"

  /** A pnpm stub that logs its arguments next to itself, mirroring [PnpmStub]. */
  fun defaultEntries(): Map<String, String> =
    mapOf(
      "pnpm" to
        """
        #!/bin/sh
        echo "installed pnpm called with: ${'$'}@"
        """
          .trimIndent() + "\n",
      "pnpm.exe" to "not a real executable\n",
    )

  private fun writeTarGz(archive: File, entries: Map<String, String>) {
    TarArchiveOutputStream(GzipCompressorOutputStream(archive.outputStream().buffered())).use {
      output ->
      entries.forEach { (name, content) ->
        val bytes = content.toByteArray()
        val entry = TarArchiveEntry(name)
        entry.size = bytes.size.toLong()
        entry.mode = EXECUTABLE_MODE
        output.putArchiveEntry(entry)
        output.write(bytes)
        output.closeArchiveEntry()
      }
    }
  }

  private fun writeZip(archive: File, entries: Map<String, String>) {
    ZipOutputStream(archive.outputStream().buffered()).use { output ->
      entries.forEach { (name, content) ->
        output.putNextEntry(ZipEntry(name))
        output.write(content.toByteArray())
        output.closeEntry()
      }
    }
  }

  private const val EXECUTABLE_MODE = 0b111_101_101
}
