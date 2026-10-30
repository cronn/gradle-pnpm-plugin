package de.cronn.pnpm

import de.cronn.pnpm.internal.extension.PnpmSourceExtension
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/**
 * Configuration of the TypeScript compiler check, added by [PnpmPlugin] as the `typescript`
 * extension.
 *
 * Pre-defined tasks are registered only when the project contains a `tsconfig.json` file.
 */
public abstract class TypescriptExtension : PnpmSourceExtension() {

  /** Whether `tsc` runs in build mode, as `--build`. */
  public abstract val build: Property<Boolean>

  /** Whether `tsc` checks the sources without writing any output, as `--noEmit`. */
  public abstract val noEmit: Property<Boolean>

  /** Directory `tsc` writes its output to, as `--outDir`. */
  public abstract val outDir: DirectoryProperty
}
