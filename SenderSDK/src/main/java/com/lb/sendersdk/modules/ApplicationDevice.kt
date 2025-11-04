package com.lb.sendersdk.modules

typealias ApplicationDevice = BluetoothDevice

data class BluetoothDevice(
    val name: String?,
    val address: String
)

