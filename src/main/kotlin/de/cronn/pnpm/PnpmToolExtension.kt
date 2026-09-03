package de.cronn.pnpm

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Configuration shared by every Node tool wired into the Gradle lifecycle by [PnpmPlugin].
 *
 * Each tool has its own extension deriving from this one: [TypescriptExtension],
 * [PrettierExtension] and [EslintExtension].
 */
public abstract class PnpmToolExtension {

  /**
   * Whether the tasks of this tool run and take part in `check` and `fix`.
   *
   * Defaults to whether the project contains a configuration file for the tool; the extension of
   * each tool documents which files those are. Set it explicitly to enable a tool that is
   * configured some other way, or to switch one off.
   */
  public abstract val enabled: Property<Boolean>

  /**
   * Ant-style patterns of files that are inputs of this tool's tasks, in addition to the tool's
   * default patterns.
   */
  public abstract val additionalIncludes: ListProperty<String>

  /**
   * Ant-style patterns excluded from this tool's inputs, in addition to the patterns excluded from
   * every tool, such as `node_modules` and the Gradle build directory.
   */
  public abstract val additionalExcludes: ListProperty<String>

  /** Additional command line arguments appended to this tool's invocations. */
  public abstract val extraArguments: ListProperty<String>

  /** Adds [patterns] to [additionalIncludes]. */
  public fun additionalIncludes(vararg patterns: String) {
    additionalIncludes.addAll(*patterns)
  }

  /** Adds [patterns] to [additionalExcludes]. */
  public fun additionalExcludes(vararg patterns: String) {
    additionalExcludes.addAll(*patterns)
  }

  /** Adds [arguments] to [extraArguments]. */
  public fun extraArguments(vararg arguments: String) {
    extraArguments.addAll(*arguments)
  }
}
