package de.cronn.pnpm

import de.cronn.pnpm.internal.extension.PnpmCheckExtension

/**
 * Configuration of the ESLint check and fix tasks, added by [PnpmPlugin] as the `eslint` extension.
 *
 * Enabled by default when the project contains an `eslint.config.*` flat config file. The legacy
 * `.eslintrc.*` format is not detected. [includes] are the inputs of `eslintCheck` and `eslintFix`,
 * and the patterns themselves are the operands ESLint is invoked with. [excludes] are passed as
 * `--ignore-pattern`.
 */
public abstract class EslintExtension : PnpmCheckExtension()
