package com.example.testapplication2

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Must match BleAdvertiserService exactly — same UUID, same encoding
// ─────────────────────────────────────────────────────────────────────────────

private val APP_SERVICE_UUID = ParcelUuid(
    UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
)

// ─────────────────────────────────────────────────────────────────────────────
// Scanner state
// ─────────────────────────────────────────────────────────────────────────────

private var activeScanCallback: ScanCallback? = null

// ─────────────────────────────────────────────────────────────────────────────
// Function 2 — Start scanning for the teacher's BLE signal (student side)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Starts a BLE scan looking for the teacher's broadcast from [BleAdvertiserService].
 * When a packet with our service UUID is found, the session code is decoded
 * from the service data bytes (UTF-8) and passed to [onSessionCodeFound].
 *
 * The scan runs until [stopBleScanning] is called.
 */
fun startBleScanning(
    context: Context,
    onSessionCodeFound: (sessionCode: String) -> Unit
): Result<Unit> {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = manager?.adapter
        ?: return Result.failure(Exception("Bluetooth is not available on this device."))

    if (!adapter.isEnabled) {
        return Result.failure(Exception("Bluetooth is turned off. Please enable it to check in."))
    }

    val scanner = adapter.bluetoothLeScanner
        ?: return Result.failure(Exception("BLE scanning is not supported on this device."))

    // If a scan is already running, stop it first to avoid leaking callbacks
    stopBleScanning(context)

    // Filter: only pick up packets that carry our service data UUID.
    // We filter by Service Data because the advertiser might omit the 
    // separate Service UUID to fit in the 31-byte legacy advertising limit.
    val filter = ScanFilter.Builder()
        .setServiceData(APP_SERVICE_UUID, null)
        .build()

    val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Extract service data bytes — same UUID the advertiser used
            val bytes = result.scanRecord
                ?.getServiceData(APP_SERVICE_UUID)
                ?: return

            // Decode exactly as the advertiser encoded: toByteArray(Charsets.UTF_8)
            val sessionCode = String(bytes, Charsets.UTF_8).trim()
            if (sessionCode.isNotBlank()) {
                onSessionCodeFound(sessionCode)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            // Scan couldn't start — common causes: Bluetooth off, permissions missing
        }
    }

    activeScanCallback = callback

    try {
        scanner.startScan(listOf(filter), settings, callback)
    } catch (e: SecurityException) {
        activeScanCallback = null
        return Result.failure(Exception("Missing Bluetooth permission: ${e.message}"))
    }

    return Result.success(Unit)
}

// ─────────────────────────────────────────────────────────────────────────────
// Function 3 — Stop scanning (student side)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Stops the BLE scan. Call this once attendance is marked or the session ends.
 * Safe to call even if scanning was never started.
 */
fun stopBleScanning(context: Context) {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val scanner = manager?.adapter?.bluetoothLeScanner

    try {
        activeScanCallback?.let { scanner?.stopScan(it) }
    } catch (e: Exception) {
        // Permissions revoked mid-session or scanner unavailable — nothing we can do
    } finally {
        activeScanCallback = null
    }
}
