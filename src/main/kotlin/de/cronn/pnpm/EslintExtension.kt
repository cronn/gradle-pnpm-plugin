package de.cronn.pnpm

/**
 * Configuration of the ESLint check and fix tasks, added by [PnpmPlugin] as the `eslint` extension.
 *
 * Enabled by default when the project contains an `eslint.config.*` flat config file. The legacy
 * `.eslintrc.*` format is not detected. [includes] defaults to `*.ts`, `src/**/*.ts` and
 * `src/**/*.tsx`; the files it resolves to are both the inputs of `eslintCheck` and `eslintFix` and
 * the operands ESLint is invoked with.
 */
public abstract class EslintExtension : PnpmToolExtension()
