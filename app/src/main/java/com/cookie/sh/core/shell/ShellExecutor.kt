package com.cookie.sh.core.shell

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ShellEvent {
    data class Output(val line: String, val isError: Boolean = false) : ShellEvent
    data class Completed(val exitCode: Int) : ShellEvent
}

data class ShellResult(
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val usedRoot: Boolean,
) {
    val isSuccess: Boolean = exitCode == 0
}

interface ShellExecutor {
    fun stream(command: String, useRoot: Boolean = false): Flow<ShellEvent>
    suspend fun execute(command: String, useRoot: Boolean = false): ShellResult
    suspend fun getRootProvider(): String
    suspend fun checkPartitionExists(name: String): Boolean
}

@Singleton
class RuntimeShellExecutor @Inject constructor() : ShellExecutor {

    private val rootMutex = Mutex()
    private var rootProcess: Process? = null
    private var rootWriter: BufferedWriter? = null
    private var rootReader: BufferedReader? = null

    override fun stream(command: String, useRoot: Boolean): Flow<ShellEvent> = callbackFlow {
        // Long-running streams get their own process to avoid blocking the persistent shell
        val process = Runtime.getRuntime().exec(commandArray(command, useRoot))
        val scope = CoroutineScope(Dispatchers.IO + Job())

        fun readStream(reader: BufferedReader, isError: Boolean) {
            try {
                reader.useLines { lines ->
                    lines.forEach { line ->
                        trySend(ShellEvent.Output(line = line, isError = isError))
                    }
                }
            } catch (e: Exception) {
                // Stream closed
            }
        }

        scope.launch {
            readStream(BufferedReader(InputStreamReader(process.inputStream)), false)
        }
        scope.launch {
            readStream(BufferedReader(InputStreamReader(process.errorStream)), true)
        }
        scope.launch {
            val exitCode = process.waitFor()
            trySend(ShellEvent.Completed(exitCode))
            channel.close()
        }

        awaitClose {
            process.destroy()
            scope.cancel()
        }
    }

    override suspend fun execute(command: String, useRoot: Boolean): ShellResult = withContext(Dispatchers.IO) {
        if (useRoot) {
            executeWithPersistentRoot(command)
        } else {
            executeWithSh(command)
        }
    }

    private suspend fun executeWithSh(command: String): ShellResult = coroutineScope {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val stdoutDeferred = async { process.inputStream.bufferedReader().use { it.readText() } }
        val stderrDeferred = async { process.errorStream.bufferedReader().use { it.readText() } }
        val exitCode = process.waitFor()
        ShellResult(
            command = command,
            stdout = stdoutDeferred.await().trim(),
            stderr = stderrDeferred.await().trim(),
            exitCode = exitCode,
            usedRoot = false
        )
    }

    private suspend fun executeWithPersistentRoot(command: String): ShellResult = rootMutex.withLock {
        try {
            val (_, writer, reader) = getOrStartRootShell()
            val marker = "END_OF_COMMAND_${UUID.randomUUID()}"
            
            // We use a marker to know when the command finished and to get the exit code
            writer.write("$command\n")
            writer.write("echo $marker \$?\n")
            writer.flush()

            val stdoutBuilder = StringBuilder()
            var exitCode = -1
            
            while (true) {
                val line = reader.readLine() ?: break
                if (line.contains(marker)) {
                    exitCode = line.substringAfter(marker).trim().toIntOrNull() ?: 0
                    break
                }
                stdoutBuilder.append(line).append("\n")
            }
            
            ShellResult(
                command = command,
                stdout = stdoutBuilder.toString().trim(),
                stderr = "", // Stderr is merged in this simplified persistent shell
                exitCode = exitCode,
                usedRoot = true
            )
        } catch (e: Exception) {
            // Reset on failure so the next call tries to restart the shell
            rootProcess?.destroy()
            rootProcess = null
            ShellResult(command, "", e.message ?: "Root Shell Error", -1, true)
        }
    }

    override suspend fun getRootProvider(): String = withContext(Dispatchers.IO) {
        val magiskVer = execute("getprop ro.magisk.version", useRoot = false).stdout.trim()
        val versionResult = execute("su -v", useRoot = false)
        val pathResult = execute("which su", useRoot = false)
        val path = pathResult.stdout.trim()
        val version = versionResult.stdout.trim()
        
        // Check for magisk binary in common locations even if not in PATH
        // Must use root to check /data/adb
        val magiskBinCheck = execute("ls /data/adb/magisk/magisk", useRoot = true).isSuccess ||
                            execute("ls /sbin/magisk", useRoot = true).isSuccess

        when {
            magiskVer.isNotBlank() -> "Magisk ($magiskVer)"
            version.contains("MAGISK", ignoreCase = true) -> "Magisk ($version)"
            path.contains("magisk") -> "Magisk (detected by path: $path)"
            magiskBinCheck -> "Magisk (Binary present, but daemon not running)"
            version.contains("bruh", ignoreCase = true) -> "phh-superuser ($version)"
            path.contains("phh") -> "phh-superuser ($path)"
            version.isNotBlank() -> "Unknown Provider ($version)"
            else -> "None / Locked"
        }
    }

    override suspend fun checkPartitionExists(name: String): Boolean = withContext(Dispatchers.IO) {
        // Use root because /dev/block is restricted on modern Android
        val result = execute("ls /dev/block/by-name/$name", useRoot = true)
        if (result.isSuccess) return@withContext true
        
        // Fallback for non-standard paths (common on some MTK/Spreadtrum devices)
        val fallback = execute("find /dev/block -name ${name.quoteForShell()} | grep -q .", useRoot = true)
        fallback.isSuccess
    }

    private fun getOrStartRootShell(): Triple<Process, BufferedWriter, BufferedReader> {
        var process = rootProcess
        if (process == null || !process.isAlive) {
            val pb = ProcessBuilder("su")
            pb.redirectErrorStream(true)
            process = pb.start()
            rootProcess = process
            rootWriter = BufferedWriter(OutputStreamWriter(process.outputStream))
            rootReader = BufferedReader(InputStreamReader(process.inputStream))
        }
        return Triple(process!!, rootWriter!!, rootReader!!)
    }

    private fun commandArray(command: String, useRoot: Boolean): Array<String> {
        return if (useRoot) {
            arrayOf("su", "-c", command)
        } else {
            arrayOf("sh", "-c", command)
        }
    }
}

fun String.quoteForShell(): String = "'${replace("'", "'\"'\"'")}'"
