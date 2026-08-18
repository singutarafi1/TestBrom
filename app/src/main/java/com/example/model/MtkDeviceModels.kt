package com.example.model

data class Brand(
    val id: String,
    val name: String,
    val models: List<MtkModel>
)

data class MtkModel(
    val id: String,
    val name: String,
    val chipset: String,
    val hwCode: Int,
    val sramAddress: Long,
    val wdtAddress: Long,
    val payloadType: String,
    val slaRequired: Boolean = true,
    val daaRequired: Boolean = true
)

data class MtkDeviceInfo(
    val chipset: String = "",
    val hwCode: String = "",
    val hwSubCode: String = "",
    val hwVersion: String = "",
    val swVersion: String = "",
    val targetConfig: String = "",
    val sbcEnabled: Boolean = false,
    val slaEnabled: Boolean = false,
    val daaEnabled: Boolean = false,
    val meid: String = "",
    val socId: String = "",
    val bromStatus: String = "Ready",
    val authBypassed: Boolean = false
)

enum class LogLevel {
    SYSTEM,
    USB,
    BROM,
    SLA,
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class LogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: String,
    val level: LogLevel,
    val message: String
)

object MtkDatabase {
    val chipsets = mapOf(
        0x0766 to "MediaTek MT6765 (Helio G25 / G35 / P35)",
        0x0707 to "MediaTek MT6768 (Helio G80 / G85)",
        0x0788 to "MediaTek MT6785 (Helio G90 / G90T / G95)",
        0x0658 to "MediaTek MT6761 (Helio A22 / A20)",
        0x0326 to "MediaTek MT6762 (Helio P22)",
        0x0335 to "MediaTek MT6735 (Quad-Core LTE)",
        0x0321 to "MediaTek MT6739 (Entry Quad-Core)",
        0x0279 to "MediaTek MT6797 (Helio X20 / X25)",
        0x0717 to "MediaTek MT6771 (Helio P60 / P70)",
        0x0816 to "MediaTek MT6833 (Dimensity 700 / 810 5G)",
        0x0813 to "MediaTek MT6877 (Dimensity 900 / 920 / 1080 5G)",
        0x0726 to "MediaTek MT6769 (Helio G88 / G91)",
        0x0989 to "MediaTek MT6789 (Helio G99 / G99 Ultra)",
        0x0650 to "MediaTek MT6580 (32-bit Quad-Core)"
    )

