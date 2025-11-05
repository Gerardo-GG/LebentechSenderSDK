package com.lb.sendersdk.extensions

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.lb.sendersdk.models.ApplicationDevice
import com.lb.sendersdk.models.BluetoothMessage

fun String.toBluetoothMessage(): BluetoothMessage {
    val messages = this.split("#")
    return BluetoothMessage(
        id = messages[0],
        action = messages[1],
        userId = messages[2],
        isStarted = messages[3],
        isFinished = messages[4],
        messages = messages[5],
        batteryPercentage = messages[6],
        freeStorageGB = messages[7]
    )
}

@SuppressLint("MissingPermission")
fun BluetoothDevice.toApplicationDevice(): ApplicationDevice {
    return ApplicationDevice(
        name = this.name,
        address = this.address
    )
}

fun BluetoothMessage.toByteArray(): ByteArray {
    return "$id#$action#$userId#$isStarted#$isFinished#$messages#$batteryPercentage#$freeStorageGB#".encodeToByteArray()
}
