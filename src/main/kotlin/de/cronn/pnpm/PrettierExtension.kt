package de.cronn.pnpm

import de.cronn.pnpm.internal.extension.PnpmCheckExtension

/**
 * Configuration of the Prettier check and fix tasks, added by [PnpmPlugin] as the `prettier`
 * extension.
 *
 * Enabled by default when the project contains a `prettier.config.*` or `.prettierrc*` file.
 * [includes] are the inputs of `prettierCheck` and `prettierFix`, and the patterns themselves are
 * the operands Prettier is invoked with. [excludes] are passed as negated operands.
 */
public abstract class PrettierExtension : PnpmCheckExtension()
