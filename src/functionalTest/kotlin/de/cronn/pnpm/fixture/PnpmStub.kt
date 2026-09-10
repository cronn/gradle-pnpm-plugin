package de.cronn.pnpm.fixture

import java.io.File
import java.util.Locale

/**
 * A fake pnpm executable that records how it was invoked, so that the functional tests can assert
 * the exact command lines, working directories and environment variables the plugins produce
 * without needing a real pnpm.
 */
class PnpmStub(private val directory: File) {

  private val recordFile = File(directory, "invocations.txt")

  /** Installs the stub and returns its path, to be used as `pnpm.executable`. */
  fun install(exitCode: Int = 0, standardOutput: String = ""): File {
    directory.mkdirs()
    // The lock directories go as well, so that the slots start over from zero.
    directory
      .listFiles()
      .orEmpty()
      .filter { it.name.startsWith("${recordFile.name}.") || it.name == recordFile.name }
      .forEach { it.delete() }
    return if (isWindows) installBatchFile(exitCode, standardOutput)
    else installShellScript(exitCode, standardOutput)
  }

  /** Every recorded invocation, in the order the stub was called. */
  fun invocations(): List<PnpmInvocation> {
    val invocations = mutableListOf<PnpmInvocation>()
    var workingDirectory: String? = null
    val arguments = mutableListOf<String>()
    val environment = mutableMapOf<String, String>()
    recordFiles()
      .flatMap { it.readLines() }
      .forEach { line ->
        when {
          line == "---" -> {
            invocations +=
              PnpmInvocation(workingDirectory.orEmpty(), arguments.toList(), environment.toMap())
            workingDirectory = null
            arguments.clear()
            environment.clear()
          }
          line.startsWith("cwd=") -> workingDirectory = line.removePrefix("cwd=")
          // The shell stub records one argument per line, the batch file the whole command line.
          line.startsWith("arg=") -> arguments += line.removePrefix("arg=")
          line.startsWith("args=") -> arguments += splitCommandLine(line.removePrefix("args="))
          line.startsWith("env=") ->
            line.removePrefix("env=").let { entry ->
              // `set` quotes a value, `set PNPM_TEST_` of the batch stub does not.
              environment[entry.substringBefore('=')] =
                entry.substringAfter('=').removeSurrounding("'")
            }
        }
      }
    return invocations
  }

  /**
   * The numbered per-invocation record files, ordered by the slot each invocation claimed, which is
   * the order the stub was called in.
   */
  private fun recordFiles(): List<File> =
    directory
      .listFiles()
      .orEmpty()
      .mapNotNull { file ->
        file.name
          .substringAfter("${recordFile.name}.", missingDelimiterValue = "")
          .toIntOrNull()
          ?.let { slot -> slot to file }
      }
      .sortedBy { (slot, _) -> slot }
      .map { (_, file) -> file }

  /**
   * The shell stub, built from shell builtins alone: the tests run the build with every `PATH`
   * entry that holds a pnpm removed, which on a machine that installed pnpm system wide takes `env`
   * and `grep` with it.
   *
   * Every invocation records into its own numbered file, claiming the first free slot the same way
   * the batch stub does. Appending to a shared file is not an option: a `printf` of a whole record
   * is not one `write` call in every shell (the bash 3.2 that is `/bin/sh` on macOS flushes the
   * format in pieces), so the lines of one invocation land between those of another as soon as
   * Gradle runs two pnpm tasks in parallel. The slot is claimed by the record redirection itself
   * under `set -C`, and not with `mkdir`, which is no builtin and therefore not on the stripped
   * `PATH`.
   */
  private fun installShellScript(exitCode: Int, standardOutput: String): File {
    val script = File(directory, "pnpm")
    script.writeText(
      """
      #!/bin/sh
      record="cwd=${'$'}(pwd)"
      for argument in "${'$'}@"; do
        record="${'$'}record
      arg=${'$'}argument"
      done
      variables=${'$'}(set | while IFS= read -r variable; do
        # The prefix is matched by cutting it off rather than with `case`, which the bash 3.2 that
        # is /bin/sh on macOS cannot parse inside a command substitution.
        if [ "${'$'}{variable#$ENVIRONMENT_PREFIX}" != "${'$'}variable" ]; then
          printf 'env=%s\n' "${'$'}variable"
        fi
      done)
      if [ -n "${'$'}variables" ]; then
        record="${'$'}record
      ${'$'}variables"
      fi
      # `set -C` makes the redirection fail on a file that exists, so creating the record file and
      # writing it are one step and no two invocations claim the same slot. Sequential invocations
      # claim ascending slots, which is what keeps the recorded order meaningful.
      set -C
      slot=0
      while [ ${'$'}slot -lt $MAX_SLOTS ]; do
        # The error output is redirected before the record file, so that the message a shell
        # prints for a redirection onto an existing file is suppressed as well.
        if printf '%s\n---\n' "${'$'}record" 2>/dev/null > '${recordFile.absolutePath}.'${'$'}slot
        then
          ${if (standardOutput.isEmpty()) "" else "printf '%s\\n' '$standardOutput'"}
          exit $exitCode
        fi
        slot=${'$'}((slot + 1))
      done
      echo "failed to record an invocation next to ${recordFile.absolutePath}" 1>&2
      exit 1
      """
        .trimIndent() + "\n"
    )
    script.setExecutable(true)
    return script
  }

