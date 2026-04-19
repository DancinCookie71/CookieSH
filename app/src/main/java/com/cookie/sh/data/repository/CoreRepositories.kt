package com.cookie.sh.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.cookie.sh.core.model.ActionResult
import com.cookie.sh.core.model.AppPackage
import com.cookie.sh.core.model.BootInfo
import com.cookie.sh.core.model.BuildProp
import com.cookie.sh.core.model.DeviceInfo
import com.cookie.sh.core.model.IntegrityResult
import com.cookie.sh.core.model.LogLevel
import com.cookie.sh.core.model.LogLine
import com.cookie.sh.core.model.NetworkToolsState
import com.cookie.sh.core.model.PackageFilter
import com.cookie.sh.core.model.PartitionInfo
import com.cookie.sh.core.model.RootStatus
import com.cookie.sh.core.model.ShellHistoryItem
import com.cookie.sh.core.model.SystemStats
import com.cookie.sh.core.shell.ShellEvent
import com.cookie.sh.core.shell.ShellExecutor
import com.cookie.sh.core.shell.quoteForShell
import com.cookie.sh.data.local.CommandHistoryDao
import com.cookie.sh.data.local.CommandHistoryEntity
import com.cookie.sh.data.local.FavoritePropDao
import com.cookie.sh.data.local.FavoritePropEntity
import com.cookie.sh.data.local.SavedLogDao
import com.cookie.sh.data.local.SavedLogEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceRepository {
    suspend fun getDeviceInfo(): DeviceInfo
    suspend fun runRootCommand(command: String)
}

interface BootRepository {
    suspend fun getBootInfo(): BootInfo
}

interface PropsRepository {
    fun observeFavorites(): Flow<Set<String>>
    suspend fun loadProps(): List<BuildProp>
    suspend fun setProp(name: String, value: String): ActionResult
    suspend fun deleteProp(name: String): ActionResult
    suspend fun toggleFavorite(name: String, favorite: Boolean)
    suspend fun exportProps(props: List<BuildProp>): ActionResult
}

interface ShellRepository {
    fun observeHistory(): Flow<List<ShellHistoryItem>>
    fun runCommand(command: String, useRoot: Boolean): Flow<ShellEvent>
    suspend fun saveCommandResult(command: String, useRoot: Boolean, fullOutput: String)
    suspend fun clearHistory()
}

interface LogcatRepository {
    fun streamLogs(): Flow<LogLine>
    suspend fun clearLogs(): ActionResult
    suspend fun exportLogs(lines: List<LogLine>): ActionResult
}

interface NetworkToolsRepository {
    suspend fun getStatus(): NetworkToolsState
    suspend fun setAdbWifi(enabled: Boolean, port: Int): ActionResult
}

interface PackageRepository {
    suspend fun getPackages(filter: PackageFilter): List<AppPackage>
    suspend fun disable(packageName: String): ActionResult
    suspend fun enable(packageName: String): ActionResult
    suspend fun uninstallUser0(packageName: String): ActionResult
    suspend fun forceStop(packageName: String): ActionResult
    suspend fun debloatGsi(): ActionResult
}

interface PartitionRepository {
    suspend fun getPartitions(activeSlotSuffix: String): List<PartitionInfo>
    suspend fun dumpPartition(partition: PartitionInfo): ActionResult
}

interface SystemRepository {
    suspend fun getSystemStats(): SystemStats
    suspend fun checkIntegrity(): IntegrityResult
    suspend fun fixIntegrityProps(): ActionResult
    suspend fun setSelinuxEnabled(enforcing: Boolean): ActionResult
    suspend fun getDisplayDpi(): Int
    suspend fun setDisplayDpi(dpi: Int): ActionResult
    suspend fun resetDisplayDpi(): ActionResult
}

interface PowerRepository {
    suspend fun reboot(target: String = ""): ActionResult
    suspend fun softReboot(): ActionResult
}

