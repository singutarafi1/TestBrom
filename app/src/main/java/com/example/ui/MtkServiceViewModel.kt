package com.example.ui

import android.app.Application
import android.content.Intent
import android.hardware.usb.UsbDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.Brand
import com.example.model.LogEntry
import com.example.model.LogLevel
import com.example.model.MtkDatabase
import com.example.model.MtkDeviceInfo
import com.example.model.MtkModel
import com.example.usb.MtkUsbService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MtkUiState(
    val brands: List<Brand> = MtkDatabase.brands,
    val selectedBrand: Brand = MtkDatabase.brands.first(),
    val selectedModel: MtkModel = MtkDatabase.brands.first().models.first(),
    val isRunning: Boolean = false,
    val isUsbConnected: Boolean = false,
    val connectedDeviceName: String = "",
    val logs: List<LogEntry> = emptyList(),
    val deviceInfo: MtkDeviceInfo? = null,
    val isExplanationExpanded: Boolean = false
)

class MtkServiceViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MtkUiState())
    val uiState: StateFlow<MtkUiState> = _uiState.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val usbService = MtkUsbService(
        context = application.applicationContext,
        coroutineScope = viewModelScope,
        onLog = { levelStr, msg ->
            val level = when (levelStr.uppercase()) {
                "SYSTEM" -> LogLevel.SYSTEM
                "USB" -> LogLevel.USB
                "BROM" -> LogLevel.BROM
                "SLA" -> LogLevel.SLA
                "INFO" -> LogLevel.INFO
                "SUCCESS" -> LogLevel.SUCCESS
                "WARNING" -> LogLevel.WARNING
                "ERROR" -> LogLevel.ERROR
                else -> LogLevel.INFO
            }
            appendLog(level, msg)
        },
        onDeviceFound = { device: UsbDevice ->
            _uiState.update {
                it.copy(
                    isUsbConnected = true,
                    connectedDeviceName = "MTK Port (VID:0x${String.format("%04X", device.vendorId)} PID:0x${String.format("%04X", device.productId)})"
                )
            }
        },
        onDeviceInfoRead = { info: MtkDeviceInfo ->
            _uiState.update { it.copy(deviceInfo = info) }
        },
        onStateChanged = { running ->
            _uiState.update { it.copy(isRunning = running) }
        }
    )

    init {
        usbService.registerReceiver()
        appendLog(LogLevel.SYSTEM, "MTK Client Native Service Engine initialized.")
        appendLog(LogLevel.USB, "USB Host Interface Active [singleTask Mode: Zero-Restart Protected].")
        appendLog(LogLevel.INFO, "Instruction: Power off device, hold [Vol+] and [Vol-], plug in OTG cable.")
        usbService.scanAndConnectDevice()
    }

    fun handleUsbIntent(intent: Intent?) {
        if (intent != null) {
            usbService.handleIncomingIntent(intent)
        }
    }

    fun selectBrand(brand: Brand) {
        val firstModel = brand.models.firstOrNull() ?: MtkDatabase.brands.first().models.first()
        _uiState.update {
            it.copy(
                selectedBrand = brand,
                selectedModel = firstModel
            )
        }
        appendLog(LogLevel.SYSTEM, "Brand selected: ${brand.name}")
    }

    fun selectModel(model: MtkModel) {
        _uiState.update { it.copy(selectedModel = model) }
        appendLog(LogLevel.SYSTEM, "Target model: ${model.name} (Chipset: ${model.chipset}, HW Code: 0x${String.format("%04X", model.hwCode)})")
    }

    fun toggleExplanation() {
        _uiState.update { it.copy(isExplanationExpanded = !it.isExplanationExpanded) }
    }

    fun startService() {
        if (_uiState.value.isRunning) return
        val currentModel = _uiState.value.selectedModel
        appendLog(LogLevel.SYSTEM, "=== STARTING SERVICE FOR ${currentModel.name} ===")
        viewModelScope.launch {
            usbService.executeServiceRoutine(currentModel)
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
        appendLog(LogLevel.SYSTEM, "Logs cleared.")
    }

    private fun appendLog(level: LogLevel, message: String) {
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            message = message
        )
        _uiState.update { it.copy(logs = it.logs + entry) }
    }

    override fun onCleared() {
        super.onCleared()
        usbService.unregisterReceiver()
    }
}
