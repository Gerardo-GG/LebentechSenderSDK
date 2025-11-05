package com.lb.sendersdk

import android.content.Context
import android.util.Log
import com.lb.sendersdk.data.ConnectionResult
import com.lb.sendersdk.models.ApplicationDevice
import com.lb.sendersdk.models.BluetoothMessage
import com.lb.sendersdk.models.BluetoothUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BluetoothService (
    private val context: Context,
    private val bluetoothController: BluetoothController
) {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private val _state = MutableStateFlow(BluetoothUiState())
    val state = combine(
        bluetoothController.scannedDevices,
        bluetoothController.pairedDevices,
        _state
    ) { scannedDevices, pairedDevices, state ->
        state.copy(
            scannedDevices = scannedDevices,
            pairedDevices = pairedDevices,
            messages = if(state.isConnected) state.messages else BluetoothMessage("", "","","","","","","")
        )
    }.stateIn(serviceScope, SharingStarted.WhileSubscribed(5000), _state.value)

    private var deviceConnectionJob: Job? = null

    init {
        bluetoothController.isConnected.onEach { isConnected ->
            _state.update { it.copy(isConnected = isConnected) }
        }.launchIn(serviceScope)

        bluetoothController.errors.onEach { error ->
            _state.update { it.copy(
                errorMessage = error
            ) }
        }.launchIn(serviceScope)
    }

    fun connectToDevice(device: ApplicationDevice) {
        _state.update { it.copy(isConnecting = true) }
        deviceConnectionJob = bluetoothController
            .connectToDevice(device)
            .listen()
    }

    fun disconnectFromDevice() {
        Log.d("SENDMSJ", "Desconectar:")
        _state.update { it.copy(
            isConnecting = false,
            isConnected = false
        ) }
        deviceConnectionJob?.cancel()
        bluetoothController.closeConnection()

    }

    fun sendMessage(message: String, id: String) {
        serviceScope.launch {
            val bluetoothMessage = bluetoothController.sendBluetoothMessage(message, id)

            if(bluetoothMessage != null) {
                _state.update { it.copy(
                    messages = bluetoothMessage
                ) }
            }
        }
    }

    fun getLocalDevice(): ApplicationDevice? {
        return bluetoothController.getLocalDevice()
    }

    fun startScan() {
        bluetoothController.startDiscovery()
    }

    fun stopScan() {
        bluetoothController.stopDiscovery()
    }

    private fun Flow<ConnectionResult>.listen(): Job {
        return onEach { result ->
            when(result) {
                ConnectionResult.ConnectionEstablished -> {
                    _state.update { it.copy(
                        isConnected = true,
                        isConnecting = false,
                        errorMessage = null
                    ) }
                }
                is ConnectionResult.ConnectionFinished -> {
                    disconnectFromDevice()
                }
                is ConnectionResult.TransferSucceeded -> {
                    val action = result.message.action.split("#")
                    val bateria = result.message.batteryPercentage
                    val freeSize = result.message.freeStorageGB

                    var messages: List<String> = result.message.messages
                        .removePrefix("[")
                        .removeSuffix("]")
                        .split(",")
                }
                is ConnectionResult.Error -> {
                    _state.update { it.copy(
                        isConnected = false,
                        isConnecting = false,
                        errorMessage = result.message
                    ) }
                }
            }
        }
            .catch {
                bluetoothController.closeConnection()
                _state.update { it.copy(
                    isConnected = false,
                    isConnecting = false,
                ) }
            }
            .launchIn(serviceScope)
    }
}