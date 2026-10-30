package de.cronn.pnpm.task

import java.io.File
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider

/**
 * The arguments a pnpm task invokes pnpm with, assembled by every layer of the task hierarchy.
 *
 * The [head] names what runs and leads, the [tail] trails, and everything else forms the body in
 * between. Each part keeps the order its arguments are added in. File locations are passed as
 * absolute paths.
 */
public class CommandLine {

  private val head = mutableListOf<String>()
  private val body = mutableListOf<String>()
  private val tail = mutableListOf<String>()

  /** Adds [arguments] to the head, such as a command, a binary or its configuration file. */
  public fun head(vararg arguments: Any) {
    arguments.mapTo(head, ::render)
  }

  /** Adds [arguments] to the head. */
  public fun head(arguments: Iterable<Any>) {
    arguments.mapTo(head, ::render)
  }

  /** Adds [arguments] to the body as they are. */
  public fun add(vararg arguments: Any) {
    arguments.mapTo(body, ::render)
  }

  /** Adds [arguments] to the body as they are. */
  public fun add(arguments: Iterable<Any>) {
    arguments.mapTo(body, ::render)
  }

  /** Adds [arguments] to the tail, such as operands that have to follow every option. */
  public fun tail(arguments: Iterable<Any>) {
    arguments.mapTo(tail, ::render)
  }

  /** Adds [name] when [enabled] is `true`. */
  public fun flag(name: String, enabled: Provider<Boolean>) {
    if (enabled.getOrElse(false)) body += name
  }

  /** Adds [name] with [value] when it is present, as `name=value` if [joined]. */
  public fun option(name: String, value: Provider<*>, joined: Boolean = false) {
    value.orNull?.let { option(name, it, joined) }
  }

  /** Adds [name] with each of [values], as `name=value` if [joined]. */
  public fun repeated(name: String, values: Provider<out Iterable<*>>, joined: Boolean = false) {
    values.orNull?.forEach { value -> option(name, checkNotNull(value), joined) }
  }

  public fun build(): List<String> = head + body + tail

  private fun option(name: String, value: Any, joined: Boolean) {
    if (joined) body += "$name=${render(value)}" else body += listOf(name, render(value))
  }

  private fun render(value: Any): String =
    when (value) {
      is FileSystemLocation -> value.asFile.absolutePath
      is File -> value.absolutePath
      else -> value.toString()
    }
}
