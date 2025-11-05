package com.lb.sendersdk.data

import com.lb.sendersdk.models.BluetoothMessage

sealed interface ConnectionResult {
    data object ConnectionEstablished: ConnectionResult
    data object ConnectionFinished: ConnectionResult
    data class TransferSucceeded(val message: BluetoothMessage): ConnectionResult
    data class Error(val message: String): ConnectionResult
}