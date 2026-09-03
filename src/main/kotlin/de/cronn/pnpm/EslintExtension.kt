package de.cronn.pnpm

/**
 * Configuration of the ESLint check and fix tasks, added by [PnpmPlugin] as the `eslint` extension.
 *
 * Enabled by default when the project contains an `eslint.config.*` flat config file. The legacy
 * `.eslintrc.*` format is not detected. The default inputs of `eslintCheck` and `eslintFix` are
 * `*.ts`.
 */
public abstract class EslintExtension : PnpmToolExtension()