  private fun installBatchFile(exitCode: Int, standardOutput: String): File {
    // Every argument is recorded as one raw command line rather than through %1/shift, because
    // cmd.exe splits batch parameters on '=' as well, which would turn "--max-warnings=0" into two
    // arguments.
    //
    // Every invocation records into its own numbered file, claiming the first free slot the same
    // way the shell stub does. The claim is a `md` of a lock directory next to it, because creating
    // a directory either succeeds or fails as one step: testing a file for existence and then
    // opening it lets two invocations claim the same slot and interleave their records. Sequential
    // invocations claim ascending slots, which is what keeps the recorded order meaningful.
    //
    // The environment variables are appended after the slot is claimed, because `set` needs its
    // own error output suppressed, which cmd.exe does not take inside a redirection block.
    val script = File(directory, "pnpm.bat")
    script.writeText(
      """
      @echo off
      setlocal
      set RECORD=${recordFile.absolutePath}
      set SLOT=0
      :claim
      md "%RECORD%.%SLOT%.lock" 2>nul || goto next
      >>"%RECORD%.%SLOT%" (
        echo cwd=%CD%
        echo args=%*
      )
      goto recorded
      :next
      set /a SLOT+=1
      if %SLOT% lss $MAX_SLOTS goto claim
      echo failed to record an invocation next to %RECORD% 1>&2
      exit /b 1
      :recorded
      for /f "delims=" %%v in ('set $ENVIRONMENT_PREFIX 2^>nul') do >>"%RECORD%.%SLOT%" echo env=%%v
      >>"%RECORD%.%SLOT%" echo ---
      ${if (standardOutput.isEmpty()) "" else "echo $standardOutput"}
      exit /b $exitCode
      """
        .trimIndent()
        .lines()
        // cmd.exe needs CRLF to reliably jump between labels in a batch file.
        .joinToString(separator = "\r\n", postfix = "\r\n")
    )
    return script
  }

  companion object {
    val isWindows: Boolean =
      System.getProperty("os.name").lowercase(Locale.ROOT).startsWith("windows")

    /**
     * Prefix of the environment variables the stub records. Recording only the variables the tests
     * set themselves keeps the record small, and keeps the environment of the machine running the
     * tests out of it.
     */
    const val ENVIRONMENT_PREFIX: String = "PNPM_TEST_"

    /** Upper bound on the numbered record files a stub will try to claim. */
    private const val MAX_SLOTS = 1000

    /**
     * Splits a Windows command line the way a process started from it would: arguments are
     * separated by whitespace, and double quotes group whitespace into a single argument.
     */
    private fun splitCommandLine(commandLine: String): List<String> {
      val arguments = mutableListOf<String>()
      val current = StringBuilder()
      var quoted = false
      var started = false
      commandLine.forEach { character ->
        when {
          character == '"' -> {
            quoted = !quoted
            started = true
          }
          !quoted && character.isWhitespace() -> {
            if (started) arguments += current.toString()
            current.setLength(0)
            started = false
          }
          else -> {
            current.append(character)
            started = true
          }
        }
      }
      if (started) arguments += current.toString()
      return arguments
    }
  }
}

/**
 * A single recorded pnpm invocation. The [environment] holds the variables named with the
 * [PnpmStub.ENVIRONMENT_PREFIX] only.
 */
data class PnpmInvocation(
  val workingDirectory: String,
  val arguments: List<String>,
  val environment: Map<String, String>,
)
