package com.lb.sendersdk.models

data class BluetoothMessage(
    val id: String,
    val action: String,
    val userId : String,

    // Device INFO
    val isStarted: String,
    val isFinished: String,
    val messages: String,

    val batteryPercentage :String,
    val freeStorageGB : String,
)