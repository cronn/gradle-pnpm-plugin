package de.cronn.pnpm.internal

import org.gradle.api.provider.Provider

/** The pnpm executable a project invokes, derived from its `pnpm` extension. */
internal class PnpmResolution(
  val executable: Provider<String>,
  val usesManagedPnpm: Provider<Boolean>,
)
