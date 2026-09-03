package de.cronn.pnpm

/**
 * Configuration of the TypeScript compiler check, added by [PnpmPlugin] as the `typescript`
 * extension.
 *
 * Enabled by default when the project contains a `tsconfig.json`. The default inputs of
 * `compileTypescript` are `eslint.config.ts` and `prettier.config.ts`, because those are the
 * sources `tsc --noEmit` type checks that no other tool owns.
 */
public abstract class TypescriptExtension : PnpmToolExtension()
