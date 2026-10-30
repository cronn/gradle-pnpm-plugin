package de.cronn.pnpm.internal

import java.io.File

/**
 * Records that a task completed, giving a task whose real effects lie outside the project an output
 * to be up to date about.
 */
internal fun writeStamp(file: File, text: String) {
  file.parentFile.mkdirs()
  file.writeText(text)
}