@Singleton
class RuntimeDeviceRepository @Inject constructor(
    private val shellExecutor: ShellExecutor,
) : DeviceRepository {

    override suspend fun getDeviceInfo(): DeviceInfo {
        val model = firstNonBlankProp("ro.product.marketname", "ro.product.model")
        val manufacturer = firstNonBlankProp("ro.product.manufacturer")
        val androidVersion = firstNonBlankProp(
            "ro.build.version.release_or_codename",
            "ro.build.version.release",
        )
        val slotSuffix = firstNonBlankProp("ro.boot.slot_suffix").ifBlank { "?" }
        val bootloaderState = resolveBootloaderState()
        val rootStatus = when {
            shellExecutor.execute("id", useRoot = true).isSuccess -> RootStatus.Rooted
            shellExecutor.execute("command -v su", useRoot = false).isSuccess -> RootStatus.Unavailable
            else -> RootStatus.Unknown
        }
        val hasInitBoot = shellExecutor.checkPartitionExists("init_boot") || 
                         shellExecutor.checkPartitionExists("init_boot_a") ||
                         shellExecutor.checkPartitionExists("init_boot_b")
        
        val selinuxStatus = shellExecutor.execute("getenforce", useRoot = false).stdout.trim()

        return DeviceInfo(
            name = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Unknown device" },
            androidVersion = androidVersion.ifBlank { "Unknown" },
            rootStatus = rootStatus,
            rootProvider = shellExecutor.getRootProvider(),
            slotSuffix = slotSuffix,
            bootloaderState = bootloaderState,
            hasInitBoot = hasInitBoot,
            selinuxStatus = selinuxStatus,
        )
    }

    override suspend fun runRootCommand(command: String) {
        shellExecutor.execute(command, useRoot = true)
    }

    private suspend fun resolveBootloaderState(): String {
        val deviceState = firstNonBlankProp("ro.boot.vbmeta.device_state")
        if (deviceState.isNotBlank()) return deviceState.replaceFirstChar { it.titlecase(Locale.US) }
        return when (firstNonBlankProp("ro.boot.flash.locked")) {
            "0" -> "Unlocked"
            "1" -> "Locked"
            else -> "Unknown"
        }
    }

    private suspend fun firstNonBlankProp(vararg keys: String): String {
        keys.forEach { key ->
            val value = shellExecutor.execute("getprop ${key.quoteForShell()}", useRoot = false).stdout.trim()
            if (value.isNotBlank()) return value
        }
        return ""
    }
}

@Singleton
class RuntimeBootRepository @Inject constructor(
    private val shellExecutor: ShellExecutor,
) : BootRepository {

    override suspend fun getBootInfo(): BootInfo {
        val cmdline = shellExecutor.execute("cat /proc/cmdline", useRoot = false).stdout
        val bootConfig = shellExecutor.execute("cat /proc/bootconfig", useRoot = false).stdout
        val slotSuffix = propOrParsed(
            key = "ro.boot.slot_suffix",
            sources = listOf(cmdline, bootConfig),
            patterns = listOf("androidboot.slot_suffix=([^\\s]+)", "slot_suffix=([^\\s]+)"),
        )
        val verifiedBootState = propOrParsed(
            key = "ro.boot.verifiedbootstate",
            sources = listOf(cmdline, bootConfig),
            patterns = listOf("androidboot.verifiedbootstate=([^\\s]+)", "verifiedbootstate=([^\\s]+)"),
        )
        val avbVersion = propOrParsed(
            key = "ro.boot.avb_version",
            sources = listOf(cmdline, bootConfig),
            patterns = listOf("androidboot.avb_version=([^\\s]+)", "avb_version=([^\\s]+)"),
        )
        val kernelVersion = shellExecutor.execute("uname -r", useRoot = false).stdout.ifBlank { "Unknown" }
        val bootloaderState = firstNonBlankProp("ro.boot.vbmeta.device_state").ifBlank {
            when (firstNonBlankProp("ro.boot.flash.locked")) {
                "0" -> "unlocked"
                "1" -> "locked"
                else -> "unknown"
            }
        }
        return BootInfo(
            slotSuffix = slotSuffix.ifBlank { "Unknown" },
            bootloaderState = bootloaderState.replaceFirstChar { it.titlecase(Locale.US) },
            verifiedBootState = verifiedBootState.ifBlank { "Unknown" },
            avbVersion = avbVersion.ifBlank { "Unknown" },
            kernelVersion = kernelVersion,
            cmdline = cmdline,
            bootConfig = bootConfig,
        )
    }

    private suspend fun propOrParsed(
        key: String,
        sources: List<String>,
        patterns: List<String>,
    ): String {
        val prop = firstNonBlankProp(key)
        if (prop.isNotBlank()) return prop
        sources.forEach { source ->
            patterns.forEach { pattern ->
                Regex(pattern).find(source)?.groupValues?.getOrNull(1)?.let { return it }
            }
        }
        return ""
    }

    private suspend fun firstNonBlankProp(key: String): String {
        return shellExecutor.execute("getprop ${key.quoteForShell()}", useRoot = false).stdout.trim()
    }
}

