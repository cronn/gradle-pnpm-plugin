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
   * Ant-style patterns of files that are inputs of this tool's tasks.
   *
   * Defaults to the patterns documented by the extension of the tool. Assigning it replaces those
   * defaults; use [additionalIncludes] to keep them and add to them.
   */
  public abstract val includes: ListProperty<String>

  /** Ant-style patterns that are inputs of this tool's tasks, on top of [includes]. */
  public abstract val additionalIncludes: ListProperty<String>

  /** Ant-style patterns excluded from this tool's inputs. */
  public abstract val excludes: ListProperty<String>

  /** Additional command line arguments appended to this tool's invocations. */
  public abstract val extraArguments: ListProperty<String>

  /** Adds [patterns] to [additionalIncludes]. */
  public fun additionalIncludes(vararg patterns: String) {
    additionalIncludes.addAll(*patterns)
  }

  /** Adds [patterns] to [excludes]. */
  public fun excludes(vararg patterns: String) {
    excludes.addAll(*patterns)
  }

  /** Adds [arguments] to [extraArguments]. */
  public fun extraArguments(vararg arguments: String) {
    extraArguments.addAll(*arguments)
  }
}
