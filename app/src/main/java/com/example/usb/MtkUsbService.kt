package com.example.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import com.example.model.MtkDeviceInfo
import com.example.model.MtkModel
import com.example.protocol.AuthHandlerType
import com.example.protocol.MtkAuthHandlerFactory
import com.example.protocol.MtkBromProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MtkUsbService(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onLog: (level: String, message: String) -> Unit,
    private val onDeviceFound: (UsbDevice) -> Unit,
    private val onDeviceInfoRead: (MtkDeviceInfo) -> Unit,
    private val onStateChanged: (Boolean) -> Unit
) {

    companion object {
        const val ACTION_USB_PERMISSION = "com.example.USB_PERMISSION"
        const val MTK_VENDOR_ID = 0x0E8D // 3725

        // Known MediaTek PIDs
        const val PID_MTK_BROM = 0x0003
        const val PID_MTK_PRELOADER = 0x2000
        const val PID_MTK_DA = 0x2001
        const val PID_MTK_GENERIC = 0x0616
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbConnection: UsbDeviceConnection? = null
    private val claimedInterfaces = mutableListOf<UsbInterface>()
    private var targetDevice: UsbDevice? = null
    private var isReceiverRegistered = false

    // Auto-handshake trigger when waiting for BROM
    var isWaitingForBrom: Boolean = false
    var activeModel: MtkModel? = null
    var activeAuthHandler: AuthHandlerType = AuthHandlerType.KAMAKIRI_EP0

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                val action = intent?.action ?: return
                when (action) {
                    ACTION_USB_PERMISSION -> {
                        synchronized(this) {
                            val device = getDeviceFromIntent(intent)
                            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            if (granted && device != null) {
                                val devName = getSafeDeviceName(device)
                                onLog("USB", "USB Permission GRANTED for $devName")
                                targetDevice = device
                                onDeviceFound(device)
                                if (isWaitingForBrom && activeModel != null) {
                                    onLog("BROM", "Instant BROM trigger: executing handshake immediately...")
                                    coroutineScope.launch {
                                        executeServiceRoutine(activeModel!!, activeAuthHandler)
                                    }
                                }
                            } else {
                                onLog("ERROR", "USB Permission was denied. Please grant permission to allow BROM access.")
                                onStateChanged(false)
                                isWaitingForBrom = false
                            }
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        val device = getDeviceFromIntent(intent)
                        onLog("USB", "USB Device ATTACHED event received.")
                        if (device != null) {
                            handleAttachedDevice(device)
                        } else {
                            scanAndConnectDevice()
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        onLog("WARNING", "USB Device disconnected from OTG port.")
                        closeConnection()
                        onStateChanged(false)
                    }
                }
            } catch (e: Exception) {
                onLog("ERROR", "USB Event Exception: ${e.localizedMessage}")
            }
        }
    }

    private fun getSafeDeviceName(device: UsbDevice?): String {
        if (device == null) return "Unknown Device"
        val vidHex = String.format("%04X", device.vendorId)
        val pidHex = String.format("%04X", device.productId)
        val prodName = try {
            if (usbManager.hasPermission(device)) device.productName else null
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
        return prodName ?: "MTK Device [VID:0x$vidHex, PID:0x$pidHex]"
    }

    private fun getDeviceFromIntent(intent: Intent): UsbDevice? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun handleIncomingIntent(intent: Intent) {
        try {
            val action = intent.action ?: return
            if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                val device = getDeviceFromIntent(intent)
                if (device != null) {
                    onLog("USB", "Direct USB Attached Intent received from Android System.")
                    handleAttachedDevice(device)
                }
            }
        } catch (e: Exception) {
            onLog("ERROR", "Incoming Intent Error: ${e.localizedMessage}")
        }
    }

    private fun handleAttachedDevice(device: UsbDevice) {
        try {
            val vid = device.vendorId
            val pid = device.productId
            if (vid == MTK_VENDOR_ID || isKnownMtkPid(pid)) {
                onLog("SUCCESS", "MediaTek Device detected: [VID:0x${String.format("%04X", vid)}, PID:0x${String.format("%04X", pid)}] -> ${getMtkModeName(pid)}")
                targetDevice = device
                onDeviceFound(device)

                val hasPerm = try {
                    usbManager.hasPermission(device)
                } catch (_: Exception) {
                    false
                }

                if (hasPerm) {
                    if (isWaitingForBrom && activeModel != null) {
                        onLog("BROM", "Permission already present! Starting instant BROM handshake...")
                        coroutineScope.launch {
                            executeServiceRoutine(activeModel!!, activeAuthHandler)
                        }
                    }
                } else {
                    requestDevicePermission(device)
                }
            }
        } catch (e: Exception) {
            onLog("ERROR", "Handle device error: ${e.localizedMessage}")
        }
    }

    fun registerReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(ACTION_USB_PERMISSION)
                    addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                    addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(usbReceiver, filter)
                }
                isReceiverRegistered = true
            } catch (e: Exception) {
                onLog("WARNING", "Receiver register fallback: ${e.localizedMessage}")
                try {
                    val filter = IntentFilter(ACTION_USB_PERMISSION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
                    } else {
                        context.registerReceiver(usbReceiver, filter)
                    }
                    isReceiverRegistered = true
                } catch (_: Exception) {}
            }
        }
    }

    fun unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(usbReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        closeConnection()
    }

    private fun requestDevicePermission(device: UsbDevice) {
        try {
            onLog("USB", "Requesting USB Host permissions from Android system...")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val intent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(context.packageName)
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                flags
            )
            usbManager.requestPermission(device, permissionIntent)
        } catch (e: Exception) {
            onLog("ERROR", "Failed to request USB permission: ${e.localizedMessage}")
        }
    }

    fun scanAndConnectDevice(): UsbDevice? {
        try {
            val deviceList = usbManager.deviceList ?: return null
            for ((_, device) in deviceList) {
                val vid = device.vendorId
                val pid = device.productId
                if (vid == MTK_VENDOR_ID || isKnownMtkPid(pid)) {
                    targetDevice = device
                    onDeviceFound(device)
                    val hasPerm = try {
                        usbManager.hasPermission(device)
                    } catch (_: Exception) {
                        false
                    }
                    if (!hasPerm) {
                        requestDevicePermission(device)
                    }
                    return device
                }
            }
        } catch (e: Exception) {
            onLog("ERROR", "USB Scan Error: ${e.localizedMessage}")
        }
        return null
    }

    suspend fun executeServiceRoutine(
        selectedModel: MtkModel,
        authHandlerType: AuthHandlerType = AuthHandlerType.KAMAKIRI_EP0
    ): Boolean = withContext(Dispatchers.IO) {
        activeModel = selectedModel
        activeAuthHandler = authHandlerType
        onStateChanged(true)
        onLog("SYSTEM", "Starting MTK Service routine for [${selectedModel.name}] using [${authHandlerType.title}]...")

        var device = targetDevice ?: scanAndConnectDevice()

        if (device == null) {
            isWaitingForBrom = true
            onLog("WARNING", "[WAITING FOR BROM] No device detected yet.")
            onLog("USB", ">>> HOW TO CONNECT:")
            onLog("USB", " 1. Power off device completely.")
            onLog("USB", " 2. Press & Hold Volume Up (+) and Volume Down (-).")
            onLog("USB", " 3. Plug in OTG USB Cable.")
            onLog("USB", ">>> Instant Auto-Handshake is active! Once plugged in, handshake starts in ~10ms.")
            return@withContext false
        }

        val hasPerm = try {
            usbManager.hasPermission(device)
        } catch (_: Exception) {
            false
        }

        if (!hasPerm) {
            isWaitingForBrom = true
            requestDevicePermission(device)
            onLog("WARNING", "Awaiting USB Permission grant. Please accept the prompt on screen.")
            return@withContext false
        }

        isWaitingForBrom = false

        val connection = try {
            usbManager.openDevice(device)
        } catch (e: Exception) {
            onLog("ERROR", "Exception opening USB Device: ${e.localizedMessage}")
            null
        }

        if (connection == null) {
            onLog("ERROR", "Failed to open USB connection to MTK device. Ensure OTG is enabled in Settings.")
            onStateChanged(false)
            return@withContext false
        }
        usbConnection = connection

        if (device.interfaceCount == 0) {
            onLog("ERROR", "No USB Interfaces found on device. Please reconnect phone in BROM mode.")
            closeConnection()
            onStateChanged(false)
            return@withContext false
        }

        // Claim ALL available USB Interfaces
        claimedInterfaces.clear()
        for (i in 0 until device.interfaceCount) {
            val intf = try {
                device.getInterface(i)
            } catch (_: Exception) {
                null
            }
            if (intf != null) {
                val claimed = connection.claimInterface(intf, true)
                if (claimed) {
                    claimedInterfaces.add(intf)
                    onLog("USB", "Claimed USB Interface #$i (Class: ${intf.interfaceClass}, Endpoints: ${intf.endpointCount})")
                }
            }
        }

        // Search for Bulk IN and Bulk OUT endpoints across all interfaces
        var inEndpoint: UsbEndpoint? = null
        var outEndpoint: UsbEndpoint? = null

        for (intf in claimedInterfaces) {
            for (i in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN && inEndpoint == null) {
                        inEndpoint = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT && outEndpoint == null) {
                        outEndpoint = ep
                    }
                }
            }
        }

        // Fallback: If not found in claimed interfaces, check all device interfaces
        if (inEndpoint == null || outEndpoint == null) {
            for (intfIdx in 0 until device.interfaceCount) {
                val intf = device.getInterface(intfIdx)
                for (epIdx in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(epIdx)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN && inEndpoint == null) inEndpoint = ep
                        if (ep.direction == UsbConstants.USB_DIR_OUT && outEndpoint == null) outEndpoint = ep
                    }
                }
            }
        }

        val inEpStr = inEndpoint?.let { "EP${it.endpointNumber} (0x${String.format("%02X", it.address)})" } ?: "NOT FOUND"
        val outEpStr = outEndpoint?.let { "EP${it.endpointNumber} (0x${String.format("%02X", it.address)})" } ?: "NOT FOUND"
        onLog("USB", "Bulk IN: $inEpStr, Bulk OUT: $outEpStr")

        if (inEndpoint == null || outEndpoint == null) {
            onLog("ERROR", "Unable to bind USB Bulk Transfer endpoints for BROM serial protocol.")
            closeConnection()
            onStateChanged(false)
            return@withContext false
        }

        // Configure USB CDC ACM Line Coding and Assert DTR/RTS
        try {
            // SET_LINE_CODING: 115200 8N1 (115200 baud = 0x0001C200)
            val lineCoding = byteArrayOf(
                0x00, 0xC2.toByte(), 0x01, 0x00, // 115200
                0x00, // 1 stop bit
                0x00, // No parity
                0x08  // 8 data bits
            )
            connection.controlTransfer(0x21, 0x20, 0, 0, lineCoding, lineCoding.size, 500)
            // SET_CONTROL_LINE_STATE: DTR (0x01) | RTS (0x02) = 0x03
            connection.controlTransfer(0x21, 0x22, 0x03, 0, null, 0, 500)
            onLog("USB", "CDC Line State initialized: 115200 8N1 (DTR/RTS Asserted)")
        } catch (_: Exception) {}

        val protocol = MtkBromProtocol(connection, inEndpoint, outEndpoint) { level, msg ->
            onLog(level, msg)
        }

        try {
            // 1. Strict Byte-to-Byte Handshake
            val handshakeOk = protocol.performHandshake()
            if (!handshakeOk) {
                onLog("WARNING", "Handshake sync incomplete, continuing auth sequence...")
            }

            // 2. Multi-Auth Handler Execution
            val handler = MtkAuthHandlerFactory.create(authHandlerType)
            val authOk = handler.execute(
                connection = connection,
                inEndpoint = inEndpoint,
                outEndpoint = outEndpoint,
                model = selectedModel,
                protocol = protocol,
                onLog = { lvl, msg -> onLog(lvl, msg) }
            )

            if (!authOk) {
                onLog("WARNING", "Auth bypass step reported warning. Proceeding to BROM Read routine...")
            }

            // 3. Read Device Info (Regardless of auth bypass level)
            val info = protocol.readDeviceInfo()
            onDeviceInfoRead(info)

            // 4. Auto Reboot Device via Watchdog Timer
            val wdtBase = if (selectedModel.wdtAddress != 0L) selectedModel.wdtAddress else 0x10007000L
            onLog("INFO", "Full Device Information retrieved successfully.")
            onLog("SYSTEM", "Executing Auto-Reboot routine on target MTK device...")
            protocol.rebootDevice(wdtBase)

            onLog("SUCCESS", "=== MTK OPERATION COMPLETED ===")
            onLog("READY", "Device reboot initiated. Disconnecting USB interface.")
        } catch (e: Exception) {
            onLog("ERROR", "Protocol execution error: ${e.localizedMessage}")
        } finally {
            closeConnection()
            onStateChanged(false)
        }

        return@withContext true
    }

    private fun isKnownMtkPid(pid: Int): Boolean {
        return pid == PID_MTK_BROM || pid == PID_MTK_PRELOADER || pid == PID_MTK_DA || pid == PID_MTK_GENERIC
    }

    private fun getMtkModeName(pid: Int): String {
        return when (pid) {
            PID_MTK_BROM -> "BROM Mode (BootROM 0x0003)"
            PID_MTK_PRELOADER -> "Preloader Mode (0x2000)"
            PID_MTK_DA -> "Download Agent (DA 0x2001)"
            PID_MTK_GENERIC -> "MediaTek USB (0x0616)"
            else -> "MediaTek Port (0x${String.format("%04X", pid)})"
        }
    }

    fun closeConnection() {
        try {
            for (intf in claimedInterfaces) {
                usbConnection?.releaseInterface(intf)
            }
            claimedInterfaces.clear()
            usbConnection?.close()
        } catch (_: Exception) {}
        usbConnection = null
    }
}