@Singleton
class RuntimePropsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellExecutor: ShellExecutor,
    private val favoritePropDao: FavoritePropDao,
) : PropsRepository {

    override fun observeFavorites(): Flow<Set<String>> {
        return favoritePropDao.observeFavorites().map { favorites -> favorites.map { it.propName }.toSet() }
    }

    override suspend fun loadProps(): List<BuildProp> {
        val favoriteSet = favoritePropDao.observeFavorites()
            .map { it.map(FavoritePropEntity::propName).toSet() }
            .first()
        val output = shellExecutor.execute("getprop", useRoot = false).stdout
        return output.lineSequence()
            .mapNotNull { line ->
                val match = Regex("^\\[(.+)]\\:\\s\\[(.*)]$").find(line.trim()) ?: return@mapNotNull null
                val name = match.groupValues[1]
                val value = match.groupValues[2]
                BuildProp(name = name, value = value, isFavorite = name in favoriteSet)
            }
            .sortedWith(compareByDescending<BuildProp> { it.isFavorite }.thenBy { it.name })
            .toList()
    }

    override suspend fun setProp(name: String, value: String): ActionResult {
        val command = buildPropWriteCommand(name, value)
        val result = shellExecutor.execute(command, useRoot = true)
        return ActionResult(
            success = result.isSuccess,
            message = if (result.isSuccess) "Updated $name" else result.stderr.ifBlank { "Failed to update $name" },
        )
    }

    override suspend fun deleteProp(name: String): ActionResult {
        val command = "if command -v resetprop >/dev/null 2>&1; then resetprop --delete ${name.quoteForShell()}; else setprop ${name.quoteForShell()} ''; fi"
        val result = shellExecutor.execute(command, useRoot = true)
        return ActionResult(
            success = result.isSuccess,
            message = if (result.isSuccess) "Removed $name" else result.stderr.ifBlank { "Failed to remove $name" },
        )
    }

    override suspend fun toggleFavorite(name: String, favorite: Boolean) {
        if (favorite) {
            favoritePropDao.upsert(FavoritePropEntity(propName = name))
        } else {
            favoritePropDao.delete(name)
        }
    }

    override suspend fun exportProps(props: List<BuildProp>): ActionResult = withContext(Dispatchers.IO) {
        val file = exportTextFile(
            directory = Environment.DIRECTORY_DOCUMENTS,
            prefix = "cookiesh-props",
            content = props.joinToString(separator = "\n") { "${it.name}=${it.value}" },
        )
        ActionResult(
            success = true,
            message = "Exported ${props.size} properties",
            exportedPath = file.absolutePath,
        )
    }

    private suspend fun exportTextFile(directory: String, prefix: String, content: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val exportDir = context.getExternalFilesDir(directory) ?: context.filesDir
        val file = File(exportDir, "$prefix-$stamp.txt")
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    private fun buildPropWriteCommand(name: String, value: String): String {
        // Prefer resetprop when available because it handles many boot/system props better than setprop.
        return "if command -v resetprop >/dev/null 2>&1; then resetprop ${name.quoteForShell()} ${value.quoteForShell()}; else setprop ${name.quoteForShell()} ${value.quoteForShell()}; fi"
    }
}

@Singleton
class RuntimeShellRepository @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val commandHistoryDao: CommandHistoryDao,
) : ShellRepository {

    override fun observeHistory(): Flow<List<ShellHistoryItem>> {
        return commandHistoryDao.observeHistory().map { history ->
            history.map {
                ShellHistoryItem(
                    id = it.id,
                    command = it.command,
                    outputPreview = it.outputPreview,
                    usedRoot = it.usedRoot,
                    executedAt = it.executedAt,
                )
            }
        }
    }

    override fun runCommand(command: String, useRoot: Boolean): Flow<ShellEvent> {
        return shellExecutor.stream(command = command, useRoot = useRoot)
    }

    override suspend fun saveCommandResult(command: String, useRoot: Boolean, fullOutput: String) {
        commandHistoryDao.insert(
            CommandHistoryEntity(
                command = command,
                outputPreview = fullOutput.lineSequence().toList().takeLast(6).joinToString("\n").take(600),
                usedRoot = useRoot,
                executedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun clearHistory() {
        commandHistoryDao.clear()
    }
}

@Singleton
class RuntimeLogcatRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellExecutor: ShellExecutor,
    private val savedLogDao: SavedLogDao,
) : LogcatRepository {

    override fun streamLogs(): Flow<LogLine> = flow {
        shellExecutor.stream("logcat -v time", useRoot = true)
            .filterIsInstance<ShellEvent.Output>()
            .collect { event ->
                emit(parseLogLine(event.line))
            }
    }

    override suspend fun clearLogs(): ActionResult {
        val result = shellExecutor.execute("logcat -c", useRoot = true)
        return ActionResult(
            success = result.isSuccess,
            message = if (result.isSuccess) "Cleared log buffers" else result.stderr.ifBlank { "Failed to clear logs" },
        )
    }

    override suspend fun exportLogs(lines: List<LogLine>): ActionResult = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val exportDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val file = File(exportDir, "cookiesh-logcat-$stamp.txt")
        file.parentFile?.mkdirs()
        file.writeText(lines.joinToString(separator = "\n") { it.raw })
        savedLogDao.insert(
            SavedLogEntity(
                filePath = file.absolutePath,
                summary = "Exported ${lines.size} log lines",
                exportedAt = System.currentTimeMillis(),
            ),
        )
        ActionResult(
            success = true,
            message = "Exported ${lines.size} log lines",
            exportedPath = file.absolutePath,
        )
    }

    private fun parseLogLine(rawLine: String): LogLine {
        val regex = Regex("^(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+)\\s+\\d+\\s+\\d+\\s+([VDIWEF])\\s+([^:]+):\\s?(.*)$")
        val match = regex.find(rawLine.trim()) ?: return LogLine(raw = rawLine)
        val level = when (match.groupValues[2]) {
            "V" -> LogLevel.Verbose
            "D" -> LogLevel.Debug
            "I" -> LogLevel.Info
            "W" -> LogLevel.Warn
            "E" -> LogLevel.Error
            "F" -> LogLevel.Fatal
            else -> LogLevel.Unknown
        }
        val message = match.groupValues[4]
        val packageGuess = Regex("([a-zA-Z0-9_]+\\.)+[A-Za-z0-9_]+").find(message)?.value.orEmpty()
        return LogLine(
            raw = rawLine,
            timestamp = match.groupValues[1],
            level = level,
            tag = match.groupValues[3].trim(),
            packageName = packageGuess,
            message = message,
        )
    }
}

@Singleton
class RuntimeNetworkToolsRepository @Inject constructor(
    private val shellExecutor: ShellExecutor,
) : NetworkToolsRepository {

    override suspend fun getStatus(): NetworkToolsState {
        val port = shellExecutor.execute("getprop 'service.adb.tcp.port'", useRoot = false).stdout.ifBlank { "-1" }
        return NetworkToolsState(
            adbWifiEnabled = port.toIntOrNull()?.let { it > 0 } == true,
            adbPort = if (port.toIntOrNull()?.let { it > 0 } == true) port else "5555",
            ipAddress = currentIpv4Address(),
        )
    }

    override suspend fun setAdbWifi(enabled: Boolean, port: Int): ActionResult {
        val targetPort = if (enabled) port.coerceIn(1, 65535) else -1
        val command = "setprop service.adb.tcp.port $targetPort && stop adbd && start adbd"
        val result = shellExecutor.execute(command, useRoot = true)
        return ActionResult(
            success = result.isSuccess,
            message = if (result.isSuccess) {
                if (enabled) "ADB over Wi-Fi enabled on port $targetPort" else "ADB over Wi-Fi disabled"
            } else {
                result.stderr.ifBlank { "Unable to update ADB over Wi-Fi" }
            },
        )
    }

    private suspend fun firstNonBlankProp(vararg keys: String): String {
        keys.forEach { key ->
            val value = shellExecutor.execute("getprop ${key.quoteForShell()}", useRoot = false).stdout.trim()
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun currentIpv4Address(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces == null) {
                "Unavailable"
            } else {
                Collections.list(interfaces)
                    .flatMap { network -> Collections.list(network.inetAddresses) }
                    .firstOrNull { address ->
                        val hostAddress = address.hostAddress ?: return@firstOrNull false
                        !address.isLoopbackAddress && !hostAddress.contains(':')
                    }
                    ?.hostAddress
                    ?: "Unavailable"
            }
        } catch (e: Exception) {
            "Unavailable"
        }
    }
}

@Singleton
class RuntimePartitionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellExecutor: ShellExecutor,
) : PartitionRepository {

    override suspend fun getPartitions(activeSlotSuffix: String): List<PartitionInfo> {
        // Query name, resolved block device, and byte size in one rooted shell pass.
        val command = """
            for path in /dev/block/by-name/*; do
              [ -e "${'$'}path" ] || continue
              name=$(basename "${'$'}path")
              target=$(readlink -f "${'$'}path")
              size=$(blockdev --getsize64 "${'$'}target" 2>/dev/null || echo 0)
              echo "${'$'}name|${'$'}target|${'$'}size"
            done
        """.trimIndent()
        val output = shellExecutor.execute(command, useRoot = true).stdout
        return output.lineSequence()
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 3) return@mapNotNull null
                val name = parts[0]
                val target = parts[1]
                val size = parts[2].toLongOrNull() ?: 0L
                PartitionInfo(
                    name = name,
                    symlinkTarget = target,
                    sizeBytes = size,
                    isActive = activeSlotSuffix.isNotBlank() && (
                        name.endsWith(activeSlotSuffix) || target.endsWith(activeSlotSuffix)
                        ),
                )
            }
            .sortedBy { it.name }
            .toList()
    }

    override suspend fun dumpPartition(partition: PartitionInfo): ActionResult = withContext(Dispatchers.IO) {
        val exportDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val outFile = File(exportDir, "${partition.name}.img")
        outFile.parentFile?.mkdirs()
        val command = "dd if=${partition.symlinkTarget.quoteForShell()} of=${outFile.absolutePath.quoteForShell()} bs=4M"
        val result = shellExecutor.execute(command, useRoot = true)
        ActionResult(
            success = result.isSuccess,
            message = if (result.isSuccess) "Dumped ${partition.name}" else result.stderr.ifBlank { "Unable to dump ${partition.name}" },
            exportedPath = outFile.absolutePath.takeIf { result.isSuccess },
        )
    }
}

