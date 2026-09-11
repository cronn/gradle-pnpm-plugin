package de.cronn.pnpm.internal.test

import org.gradle.api.GradleException

/**
 * The modes Playwright records a trace in, as `--trace`.
 *
 * Playwright rejects an unknown mode itself, but only after the browsers are up and the suite has
 * started, so the typo costs a whole run. [require] rejects it while the arguments are assembled
 * instead.
 */
internal object TraceMode {

  /** The modes, in the order the Playwright documentation lists them. */
  val NAMES: List<String> =
    listOf(
      "on",
      "off",
      "on-first-retry",
      "on-all-retries",
      "retain-on-failure",
      "retain-on-first-failure",
    )

  /** Returns [mode] when Playwright knows it, and throws otherwise. */
  fun require(mode: String, owner: String): String {
    if (mode in NAMES) return mode
    throw GradleException(
      "The trace mode \"$mode\" of $owner is not one Playwright knows. " +
        "Use one of: ${NAMES.joinToString()}."
    )
  }
}
