package de.cronn.pnpm

/**
 * Configuration of the Prettier check and fix tasks, added by [PnpmPlugin] as the `prettier`
 * extension.
 *
 * Enabled by default when the project contains a `prettier.config.*` or `.prettierrc*` file. The
 * default inputs of `prettierCheck` and `prettierFix` are `*.ts`, `*.json` and `*.md`.
 */
public abstract class PrettierExtension : PnpmToolExtension()
