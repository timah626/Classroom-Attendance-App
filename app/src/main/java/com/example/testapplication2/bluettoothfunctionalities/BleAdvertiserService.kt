package com.example.testapplication2.bluettoothfunctionalities



import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.UUID

class BleAdvertiserService : Service() {

    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("BleAdvertiserService", "BLE Advertisement started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BleAdvertiserService", "BLE Advertisement failed with error code: $errorCode")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter != null && adapter.isEnabled) {
            bluetoothLeAdvertiser = adapter.bluetoothLeAdvertiser
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionCode = intent?.getStringExtra(EXTRA_SESSION_CODE) ?: "UNKNOWN"

        startForeground(NOTIFICATION_ID, createNotification(sessionCode))
        startAdvertising(sessionCode)

        return START_STICKY
    }

    private fun startAdvertising(sessionCode: String) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        // Using a custom UUID for the application
        val appUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

        // CRITICAL FIX: We must keep the advertisement data under 31 bytes.
        // A 128-bit UUID (16 bytes) + payload (6 bytes) + headers + flags
        // quickly hits this limit.
        // We remove the redundant 'addServiceUuid' and only use 'addServiceData'.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(ParcelUuid(appUuid), sessionCode.toByteArray(Charsets.UTF_8))
            .build()

        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) // reset if active
            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e("BleAdvertiserService", "Missing permissions for BLE advertising", e)
        }
    }

    private fun createNotification(sessionCode: String): Notification {
        val channelId = "ble_advertiser_channel"
        val channelName = "BLE Session Broadcast"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Broadcasting Session")
            .setContentText("Session Code: $sessionCode")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: SecurityException) {
            Log.e("BleAdvertiserService", "Missing permissions to stop BLE advertising", e)
        }
    }

    companion object {
        const val EXTRA_SESSION_CODE = "extra_session_code"
        private const val NOTIFICATION_ID = 101
    }
}