@Singleton
class RuntimePackageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellExecutor: ShellExecutor,
) : PackageRepository {

    private val packageManager: PackageManager = context.packageManager

    override suspend fun getPackages(filter: PackageFilter): List<AppPackage> = withContext(Dispatchers.IO) {
        val packages = packageManager.getInstalledPackagesCompat()
        packages.map { info ->
            val applicationInfo = info.applicationInfo
            AppPackage(
                label = applicationInfo.loadLabel(packageManager).toString(),
                packageName = info.packageName,
                versionName = info.versionName.orEmpty().ifBlank { "Unknown" },
                installDate = info.firstInstallTime,
                permissions = info.requestedPermissions?.toList().orEmpty().sorted(),
                isSystemApp = applicationInfo.isSystemApp(),
                isEnabled = applicationInfo.enabled,
            )
        }
            .filter { item ->
                    when (filter) {
                        PackageFilter.All -> true
                        PackageFilter.System -> item.isSystemApp
                        PackageFilter.User -> !item.isSystemApp
                        PackageFilter.Disabled -> !item.isEnabled
                        else -> true
                    }
                }
                .sortedWith(
                    compareByDescending<AppPackage> { !it.isEnabled }
                        .thenBy { it.label.lowercase(Locale.US) }
                )
    }

    override suspend fun disable(packageName: String): ActionResult {
        return runPmCommand("pm disable-user --user 0 ${packageName.quoteForShell()}", "Disabled $packageName")
    }

    override suspend fun enable(packageName: String): ActionResult {
        return runPmCommand("pm enable ${packageName.quoteForShell()}", "Enabled $packageName")
    }

    override suspend fun uninstallUser0(packageName: String): ActionResult {
        return runPmCommand("pm uninstall --user 0 ${packageName.quoteForShell()}", "Requested uninstall for user 0")
    }

    override suspend fun forceStop(packageName: String): ActionResult {
        return runPmCommand("am force-stop ${packageName.quoteForShell()}", "Force-stopped $packageName")
    }

    override suspend fun debloatGsi(): ActionResult {
        val bloat = listOf(
            "com.android.browser",
            "com.android.calendar",
            "com.android.camera2",
            "com.android.deskclock",
            "com.android.email",
            "com.android.gallery3d",
            "com.android.music",
            "com.android.quicksearchbox"
        )
        
        var count = 0
        for (pkg in bloat) {
            val result = shellExecutor.execute("pm disable-user ${pkg.quoteForShell()}", useRoot = true)
            if (result.isSuccess) count++
        }
        
        return ActionResult(success = true, message = "Disabled $count AOSP apps.")
    }

    private suspend fun runPmCommand(command: String, successMessage: String): ActionResult {
        val result = shellExecutor.execute(command, useRoot = true)
        return ActionResult(
            success = result.isSuccess,
            message = if (result.isSuccess) successMessage else result.stderr.ifBlank { "Package command failed" },
        )
    }

    private fun PackageManager.getInstalledPackagesCompat(): List<PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }
    }

    private fun ApplicationInfo.isSystemApp(): Boolean {
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 || (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }
}

