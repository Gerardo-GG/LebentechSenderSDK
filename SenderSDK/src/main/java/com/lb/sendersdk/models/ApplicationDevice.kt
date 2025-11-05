package com.lb.sendersdk.models

typealias ApplicationDevice = BluetoothDevice

data class BluetoothDevice(
    val name: String?,
    val address: String
)

