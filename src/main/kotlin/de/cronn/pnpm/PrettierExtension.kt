package de.cronn.pnpm

/**
 * Configuration of the Prettier check and fix tasks, added by [PnpmPlugin] as the `prettier`
 * extension.
 *
 * Enabled by default when the project contains a `prettier.config.*` or `.prettierrc*` file.
 * [includes] defaults to `*.ts`, `src/**/*.ts`, `src/**/*.tsx`, `*.json` and `*.md`; the files it
 * resolves to are both the inputs of `prettierCheck` and `prettierFix` and the operands Prettier is
 * invoked with.
 */
public abstract class PrettierExtension : PnpmToolExtension()
