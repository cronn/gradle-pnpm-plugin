package de.cronn.pnpm

import de.cronn.pnpm.internal.extension.PnpmSourceExtension

/**
 * Configuration of the TypeScript compiler check, added by [PnpmPlugin] as the `typescript`
 * extension.
 *
 * Enabled by default when the project contains a `tsconfig.json`. `tsc` takes the files it type
 * checks from the `tsconfig.json`, so for this tool the patterns only describe the Gradle inputs of
 * `compileTypescript` and no pattern reaches the command line.
 */
public abstract class TypescriptExtension : PnpmSourceExtension()
