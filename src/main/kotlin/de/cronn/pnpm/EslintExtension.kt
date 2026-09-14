package de.cronn.pnpm

import de.cronn.pnpm.internal.extension.PnpmCheckExtension

/**
 * Configuration of the ESLint check and fix tasks, added by [PnpmPlugin] as the `eslint` extension.
 *
 * Pre-defined tasks are registered only when the project contains an `eslint.config.*` file.
 */
public abstract class EslintExtension : PnpmCheckExtension()
