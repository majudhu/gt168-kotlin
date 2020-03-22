package com.example.gt168_kotlin


import android.app.*
import android.content.*
import android.hardware.usb.*
import kotlinx.coroutines.*

class gt168Controller {
    companion object {
        const val VID = 0x2009
        const val PID = 0x7638

        private const val ACTION = "com.example.gt168_kotlin.USB_PERMISSION"

        private fun getDevice(usbManager: UsbManager): UsbDevice? =
            usbManager.deviceList.values.firstOrNull { it.vendorId == VID && it.productId == PID }

        fun hasPermission(context: Context): Boolean {
            val usbManager = context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
            return usbManager.hasPermission(getDevice(usbManager))
        }

        suspend fun requestPermission(context: Context) {
            val usbManager = context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
            val pendingIntent = PendingIntent.getBroadcast(context.applicationContext, 0, Intent("test.maju.gt168-kotlin.USB_PERMISSION"), 0)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(applicationContext: Context, intent: Intent) {
                    applicationContext.unregisterReceiver(this)
                }
            }
            context.applicationContext.registerReceiver(receiver, IntentFilter("test.maju.gt168-kotlin.USB_PERMISSION"))
            usbManager.requestPermission(getDevice(usbManager), pendingIntent)
        }
    }
}