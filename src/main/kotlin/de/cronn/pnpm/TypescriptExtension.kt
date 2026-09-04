package de.cronn.pnpm

/**
 * Configuration of the TypeScript compiler check, added by [PnpmPlugin] as the `typescript`
 * extension.
 *
 * Enabled by default when the project contains a `tsconfig.json`. [includes] defaults to `*.ts`,
 * `src/**/*.ts` and `src/**/*.tsx`. `tsc` takes the files it type checks from the `tsconfig.json`,
 * so for this tool the patterns only describe the Gradle inputs of `compileTypescript`.
 */
public abstract class TypescriptExtension : PnpmToolExtension()
