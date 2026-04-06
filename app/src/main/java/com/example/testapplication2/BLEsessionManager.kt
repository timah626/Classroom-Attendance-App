package com.example.testapplication2

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent

/**
 * BleSessionManager
 *
 * A single place to start and stop BLE broadcasting for an attendance session.
 * Call [startBroadcast] when a session begins, and [stopBroadcast] when it ends.
 */
object BleSessionManager {

    /**
     * Returns true if the device's Bluetooth adapter is currently switched on.
     * Always call this before [startBroadcast] — BLE advertising silently fails
     * or crashes if Bluetooth is off.
     */
    fun isBluetoothOn(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }

    /**
     * Starts broadcasting [sessionCode] via BLE in the background.
     * Safe to call from any Composable or ViewModel that has a [Context].
     * Make sure [isBluetoothOn] returns true before calling this.
     */
    fun startBroadcast(context: Context, sessionCode: String) {
        val intent = Intent(context, BleAdvertiserService::class.java)
            .putExtra(BleAdvertiserService.EXTRA_SESSION_CODE, sessionCode)
        context.startForegroundService(intent)
    }

    /**
     * Stops the BLE broadcast and dismisses the foreground notification.
     * Safe to call from any Composable or ViewModel that has a [Context].
     */
    fun stopBroadcast(context: Context) {
        context.stopService(Intent(context, BleAdvertiserService::class.java))
    }
}
