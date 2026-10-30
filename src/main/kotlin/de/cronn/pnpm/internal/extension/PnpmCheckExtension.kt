package de.cronn.pnpm.internal.extension

/**
 * Configuration of a Node tool that is handed its patterns on the command line.
 *
 * The patterns have to mean the same to the tool as to Gradle: an exclude naming a directory, for
 * example, needs the trailing globstar an Ant pattern can leave out. A pattern only one of the two
 * understands fails the build.
 */
public abstract class PnpmCheckExtension : PnpmSourceExtension()
