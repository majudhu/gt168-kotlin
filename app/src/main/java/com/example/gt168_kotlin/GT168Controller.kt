package com.example.gt168_kotlin


import android.hardware.usb.*
import kotlinx.coroutines.*

class GT168Controller(private val usbManager: UsbManager, private val device: UsbDevice) {

    companion object {
        private const val VID = 0x2009
        private const val PID = 0x7638
        fun getDevice(usbManager: UsbManager): UsbDevice? = usbManager
            .deviceList.values.firstOrNull { it.vendorId == VID && it.productId == PID }
    }

    private val usbInterface = device.getInterface(0)

    private val endPoints = (0 until usbInterface.endpointCount)
        .map { usbInterface.getEndpoint(it) }
        .filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }

    private val inEndPoint =
        endPoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN }!!

    private val outEndpoint =
        endPoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }!!

    private val maxInSize = inEndPoint.maxPacketSize

    private val maxOutSize = outEndpoint.maxPacketSize


    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        val res = ByteArray(64)
        val csw = ByteArray(64)
        val usbInterface = device.getInterface(0)
        val cbw = byteArrayOf(
            0x55,
            0x53,
            0x42,
            0x43,
            0x28,
            0x2B,
            0x18,
            0x89.toByte(),
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x0A,
            0xEF.toByte(),
            0x11,
            0x00,
            0x00,
            0x18,
            *ByteArray(11)
        )
        val cmd =
            byteArrayOf(0x55, 0xAA.toByte(), 0x50, 0x01, *ByteArray(18), 0x50, 0x01)
        val usbDeviceConnection = usbManager.openDevice(device)
        usbDeviceConnection.claimInterface(usbInterface, true)

        val r1 = usbDeviceConnection.bulkTransfer(outEndpoint, cbw, 31, 3000)
        val r2 = usbDeviceConnection.bulkTransfer(outEndpoint, cmd, 24, 3000)
        val r3 = usbDeviceConnection.bulkTransfer(inEndPoint, csw, 13, 3000)
        cbw[0x0c] = 0x80.toByte()
        cbw[0x10] = 0x12.toByte()
        val r4 = usbDeviceConnection.bulkTransfer(outEndpoint, cbw, 31, 3000)
        val r5 = usbDeviceConnection.bulkTransfer(inEndPoint, res, 24, 3000)
        val r6 = usbDeviceConnection.bulkTransfer(inEndPoint, csw, 13, 3000)

        val r7 = usbDeviceConnection.releaseInterface(usbInterface)
        usbDeviceConnection.close()
        return@withContext res[4].toInt() == 0;
    }
}