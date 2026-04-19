package com.cookie.sh.core.model

data class SystemStats(
    val cpuUsage: Int = 0,
    val cpuTemp: Float = 0f,
    val ramUsedBytes: Long = 0,
    val ramTotalBytes: Long = 0,
    val swapUsedBytes: Long = 0,
    val swapTotalBytes: Long = 0,
    val diskUsedBytes: Long = 0,
    val diskTotalBytes: Long = 0,
    val batteryLevel: Int = 0,
    val batteryTemp: Float = 0f,
    val batteryCurrentMa: Int = 0,
    val batteryStatus: String = "Unknown",
    val uptime: String = "00:00:00",
    val selinuxEnforcing: Boolean = true,
) {
    val ramPercent: Float get() = if (ramTotalBytes > 0) ramUsedBytes.toFloat() / ramTotalBytes else 0f
    val swapPercent: Float get() = if (swapTotalBytes > 0) swapUsedBytes.toFloat() / swapTotalBytes else 0f
    val diskPercent: Float get() = if (diskTotalBytes > 0) diskUsedBytes.toFloat() / diskTotalBytes else 0f
}
