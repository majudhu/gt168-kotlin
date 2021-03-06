package com.example.gt168_kotlin

import android.hardware.usb.*
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

        private const val CMD_PKT_PRFX = 0xAA55 // command packet prefix (out)
        private const val RES_PKT_PRFX = 0x55AA // response packet prefix (out)
        private const val CMD_DAT_PKT_PRFX = 0xA55A // command data packet prefix (in)
        private const val RES_DAT_PKT_PRFX = 0xA55A // response data packet prefix (in)

        private const val CMD_ENROLL = 0X0103
        private const val CMD_VERIFY = 0X0101
        private const val CMD_IDENTIFY = 0X0102
        private const val CMD_IDENTIFY_FREE = 0X0125
        private const val CMD_ENROLL_ONE_TIME = 0X0104
        private const val CMD_CHANGE_TEMPLATE = 0X0129
        private const val CMD_FP_CANCEL = 0X0130
        private const val CMD_CLEAR_TEMPLATE = 0X0105
        private const val CMD_CLEAR_ALL_TEMPLATES = 0X0106
        private const val CMD_GET_EMPTY_ID = 0X0107
        private const val CMD_GET_BROKEN_TEMPLATE = 0X0109
        private const val CMD_READ_TEMPLATE = 0X010A
        private const val CMD_WRITE_TEMPLATE = 0X010B
        private const val CMD_SET_PARAMETER = 0X013A
        private const val CMD_GET_FW_VERSION = 0X0112
        private const val CMD_FINGER_DETECT = 0X0113
        private const val CMD_GET_FEATURE_DATA_OF_CAPTURED_FP = 0X011A
        private const val CMD_IDENTIFY_DOWNLOADED_FEATURE_WITH_CAPTURED_FP = 0X011C
        private const val CMD_SENSOR_LED_CONTROL = 0X0124
        private const val CMD_SET_DEVICE_PASSWORD = 0X0126
        private const val CMD_VERIFY_DEVICE_PASSWORD = 0X0127
        private const val CMD_EXIT_DEVICE_PASSWORD = 0X013B
        private const val CMD_GET_ENROLL_COUNT = 0X0128
        private const val CMD_UP_IMAGE = 0X012C
        private const val CMD_IDENTIFY_WITH_IMAGE = 0X0138
        private const val CMD_VERIFY_WITH_IMAGE = 0X0139
        private const val CMD_IDENTIFY_WITH_TEMPLATE = 0X012E
        private const val CMD_VERIFY_WITH_TEMPLATE = 0X012D
        private const val CMD_ADJUST_SENSOR = 0X0137
        private const val CMD_TEST_CONNECTION = 0x0150

        val CBW_DATA_OUT get() = byteArrayOf(0xEF.toByte(), 0x11, 0x00, 0x00, 0x18)
        val CBW_DATA_IN get() = byteArrayOf(0xEF.toByte(), 0x12, 0x00, 0x00, 0x18)


        fun getDevice(usbManager: UsbManager): UsbDevice? = usbManager
            .deviceList.values.firstOrNull { it.vendorId == VID && it.productId == PID }

        fun Long.toByteArray(): ByteArray = byteArrayOf(
            this.toByte(),
            (this ushr 8).toByte(),
            (this ushr 16).toByte(),
            (this ushr 24).toByte()
        )

        fun Int.to4ByteArray(): ByteArray = byteArrayOf(
            this.toByte(),
            (this ushr 8).toByte(),
            (this ushr 16).toByte(),
            (this ushr 24).toByte()
        )


        fun Int.to2ByteArray(): ByteArray = byteArrayOf(
            this.toByte().also { print(it) },
            (this ushr 8).toByte().also { print(it) }
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

    private suspend fun sendCommandPacket(cmdCode: Int): ByteArray = withContext(Dispatchers.IO) {
        val cbwOut = byteArrayOf(    // command block wrapper
            *CBW_SIG.to4ByteArray(),  // signature
            *CBW_TUN.toByteArray(),  // tag (transaction unique identifier)
            *ByteArray(4),      // length
            CBW_OUT,                 // direction out
            CBW_LUN,                 // logical unit number
            CBW_LEN,                 // data length
            *CBW_DATA_OUT,           // data
            *ByteArray(11)      // zerofill
        )
        val cbwIn = byteArrayOf(     // command block wrapper
            *CBW_SIG.to4ByteArray(),  // signature
            *CBW_TUN.toByteArray(),  // tag (transaction unique identifier)
            *ByteArray(4),      // length
            CBW_IN,                  // direction out
            CBW_LUN,                 // logical unit number
            CBW_LEN,                 // data length
            *CBW_DATA_IN,            // data
            *ByteArray(11)      // zerofill
        )

        val command = byteArrayOf(
            *CMD_PKT_PRFX.to2ByteArray(),
            *cmdCode.to2ByteArray(),
            *ByteArray(18)
        ).let { byteArrayOf(*it, *it.sum().to2ByteArray()) }

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
            && csw.take(8) == cbwOut.take(8) // validate csw
        ) {
            connection.releaseInterface(usbInterface)
            connection.close()
            return@withContext ByteArray(24)
        }
        while ( // receive response packet
            (connection.bulkTransfer(outEndpoint, cbwIn, CBW_PKT_LEN, IO_TIMEOUT) != CBW_PKT_LEN)
            &&
            (connection.bulkTransfer(inEndPoint, res, CMD_PKT_LEN, IO_TIMEOUT) != CMD_PKT_LEN)
            &&
            (connection.bulkTransfer(inEndPoint, csw, CSW_PKT_LEN, IO_TIMEOUT) != CSW_PKT_LEN)
            && csw.take(8) == cbwOut.take(8) // validate csw
            && res.all { it == CMD_WAIT } // check for wait signal
        ) {
            delay(250)
        }
        connection.releaseInterface(usbInterface)
        connection.close()

        return@withContext res.slice(6..18).toByteArray()
    }

    suspend fun testConnection(): Boolean {
        val response = sendCommandPacket(CMD_TEST_CONNECTION)
        return (response[6].toInt() == 0 && response[7].toInt() == 0)
    }

}