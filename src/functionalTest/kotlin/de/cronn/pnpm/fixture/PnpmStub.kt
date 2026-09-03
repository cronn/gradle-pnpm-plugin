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
    recordFiles().forEach { it.delete() }
    return if (isWindows) installBatchFile(exitCode, standardOutput)
    else installShellScript(exitCode, standardOutput)
  }

  /** Every recorded invocation, in the order the stub was called. */
  fun invocations(): List<PnpmInvocation> {
    val invocations = mutableListOf<PnpmInvocation>()
    var workingDirectory: String? = null
    val arguments = mutableListOf<String>()
    recordFiles()
      .flatMap { it.readLines() }
      .forEach { line ->
        when {
          line == "---" -> {
            invocations += PnpmInvocation(workingDirectory.orEmpty(), arguments.toList())
            workingDirectory = null
            arguments.clear()
          }
          line.startsWith("cwd=") -> workingDirectory = line.removePrefix("cwd=")
          // The shell stub records one argument per line, the batch file the whole command line.
          line.startsWith("arg=") -> arguments += line.removePrefix("arg=")
          line.startsWith("args=") -> arguments += splitCommandLine(line.removePrefix("args="))
        }
      }
    return invocations
  }

  /**
   * The record files in the order they were written: the single file the shell stub appends to, or
   * the numbered per-invocation files of the batch stub, ordered by the slot each invocation
   * claimed.
   */
  private fun recordFiles(): List<File> {
    val slots =
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
    return listOfNotNull(recordFile.takeIf { it.isFile }) + slots
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
    // Every argument is recorded as one raw command line rather than through %1/shift, because
    // cmd.exe splits batch parameters on '=' as well, which would turn "--max-warnings=0" into two
    // arguments.
    //
    // cmd.exe opens a redirection target without sharing it for writing, so two stub processes
    // started by tasks that Gradle runs in parallel cannot append to the same file: one of them
    // loses its record entirely. Each invocation therefore claims the first free numbered file,
    // and `2>nul ( ... ) || goto` both swallows the redirection error cmd.exe would print and
    // moves on to the next slot when another invocation won the race for this one. Sequential
    // invocations claim ascending slots, which is what keeps the recorded order meaningful.
    val script = File(directory, "pnpm.bat")
    script.writeText(
      """
      @echo off
      setlocal
      set RECORD=${recordFile.absolutePath}
      set SLOT=0
      :claim
      if exist "%RECORD%.%SLOT%" goto next
      2>nul (
        >>"%RECORD%.%SLOT%" (
          echo cwd=%CD%
          echo args=%*
          echo ---
        )
      ) || goto next
      if exist "%RECORD%.%SLOT%" goto recorded
      :next
      set /a SLOT+=1
      if %SLOT% lss $MAX_SLOTS goto claim
      echo failed to record an invocation next to %RECORD% 1>&2
      exit /b 1
      :recorded
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

    /** Upper bound on the numbered record files the batch stub will try to claim. */
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

/** A single recorded pnpm invocation. */
data class PnpmInvocation(val workingDirectory: String, val arguments: List<String>)
