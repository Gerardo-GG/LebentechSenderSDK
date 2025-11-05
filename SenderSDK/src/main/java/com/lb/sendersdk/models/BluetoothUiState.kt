package com.lb.sendersdk.models


data class BluetoothUiState(
    val scannedDevices: List<ApplicationDevice> = emptyList(),
    val pairedDevices: List<ApplicationDevice> = emptyList(),
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
    val messages: BluetoothMessage = BluetoothMessage("", "","","","","","","")
)
