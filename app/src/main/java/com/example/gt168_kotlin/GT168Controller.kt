package com.example.gt168_kotlin


import android.hardware.usb.*
import kotlinx.coroutines.*

class GT168Controller(private val usbManager: UsbManager, private val device: UsbDevice) {

    companion object {
        private const val VID = 0x2009
        private const val PID = 0x7638

        private const val USB_CBW_SIG = 0x43425355
        private const val USB_CBW_TUN = 0x89182B28
        private const val USB_CBW_IN = 0x00.toByte()
        private const val USB_CBW_OUT = 0x80.toByte()
        private const val USB_CBW_LUN = 0x00.toByte()

        fun getDevice(usbManager: UsbManager): UsbDevice? = usbManager
            .deviceList.values.firstOrNull { it.vendorId == VID && it.productId == PID }

        fun Long.toByteArray(): ByteArray = byteArrayOf(
            (this and 0xFFFF).toByte(),
            ((this ushr 8) and 0xFFFF).toByte(),
            ((this ushr 16) and 0xFFFF).toByte(),
            ((this ushr 24) and 0xFFFF).toByte()
        )

        fun Int.toByteArray(): ByteArray = byteArrayOf(
            (this and 0xFFFF).toByte(),
            ((this ushr 8) and 0xFFFF).toByte(),
            ((this ushr 16) and 0xFFFF).toByte(),
            ((this ushr 24) and 0xFFFF).toByte()
        )

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
            *USB_CBW_SIG.toByteArray(),
            *USB_CBW_TUN.toByteArray(),
            *0.toByteArray(),
            USB_CBW_IN,
            USB_CBW_LUN,
            0x0A,
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
        cbw[0x0c] = USB_CBW_OUT
        cbw[0x10] = 0x12.toByte()
        val r4 = usbDeviceConnection.bulkTransfer(outEndpoint, cbw, 31, 3000)
        val r5 = usbDeviceConnection.bulkTransfer(inEndPoint, res, 24, 3000)
        val r6 = usbDeviceConnection.bulkTransfer(inEndPoint, csw, 13, 3000)

        val r7 = usbDeviceConnection.releaseInterface(usbInterface)
        usbDeviceConnection.close()
        return@withContext res[4].toInt() == 0;
    }
}