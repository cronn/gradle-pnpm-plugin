package de.cronn.pnpm

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Configuration shared by every Node tool wired into the Gradle lifecycle by [PnpmPlugin].
 *
 * Each tool has its own extension deriving from this one: [TypescriptExtension],
 * [PrettierExtension] and [EslintExtension].
 */
public abstract class PnpmCheckExtension {

  /**
   * Whether the tasks of this tool run and take part in `check` and `fix`.
   *
   * Defaults to whether the project contains a configuration file for the tool; the extension of
   * each tool documents which files those are. Set it explicitly to enable a tool that is
   * configured some other way, or to switch one off.
   */
  public abstract val enabled: Property<Boolean>

  /**
   * Ant-style patterns of files that are inputs of this tool's tasks, and which the tool itself is
   * invoked with.
   *
   * Defaults to the patterns documented by the extension of the tool. Assigning it replaces those
   * defaults; the [includes] method adds to them.
   *
   * The patterns are resolved twice: by Gradle, to the files deciding when a task is up to date,
   * and by the tool, which is handed the patterns rather than the files. They therefore have to be
   * valid in both, which the README describes in more detail. A pattern only one of the two
   * understands fails the build when the task resolves its inputs.
   */
  public abstract val includes: ListProperty<String>

  /**
   * Ant-style patterns excluded from this tool's inputs, passed on in whatever way the tool
   * supports. An exclude naming a directory needs a trailing globstar, which an Ant pattern can
   * leave out but a tool cannot.
   */
  public abstract val excludes: ListProperty<String>

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