@Singleton
class RuntimeSystemRepository @Inject constructor(
    private val shellExecutor: ShellExecutor,
) : SystemRepository {

    override suspend fun getSystemStats(): SystemStats {
        val memInfo = shellExecutor.execute("cat /proc/meminfo", useRoot = false).stdout
        val uptime = shellExecutor.execute("uptime -p", useRoot = false).stdout.removePrefix("up ").trim()
        
        val batteryPath = resolveBatteryPath()
        val batteryLevel = if (batteryPath != null) {
            shellExecutor.execute("cat $batteryPath/capacity", useRoot = false).stdout.trim().toIntOrNull() ?: 0
        } else 0
        
        val batteryTemp = if (batteryPath != null) {
            (shellExecutor.execute("cat $batteryPath/temp", useRoot = false).stdout.trim().toFloatOrNull() ?: 0f) / 10f
        } else 0f
        
        val batteryCurrent = if (batteryPath != null) {
            // Check for current_now, current_avg, or constant_charge_current
            var raw = shellExecutor.execute("cat $batteryPath/current_now", useRoot = false).stdout.trim().toIntOrNull()
            if (raw == null) {
                raw = shellExecutor.execute("cat $batteryPath/constant_charge_current", useRoot = false).stdout.trim().toIntOrNull()
            }
            raw = raw ?: 0
            if (raw > 100000 || raw < -100000) raw / 1000 else raw
        } else 0
        
        val batteryStatus = if (batteryPath != null) {
            shellExecutor.execute("cat $batteryPath/status", useRoot = false).stdout.trim()
        } else "Unknown"

        val ramTotal = parseMemInfo(memInfo, "MemTotal")
        val ramFree = parseMemInfo(memInfo, "MemFree")
        val ramBuffers = parseMemInfo(memInfo, "Buffers")
        val ramCached = parseMemInfo(memInfo, "Cached")
        val ramUsed = ramTotal - ramFree - ramBuffers - ramCached

        val swapTotal = parseMemInfo(memInfo, "SwapTotal")
        val swapFree = parseMemInfo(memInfo, "SwapFree")
        val swapUsed = swapTotal - swapFree

        val diskInfo = shellExecutor.execute("df /data", useRoot = false).stdout
        val diskStats = parseDf(diskInfo)

        val cpuUsage = parseCpuUsage()
        val cpuTemp = resolveCpuTemp()
        
        val selinuxEnforcing = shellExecutor.execute("getenforce", useRoot = false).stdout.trim().lowercase() == "enforcing"

        return SystemStats(
            cpuUsage = cpuUsage,
            cpuTemp = cpuTemp,
            ramUsedBytes = ramUsed * 1024,
            ramTotalBytes = ramTotal * 1024,
            swapUsedBytes = swapUsed * 1024,
            swapTotalBytes = swapTotal * 1024,
            diskUsedBytes = diskStats.first * 1024,
            diskTotalBytes = diskStats.second * 1024,
            batteryLevel = batteryLevel,
            batteryTemp = batteryTemp,
            batteryCurrentMa = batteryCurrent,
            batteryStatus = batteryStatus,
            uptime = uptime,
            selinuxEnforcing = selinuxEnforcing
        )
    }

    override suspend fun checkIntegrity(): IntegrityResult {
        val fingerprint = shellExecutor.execute("getprop ro.build.fingerprint", useRoot = false).stdout.trim()
        
        // Simulating Integrity check for now as it usually requires a remote server or specialized binary.
        // We will check if any typical spoofing props are set.
        val isSpoofed = shellExecutor.execute("getprop ro.boot.flash.locked", useRoot = false).stdout.trim() == "1" &&
                       shellExecutor.execute("getprop ro.boot.verifiedbootstate", useRoot = false).stdout.trim() == "green"
        
        // In a real app, you'd use a library or call a specialized tool.
        // For CookieSH, we'll "detect" it based on common patterns in GSIs/Magisk.
        val hasResetProp = shellExecutor.execute("command -v resetprop", useRoot = true).isSuccess
        
        return IntegrityResult(
            deviceIntegrity = isSpoofed || !fingerprint.contains("test-keys"),
            basicIntegrity = true,
            strongIntegrity = false, // Hard to pass on GSIs
            spoofingActive = hasResetProp,
            fingerprint = fingerprint
        )
    }

    override suspend fun fixIntegrityProps(): ActionResult = withContext(Dispatchers.IO) {
        val commands = listOf(
            "resetprop ro.boot.flash.locked 1",
            "resetprop ro.boot.verifiedbootstate green",
            "resetprop ro.boot.vbmeta.device_state locked",
            "resetprop ro.build.tags release-keys"
        )
        
        var success = true
        val errors = mutableListOf<String>()
        
        for (cmd in commands) {
            val result = shellExecutor.execute(cmd, useRoot = true)
            if (!result.isSuccess) {
                success = false
                errors.add(result.stderr)
            }
        }
        
        ActionResult(
            success = success,
            message = if (success) "Props spoofed successfully. Reboot recommended." else "Failed: ${errors.firstOrNull()}"
        )
    }

    override suspend fun setSelinuxEnabled(enforcing: Boolean): ActionResult {
        val target = if (enforcing) "1" else "0"
        val result = shellExecutor.execute("setenforce $target", useRoot = true)
        return ActionResult(
            success = result.isSuccess,
            message = if (result.isSuccess) "SELinux set to ${if (enforcing) "Enforcing" else "Permissive"}" else result.stderr
        )
    }

    override suspend fun getDisplayDpi(): Int {
        val output = shellExecutor.execute("wm density", useRoot = false).stdout
        return Regex("Physical density: (\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("Override density: (\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull()
            ?: 420
    }

    override suspend fun setDisplayDpi(dpi: Int): ActionResult {
        val result = shellExecutor.execute("wm density $dpi", useRoot = true)
        return ActionResult(
            success = result.isSuccess,
            message = if (result.isSuccess) "DPI set to $dpi" else result.stderr
        )
    }

    override suspend fun resetDisplayDpi(): ActionResult {
        val result = shellExecutor.execute("wm density reset", useRoot = true)
        return ActionResult(
            success = result.isSuccess,
            message = if (result.isSuccess) "DPI reset to default" else result.stderr
        )
    }

    private suspend fun resolveBatteryPath(): String? {
        val paths = listOf("/sys/class/power_supply/battery", "/sys/class/power_supply/axp2202-battery")
        for (path in paths) {
            if (shellExecutor.execute("[ -d $path ]", useRoot = false).isSuccess) {
                return path
            }
        }
        // Fallback: search for any directory in power_supply that contains 'capacity'
        val search = shellExecutor.execute("find /sys/class/power_supply/ -name capacity", useRoot = false).stdout.trim()
        if (search.isNotBlank()) {
            return search.substringBeforeLast("/")
        }
        return null
    }

    private suspend fun resolveCpuTemp(): Float {
        // Common CPU thermal zone names/paths
        val zones = listOf("cpu-thermal", "cpu_thermal", "cpul_thermal_zone", "soc-thermal")
        val output = shellExecutor.execute("su -c 'for f in /sys/class/thermal/thermal_zone*/type; do echo \"$(cat \$f)|$(dirname \$f)\"; done'", useRoot = true).stdout
        
        output.lines().forEach { line ->
            val parts = line.split("|")
            if (parts.size == 2) {
                val type = parts[0].lowercase()
                val path = parts[1]
                if (zones.any { type.contains(it) }) {
                    val temp = shellExecutor.execute("su -c 'cat $path/temp'", useRoot = true).stdout.trim().toFloatOrNull()
                    if (temp != null) {
                        return if (temp > 1000) temp / 1000f else temp
                    }
                }
            }
        }
        
        // Fallback to zone0 if we found nothing
        val fallback = shellExecutor.execute("su -c 'cat /sys/class/thermal/thermal_zone0/temp'", useRoot = true).stdout.trim().toFloatOrNull()
        return if (fallback != null) (if (fallback > 1000) fallback / 1000f else fallback) else 0f
    }

    private fun parseMemInfo(content: String, key: String): Long {
        return Regex("$key:\\s+(\\d+)").find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun parseDf(content: String): Pair<Long, Long> {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return 0L to 0L
        val parts = lines[1].split(Regex("\\s+"))
        if (parts.size < 4) return 0L to 0L
        val total = parts[1].toLongOrNull() ?: 0L
        val used = parts[2].toLongOrNull() ?: 0L
        return used to total
    }

    private suspend fun parseCpuUsage(): Int {
        val stat1 = shellExecutor.execute("cat /proc/stat", useRoot = false).stdout.lines().firstOrNull { it.startsWith("cpu ") } ?: return 0
        kotlinx.coroutines.delay(100)
        val stat2 = shellExecutor.execute("cat /proc/stat", useRoot = false).stdout.lines().firstOrNull { it.startsWith("cpu ") } ?: return 0
        
        val usage1 = stat1.split(Regex("\\s+")).filter { it.isNotBlank() }.drop(1).map { it.toLongOrNull() ?: 0L }
        val usage2 = stat2.split(Regex("\\s+")).filter { it.isNotBlank() }.drop(1).map { it.toLongOrNull() ?: 0L }
        
        if (usage1.size < 5 || usage2.size < 5) return 0
        
        val idle1 = usage1[3] + usage1[4]
        val idle2 = usage2[3] + usage2[4]
        
        val total1 = usage1.sum()
        val total2 = usage2.sum()
        
        val totalDiff = total2 - total1
        val idleDiff = idle2 - idle1
        
        return if (totalDiff > 0) ((totalDiff - idleDiff) * 100 / totalDiff).toInt().coerceIn(0, 100) else 0
    }
}

@Singleton
class RuntimePowerRepository @Inject constructor(
    private val shellExecutor: ShellExecutor,
) : PowerRepository {

    override suspend fun reboot(target: String): ActionResult {
        val cmd = if (target.isBlank()) "reboot" else "reboot $target"
        val result = shellExecutor.execute(cmd, useRoot = true)
        return ActionResult(
            success = result.isSuccess,
            message = if (result.isSuccess) "Rebooting..." else result.stderr
        )
    }

    override suspend fun softReboot(): ActionResult {
        val result = shellExecutor.execute("killall system_server", useRoot = true)
        return ActionResult(
            success = result.isSuccess,
            message = if (result.isSuccess) "Soft rebooting..." else result.stderr
        )
    }
}

