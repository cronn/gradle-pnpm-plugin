package de.cronn.pnpm.internal.extension

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty

/**
 * Configuration shared by every Node tool.
 *
 * The properties of an extension are the defaults of every task of the tool, which a task can
 * override with a value of its own. Unset, the task leaves the option to the tool.
 */
public abstract class PnpmSourceExtension {

  /**
   * Ant-style patterns of the sources, which decide when the tasks of this tool are up to date.
   * ESLint and Prettier are handed them as well. Defaults to the patterns of the tool; assigning it
   * replaces those, while the [includes] method adds to them.
   */
  public abstract val includes: ListProperty<String>

  /** Ant-style patterns excluded from this tool's inputs. */
  public abstract val excludes: ListProperty<String>

  /**
   * The configuration file handed to the tool. Defaults to the one detected in the project, which
   * also decides whether the predefined tasks are registered.
   */
  public abstract val configFile: RegularFileProperty

  /** Additional command line arguments appended to this tool's invocations. */
  public abstract val extraArguments: ListProperty<String>

  /** Adds [patterns] to [includes], keeping the patterns already there. */
  public fun includes(vararg patterns: String) {
    includes.addAll(*patterns)
  }

  /** Adds [patterns] to [includes], keeping the patterns already there. */
  public fun includes(patterns: Iterable<String>) {
    includes.addAll(patterns)
  }

  /** Adds [patterns] to [excludes]. */
  public fun excludes(vararg patterns: String) {
    excludes.addAll(*patterns)
  }

  /** Adds [patterns] to [excludes]. */
  public fun excludes(patterns: Iterable<String>) {
    excludes.addAll(patterns)
  }

  /** Adds [arguments] to [extraArguments]. */
  public fun extraArguments(vararg arguments: String) {
    extraArguments.addAll(*arguments)
  }
}
