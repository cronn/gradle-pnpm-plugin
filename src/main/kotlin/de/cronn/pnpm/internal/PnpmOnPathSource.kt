package de.cronn.pnpm.internal

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.Serializable
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations

/**
 * Locates a pnpm installation on the `PATH` and determines its version, or produces no value when
 * there is no usable pnpm.
 *
 * Scanning the `PATH` and starting a process are untracked side effects. Wrapping them in a
 * [ValueSource] turns them into a single declared configuration input, so that installing or
 * upgrading pnpm invalidates the configuration cache instead of silently reusing a stale decision.
 */
internal abstract class PnpmOnPathSource : ValueSource<PnpmOnPath, PnpmOnPathSource.Parameters> {

  internal interface Parameters : ValueSourceParameters {
    /** Contents of the `PATH` environment variable, passed in so that changes invalidate here. */
    val searchPath: Property<String>

    /** Candidate executable file names for the current platform. */
    val executableNames: ListProperty<String>
  }

  @get:Inject protected abstract val execOperations: ExecOperations

  override fun obtain(): PnpmOnPath? {
    val candidate =
      parameters.searchPath
        .getOrElse("")
        .split(File.pathSeparator)
        .filter { it.isNotBlank() }
        .asSequence()
        .flatMap { directory ->
          parameters.executableNames.get().asSequence().map { File(directory, it) }
        }
        .firstOrNull { it.isFile } ?: return null

    val standardOutput = ByteArrayOutputStream()
    val result =
      execOperations.exec { spec ->
        spec.commandLine(candidate.absolutePath, "--version")
        spec.standardOutput = standardOutput
        spec.errorOutput = ByteArrayOutputStream()
        spec.isIgnoreExitValue = true
      }
    if (result.exitValue != 0) {
      return null
    }

    val version = standardOutput.toString(StandardCharsets.UTF_8).trim()
    return if (version.isEmpty()) null else PnpmOnPath(candidate.absolutePath, version)
  }
}

/** A pnpm executable found on the `PATH`, together with the version it reports. */
internal data class PnpmOnPath(val executablePath: String, val version: String) : Serializable
