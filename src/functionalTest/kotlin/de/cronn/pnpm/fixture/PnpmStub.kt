package de.cronn.pnpm.fixture

import java.io.File
import java.util.Locale

/**
 * A fake pnpm executable that records how it was invoked, so that the functional tests can assert
 * the exact command lines and working directories the plugins produce without needing a real pnpm.
 */
class PnpmStub(private val directory: File) {

  private val recordFile = File(directory, "invocations.txt")

  /** Installs the stub and returns its path, to be used as `pnpm.executable`. */
  fun install(exitCode: Int = 0, standardOutput: String = ""): File {
    directory.mkdirs()
    recordFile.delete()
    return if (isWindows) installBatchFile(exitCode, standardOutput)
    else installShellScript(exitCode, standardOutput)
  }

  /** Every recorded invocation, in the order the stub was called. */
  fun invocations(): List<PnpmInvocation> {
    if (!recordFile.isFile) {
      return emptyList()
    }

    val invocations = mutableListOf<PnpmInvocation>()
    var workingDirectory: String? = null
    val arguments = mutableListOf<String>()
    recordFile.readLines().forEach { line ->
      when {
        line == "---" -> {
          invocations += PnpmInvocation(workingDirectory.orEmpty(), arguments.toList())
          workingDirectory = null
          arguments.clear()
        }
        line.startsWith("cwd=") -> workingDirectory = line.removePrefix("cwd=")
        line.startsWith("arg=") -> arguments += line.removePrefix("arg=")
      }
    }
    return invocations
  }

  private fun installShellScript(exitCode: Int, standardOutput: String): File {
    val script = File(directory, "pnpm")
    script.writeText(
      """
      #!/bin/sh
      {
        printf 'cwd=%s\n' "${'$'}(pwd)"
        for argument in "${'$'}@"; do printf 'arg=%s\n' "${'$'}argument"; done
        printf -- '---\n'
      } >> '${recordFile.absolutePath}'
      ${if (standardOutput.isEmpty()) "" else "printf '%s\\n' '$standardOutput'"}
      exit $exitCode
      """
        .trimIndent() + "\n"
    )
    script.setExecutable(true)
    return script
  }

  private fun installBatchFile(exitCode: Int, standardOutput: String): File {
    // %* collapses the arguments into one string and mangles quoting, so they are shifted through
    // one at a time. Note the missing space before >>, which would end up in the output.
    val script = File(directory, "pnpm.bat")
    script.writeText(
      """
      @echo off
      set RECORD=${recordFile.absolutePath}
      echo cwd=%CD%>>"%RECORD%"
      :loop
      if "%~1"=="" goto done
      echo arg=%~1>>"%RECORD%"
      shift
      goto loop
      :done
      echo --->>"%RECORD%"
      ${if (standardOutput.isEmpty()) "" else "echo $standardOutput"}
      exit /b $exitCode
      """
        .trimIndent() + "\n"
    )
    return script
  }

  companion object {
    val isWindows: Boolean =
      System.getProperty("os.name").lowercase(Locale.ROOT).startsWith("windows")
  }
}

/** A single recorded pnpm invocation. */
data class PnpmInvocation(val workingDirectory: String, val arguments: List<String>)
