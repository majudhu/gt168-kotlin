package com.example.gt168_kotlin

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.*
import android.hardware.usb.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    companion object {
    }

    private val usbManager =
        applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager

    private var controller: GT168Controller? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        getPermission()
    }


    private fun getPermission() {
        val intentAction = "test.maju.gt168_kotlin.USB_PERMISSION"
        val usbManager =
            applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = GT168Controller.getDevice(usbManager)
        val intent =
            PendingIntent.getBroadcast(
                applicationContext,
                0,
                Intent(intentAction),
                0
            )
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(applicationContext: Context, intent: Intent) {
                applicationContext.unregisterReceiver(this)
                if (intent.action == intentAction) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        controller = device?.let { GT168Controller(usbManager, it) }
                        Toast.makeText(applicationContext, "Permission Granted", Toast.LENGTH_SHORT)
                            .show()
                    } else
                        Toast.makeText(applicationContext, "Permission Denied", Toast.LENGTH_SHORT)
                            .show()
                }
            }
        }
        applicationContext.registerReceiver(receiver, IntentFilter(intentAction))
        usbManager.requestPermission(device, intent)
    }

    fun openDevice(view: View) {
        if (usbManager.hasPermission(GT168Controller.getDevice(usbManager))) {
            MainScope().launch {
                if (controller?.testConnection() == true) {
                    btnOpen.isEnabled = false;
                    txtStatus.text = "Open device successful"
                    listOf<Button>(
                        btnClose,
                        btnCancel,
                        btnEnroll,
                        btnVerify,
                        btnIdentify,
                        btnIdentifyFree,
                        btnEmptyId,
                        btnEnrollCount
                    ).forEach { it.isEnabled = true }
                } else {
                    txtStatus.text = "Open device failed"
                }
            }
        } else {
            getPermission()
        }
    }

    fun closeDevice(view: View) {
        controller = null;
        btnOpen.isEnabled = true;
        txtStatus.text = "Open device successful"
        listOf<Button>(
            btnClose,
            btnCancel,
            btnEnroll,
            btnVerify,
            btnIdentify,
            btnIdentifyFree,
            btnEmptyId,
            btnEnrollCount
        ).forEach { it.isEnabled = false }
    }

}
