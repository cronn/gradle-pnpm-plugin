package de.cronn.pnpm

import de.cronn.pnpm.internal.extension.PnpmCheckExtension

/**
 * Configuration of the Prettier check and fix tasks, added by [PnpmPlugin] as the `prettier`
 * extension.
 *
 * Pre-defined tasks are registered only when the project contains a `prettier.config.*` or
 * `.prettierrc*` file.
 */
public abstract class PrettierExtension : PnpmCheckExtension()
