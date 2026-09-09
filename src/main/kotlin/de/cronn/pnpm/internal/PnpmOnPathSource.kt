package de.cronn.pnpm.internal

import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Locates a pnpm installation on the `PATH`, or produces no value when there is none.
 *
 * Scanning the `PATH` is an untracked side effect. Wrapping it in a [ValueSource] turns it into a
 * single declared configuration input, so that installing or removing pnpm invalidates the
 * configuration cache instead of silently reusing a stale decision.
 */
internal abstract class PnpmOnPathSource : ValueSource<String, PnpmOnPathSource.Parameters> {

  internal interface Parameters : ValueSourceParameters {
    /** Contents of the `PATH` environment variable, passed in so that changes invalidate here. */
    val searchPath: Property<String>

    /** Operating system the pnpm executable names are derived from. */
    val osName: Property<String>

    /** CPU architecture the pnpm executable names are derived from. */
    val osArch: Property<String>
  }

  override fun obtain(): String? =
    PnpmPlatform(parameters.osName.get(), parameters.osArch.get())
      .findPnpmOnPath(parameters.searchPath.orNull)
      ?.absolutePath
}
