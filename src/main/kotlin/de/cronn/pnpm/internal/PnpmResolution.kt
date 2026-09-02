package de.cronn.pnpm.internal

import org.gradle.api.provider.Provider

/**
 * The pnpm executable resolved by the base plugin, shared with the other plugins of this build
 * through the project's extension container.
 *
 * This is not part of the public DSL: it carries derived values, not user configuration.
 */
internal class PnpmResolution(
  val executable: Provider<String>,
  val usesManagedPnpm: Provider<Boolean>,
  val executableName: String,
)
