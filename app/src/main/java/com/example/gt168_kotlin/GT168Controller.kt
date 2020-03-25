package com.example.gt168_kotlin

import android.hardware.usb.*
import android.view.View
import kotlinx.coroutines.*

class GT168Controller(private val usbManager: UsbManager, private val device: UsbDevice) {

    companion object {
        private const val VID = 0x2009  // USB Vendor ID
        private const val PID = 0x7638  // USB Product ID

        private const val IO_TIMEOUT = 3000  // USB Read/Write timeout

        private const val CBW_SIG = 0x43425355          // Command Block Wrapper Signature
        private const val CBW_TUN = 0x89182B28          // Transaction Unique Identifier
        private const val CBW_IN = 0x00.toByte()  // Direction: to Device
        private const val CBW_OUT = 0x80.toByte() // Direction: to Host
        private const val CBW_LUN = 0x00.toByte() // Logical Unit Number
        private const val CBW_LEN = 0x0A.toByte() // Command Block Length
        private const val CBW_PKT_LEN = 31  // Command Block Wrapper Packet Length
        private const val CMD_PKT_LEN = 24  // Command/Response Packet Length
        private const val CSW_PKT_LEN = 13  // Command Status Wrapper Packet Length
        private const val CMD_WAIT = 0xAF.toByte()

        val CBW_DATA_OUT get() = byteArrayOf(0xEF.toByte(), 0x11, 0x00, 0x00, 0x18)
        val CBW_DATA_IN get() = byteArrayOf(0xEF.toByte(), 0x12, 0x00, 0x00, 0x18)


        fun getDevice(usbManager: UsbManager): UsbDevice? = usbManager
            .deviceList.values.firstOrNull { it.vendorId == VID && it.productId == PID }

        fun Long.toByteArray(): ByteArray = byteArrayOf(
            ((this ushr 24) and 0xFFFF).toByte(),
            ((this ushr 16) and 0xFFFF).toByte(),
            ((this ushr 8) and 0xFFFF).toByte(),
            (this and 0xFFFF).toByte()
        )

        fun Int.toByteArray(): ByteArray = byteArrayOf(
            ((this ushr 24) and 0xFFFF).toByte(),
            ((this ushr 16) and 0xFFFF).toByte(),
            ((this ushr 8) and 0xFFFF).toByte(),
            (this and 0xFFFF).toByte()
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

    suspend fun sendCommandPacket(): Boolean = withContext(Dispatchers.IO) {
        val cbwOut = byteArrayOf(    // command block wrapper
            *CBW_SIG.toByteArray(),  // signature
            *CBW_TUN.toByteArray(),  // tag (transaction unique identifier)
            *ByteArray(4),      // length
            CBW_OUT,                 // direction out
            CBW_LUN,                 // logical unit number
            CBW_LEN,                 // data length
            *CBW_DATA_OUT,           // data
            *ByteArray(11)      // zerofill
        )
        val cbwIn = byteArrayOf(     // command block wrapper
            *CBW_SIG.toByteArray(),  // signature
            *CBW_TUN.toByteArray(),  // tag (transaction unique identifier)
            *ByteArray(4),      // length
            CBW_IN,                  // direction out
            CBW_LUN,                 // logical unit number
            CBW_LEN,                 // data length
            *CBW_DATA_IN,            // data
            *ByteArray(11)      // zerofill
        )
        val command = byteArrayOf(0x55, 0xAA.toByte(), 0x50, 0x01, *ByteArray(18), 0x50, 0x01)
        val connection = usbManager.openDevice(device)
        val csw = ByteArray(CSW_PKT_LEN)  // command status wrapper
        val res = ByteArray(CMD_PKT_LEN); // command response packet
        connection.claimInterface(usbInterface, true)
        if ( // send command packet
            (connection.bulkTransfer(outEndpoint, cbwOut, CBW_PKT_LEN, IO_TIMEOUT) != CBW_PKT_LEN)
            &&
            (connection.bulkTransfer(outEndpoint, command, CMD_PKT_LEN, IO_TIMEOUT) != CMD_PKT_LEN)
            &&
            (connection.bulkTransfer(inEndPoint, csw, CSW_PKT_LEN, IO_TIMEOUT) != CSW_PKT_LEN)
            && csw.take(8) == cbwOut.take(8)
        ) {
            connection.releaseInterface(usbInterface)
            connection.close()
            return@withContext false
        }
        while ( // receive response packet
            (connection.bulkTransfer(outEndpoint, cbwOut, CBW_PKT_LEN, IO_TIMEOUT) != CBW_PKT_LEN)
            &&
            (connection.bulkTransfer(inEndPoint, res, CMD_PKT_LEN, IO_TIMEOUT) != CMD_PKT_LEN)
            &&
            (connection.bulkTransfer(inEndPoint, csw, CSW_PKT_LEN, IO_TIMEOUT) != CSW_PKT_LEN)
            && csw.take(8) == cbwOut.take(8)
            && res.all { it == CMD_WAIT }
        ) {
            delay(250)
        }
        connection.releaseInterface(usbInterface)
        connection.close()

        // todo: verify res

        return@withContext true;
    }

}