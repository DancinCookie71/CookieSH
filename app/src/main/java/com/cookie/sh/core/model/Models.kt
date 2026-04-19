package com.cookie.sh.core.model

import java.util.UUID

enum class RootStatus {
    Rooted,
    Unavailable,
    Unknown,
}

data class DeviceInfo(
    val name: String = "Unknown device",
    val androidVersion: String = "Unknown",
    val rootStatus: RootStatus = RootStatus.Unknown,
    val rootProvider: String = "Unknown",
    val slotSuffix: String = "?",
    val bootloaderState: String = "Unknown",
    val hasInitBoot: Boolean = false,
    val selinuxStatus: String = "Unknown",
)

data class BuildProp(
    val name: String,
    val value: String,
    val isFavorite: Boolean = false,
)

data class BootInfo(
    val slotSuffix: String = "Unknown",
    val bootloaderState: String = "Unknown",
    val verifiedBootState: String = "Unknown",
    val avbVersion: String = "Unknown",
    val kernelVersion: String = "Unknown",
    val cmdline: String = "",
    val bootConfig: String = "",
)

data class ShellHistoryItem(
    val id: Long,
    val command: String,
    val outputPreview: String,
    val usedRoot: Boolean,
    val executedAt: Long,
)

data class TerminalLine(
    val text: String,
    val isError: Boolean = false,
)

enum class LogLevel(val shortName: String) {
    Verbose("V"),
    Debug("D"),
    Info("I"),
    Warn("W"),
    Error("E"),
    Fatal("F"),
    Unknown("?"),
}

data class LogLine(
    val id: String = UUID.randomUUID().toString(),
    val raw: String,
    val timestamp: String = "",
    val level: LogLevel = LogLevel.Unknown,
    val tag: String = "",
    val packageName: String = "",
    val message: String = raw,
)

enum class PackageFilter {
    All,
    System,
    User,
    Disabled,
}

data class AppPackage(
    val label: String,
    val packageName: String,
    val versionName: String,
    val installDate: Long,
    val permissions: List<String>,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
)

data class PartitionInfo(
    val name: String,
    val symlinkTarget: String,
    val sizeBytes: Long,
    val isActive: Boolean,
)

data class NetworkToolsState(
    val adbWifiEnabled: Boolean = false,
    val adbPort: String = "5555",
    val ipAddress: String = "Unavailable",
    val phhManagerPackage: String = "",
)

data class ActionResult(
    val success: Boolean,
    val message: String,
    val exportedPath: String? = null,
)

data class BuildPropPreset(
    val title: String,
    val description: String,
    val packageName: String,
)

data class IntegrityResult(
    val deviceIntegrity: Boolean = false,
    val basicIntegrity: Boolean = false,
    val strongIntegrity: Boolean = false,
    val spoofingActive: Boolean = false,
    val fingerprint: String = "",
)
