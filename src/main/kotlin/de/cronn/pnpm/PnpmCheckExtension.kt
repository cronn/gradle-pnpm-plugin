package de.cronn.pnpm

/**
 * Configuration shared by every Node tool that is handed its patterns on the command line, wired
 * into the Gradle lifecycle by [PnpmPlugin].
 *
 * [PrettierExtension] and [EslintExtension] are the extensions of those tools. What sets them apart
 * from the rest of [PnpmSourceExtension] is that their patterns are resolved twice: by Gradle, to
 * the files deciding when a task is up to date, and by the tool, which is handed the patterns
 * rather than the files. They therefore have to be valid in both -- an exclude naming a directory
 * needs a trailing globstar, which an Ant pattern can leave out but a tool cannot -- and a pattern
 * only one of the two understands fails the build.
 */
public abstract class PnpmCheckExtension : PnpmSourceExtension()
