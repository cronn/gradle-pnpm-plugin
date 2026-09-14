package de.cronn.pnpm

import de.cronn.pnpm.internal.extension.PnpmSourceExtension

/**
 * Configuration of the TypeScript compiler check, added by [PnpmPlugin] as the `typescript`
 * extension.
 *
 * Pre-defined tasks are registered only when the project contains a `tsconfig.json` file.
 */
public abstract class TypescriptExtension : PnpmSourceExtension()