    val brands: List<Brand> = listOf(
        Brand(
            id = "auto",
            name = "Auto-Detect / Generic MTK",
            models = listOf(
                MtkModel("auto_all", "Auto-Detect (Any MTK Chipset)", "Generic BROM", 0x0000, 0x00102100L, 0x10007000L, "generic_payload"),
                MtkModel("mt6765_gen", "MT6765 / MT6762 (Helio P35/G35/P22)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("mt6768_gen", "MT6768 (Helio G80 / G85)", "MT6768", 0x0707, 0x00102100L, 0x10007000L, "kama_6768"),
                MtkModel("mt6785_gen", "MT6785 (Helio G90 / G90T / G95)", "MT6785", 0x0788, 0x00102100L, 0x10007000L, "kama_6785"),
                MtkModel("mt6761_gen", "MT6761 (Helio A22)", "MT6761", 0x0658, 0x00102100L, 0x10007000L, "kama_6761"),
                MtkModel("mt6833_gen", "MT6833 (Dimensity 700 / 810 5G)", "MT6833", 0x0816, 0x00200000L, 0x10007000L, "kama_6833"),
                MtkModel("mt6877_gen", "MT6877 (Dimensity 900 / 1080 5G)", "MT6877", 0x0813, 0x00200000L, 0x10007000L, "kama_6877"),
                MtkModel("mt6771_gen", "MT6771 (Helio P60 / P70)", "MT6771", 0x0717, 0x00102100L, 0x10007000L, "kama_6771"),
                MtkModel("mt6739_gen", "MT6739 (Quad-Core LTE)", "MT6739", 0x0321, 0x00102100L, 0x10007000L, "kama_6739"),
                MtkModel("mt6580_gen", "MT6580 (Legacy 32-Bit)", "MT6580", 0x0650, 0x00102100L, 0x10007000L, "kama_6580")
            )
        ),
        Brand(
            id = "xiaomi",
            name = "Xiaomi / Redmi / POCO",
            models = listOf(
                MtkModel("redmi_9", "Redmi 9 / Prime (MT6768)", "MT6768", 0x0707, 0x00102100L, 0x10007000L, "kama_6768"),
                MtkModel("redmi_9a", "Redmi 9A / 9AT (MT6765)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("redmi_9c", "Redmi 9C / NFC (MT6765)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("redmi_note_8_pro", "Redmi Note 8 Pro (MT6785 Begonia)", "MT6785", 0x0788, 0x00102100L, 0x10007000L, "kama_6785"),
                MtkModel("redmi_note_9", "Redmi Note 9 (MT6768 Merlin)", "MT6768", 0x0707, 0x00102100L, 0x10007000L, "kama_6768"),
                MtkModel("redmi_note_10s", "Redmi Note 10S (MT6785 Rosemary)", "MT6785", 0x0788, 0x00102100L, 0x10007000L, "kama_6785"),
                MtkModel("redmi_note_10_5g", "Redmi Note 10 5G (MT6833 Camellian)", "MT6833", 0x0816, 0x00200000L, 0x10007000L, "kama_6833"),
                MtkModel("redmi_note_11s", "Redmi Note 11S / 4G (MT6781 Fleur)", "MT6781", 0x0788, 0x00102100L, 0x10007000L, "kama_6785"),
                MtkModel("redmi_10_5g", "Redmi 10 5G / Note 11E (MT6833 Light)", "MT6833", 0x0816, 0x00200000L, 0x10007000L, "kama_6833"),
                MtkModel("poco_m3_pro", "POCO M3 Pro 5G (MT6833 Camellian)", "MT6833", 0x0816, 0x00200000L, 0x10007000L, "kama_6833"),
                MtkModel("poco_m4_pro_4g", "POCO M4 Pro 4G (MT6781 Fleur)", "MT6781", 0x0788, 0x00102100L, 0x10007000L, "kama_6785"),
                MtkModel("xiaomi_11t", "Xiaomi 11T (MT6893 Agate)", "MT6893", 0x0813, 0x00200000L, 0x10007000L, "kama_6877")
            )
        ),
        Brand(
            id = "samsung",
            name = "Samsung Galaxy",
            models = listOf(
                MtkModel("a01_core", "Galaxy A01 Core (MT6739 SM-A013F)", "MT6739", 0x0321, 0x00102100L, 0x10007000L, "kama_6739"),
                MtkModel("a02", "Galaxy A02 (MT6739 SM-A022F)", "MT6739", 0x0321, 0x00102100L, 0x10007000L, "kama_6739"),
                MtkModel("a03s", "Galaxy A03s (MT6765 SM-A037F)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("a04", "Galaxy A04 (MT6765 SM-A045F)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("a04e", "Galaxy A04e (MT6765 SM-A042F)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("a12", "Galaxy A12 (MT6765 SM-A125F)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("a13_5g", "Galaxy A13 5G (MT6833 SM-A136U/B)", "MT6833", 0x0816, 0x00200000L, 0x10007000L, "kama_6833"),
                MtkModel("a14_5g", "Galaxy A14 5G (MT6833 SM-A146P)", "MT6833", 0x0816, 0x00200000L, 0x10007000L, "kama_6833"),
                MtkModel("a22", "Galaxy A22 (MT6769 SM-A225F)", "MT6769", 0x0707, 0x00102100L, 0x10007000L, "kama_6768"),
                MtkModel("a22_5g", "Galaxy A22 5G (MT6833 SM-A226B)", "MT6833", 0x0816, 0x00200000L, 0x10007000L, "kama_6833"),
                MtkModel("a31", "Galaxy A31 (MT6768 SM-A315F)", "MT6768", 0x0707, 0x00102100L, 0x10007000L, "kama_6768"),
                MtkModel("a32", "Galaxy A32 4G (MT6769 SM-A325F)", "MT6769", 0x0707, 0x00102100L, 0x10007000L, "kama_6768"),
                MtkModel("a34_5g", "Galaxy A34 5G (MT6877V SM-A346E)", "MT6877", 0x0813, 0x00200000L, 0x10007000L, "kama_6877")
            )
        ),
        Brand(
            id = "oppo_realme",
            name = "OPPO / Realme",
            models = listOf(
                MtkModel("oppo_a15", "OPPO A15 / A15s (MT6765 CPH2185)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("oppo_a16", "OPPO A16 / A16s (MT6765 CPH2269)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("oppo_a31", "OPPO A31 2020 (MT6765 CPH2015)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("oppo_a54", "OPPO A54 (MT6765 CPH2239)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("realme_c11", "Realme C11 2020 (MT6765 RMX2185)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("realme_c12", "Realme C12 / C15 (MT6765 RMX2189)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("realme_c21", "Realme C21 (MT6765 RMX3201)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("realme_6", "Realme 6 / 6s (MT6785 RMX2001)", "MT6785", 0x0788, 0x00102100L, 0x10007000L, "kama_6785"),
                MtkModel("realme_7", "Realme 7 (MT6785 RMX2151)", "MT6785", 0x0788, 0x00102100L, 0x10007000L, "kama_6785"),
                MtkModel("realme_8", "Realme 8 4G (MT6785 RMX3085)", "MT6785", 0x0788, 0x00102100L, 0x10007000L, "kama_6785"),
                MtkModel("realme_8_5g", "Realme 8 5G (MT6833 RMX3241)", "MT6833", 0x0816, 0x00200000L, 0x10007000L, "kama_6833")
            )
        ),
        Brand(
            id = "vivo",
            name = "Vivo / iQOO",
            models = listOf(
                MtkModel("vivo_y12", "Vivo Y12 / Y15 / Y17 (MT6765 1901)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("vivo_y20", "Vivo Y20 2021 / Y20A (MT6765 V2044)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("vivo_y21", "Vivo Y21 2021 (MT6765 V2111)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("vivo_y30", "Vivo Y30 (MT6765 1938)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("vivo_y91", "Vivo Y91 / Y93 / Y95 (MT6762 1816)", "MT6762", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("vivo_v21_5g", "Vivo V21 5G (MT6853 V2050)", "MT6853", 0x0816, 0x00200000L, 0x10007000L, "kama_6833"),
                MtkModel("vivo_v23_5g", "Vivo V23 5G (MT6877 V2130)", "MT6877", 0x0813, 0x00200000L, 0x10007000L, "kama_6877")
            )
        ),
        Brand(
            id = "infinix_tecno",
            name = "Infinix / Tecno / Itel",
            models = listOf(
                MtkModel("inf_hot_8", "Infinix Hot 8 (MT6761/MT6762 X650)", "MT6761", 0x0658, 0x00102100L, 0x10007000L, "kama_6761"),
                MtkModel("inf_hot_9", "Infinix Hot 9 / Hot 9 Pro (MT6762 X655)", "MT6762", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("inf_hot_10", "Infinix Hot 10 (MT6768 X682)", "MT6768", 0x0707, 0x00102100L, 0x10007000L, "kama_6768"),
                MtkModel("inf_hot_10_play", "Infinix Hot 10 Play (MT6765 X688)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("inf_hot_11", "Infinix Hot 11 (MT6768 X662)", "MT6768", 0x0707, 0x00102100L, 0x10007000L, "kama_6768"),
                MtkModel("inf_note_10", "Infinix Note 10 / Note 10 Pro (MT6785 X693)", "MT6785", 0x0788, 0x00102100L, 0x10007000L, "kama_6785"),
                MtkModel("inf_note_11", "Infinix Note 11 (MT6781 X663)", "MT6781", 0x0788, 0x00102100L, 0x10007000L, "kama_6785"),
                MtkModel("inf_note_12", "Infinix Note 12 G96 (MT6781 X670)", "MT6781", 0x0788, 0x00102100L, 0x10007000L, "kama_6785"),
                MtkModel("tecno_spark_6", "Tecno Spark 6 (MT6768 KE7)", "MT6768", 0x0707, 0x00102100L, 0x10007000L, "kama_6768"),
                MtkModel("tecno_spark_7", "Tecno Spark 7 (MT6761 KF6)", "MT6761", 0x0658, 0x00102100L, 0x10007000L, "kama_6761"),
                MtkModel("tecno_spark_8", "Tecno Spark 8 / 8P (MT6765/MT6769 KG6)", "MT6765", 0x0766, 0x00102100L, 0x10007000L, "kama_6765"),
                MtkModel("tecno_pova_2", "Tecno Pova 2 (MT6769 LE7)", "MT6769", 0x0707, 0x00102100L, 0x10007000L, "kama_6768"),
                MtkModel("tecno_camon_17", "Tecno Camon 17 / 17 Pro (MT6785 CG7)", "MT6785", 0x0788, 0x00102100L, 0x10007000L, "kama_6785")
            )
        )
    )
}
