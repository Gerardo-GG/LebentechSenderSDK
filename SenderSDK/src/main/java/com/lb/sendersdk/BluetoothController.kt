package com.lb.sendersdk

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import com.lb.sendersdk.data.BluetoothDataTransferService
import com.lb.sendersdk.data.ConnectionResult
import com.lb.sendersdk.data.receivers.BluetoothStateReceiver
import com.lb.sendersdk.data.receivers.FoundDeviceReceiver
import com.lb.sendersdk.extensions.toApplicationDevice
import com.lb.sendersdk.extensions.toByteArray
import com.lb.sendersdk.models.ApplicationDevice
import com.lb.sendersdk.models.BluetoothMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

@SuppressLint("MissingPermission")
class BluetoothController @Inject constructor(
    private val context: Context
) {

    private val bluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }

    private val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean>
        get() = _isConnected.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String>
        get() = _errors.asSharedFlow()

    private val _scannedDevices = MutableStateFlow<List<ApplicationDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ApplicationDevice>>
        get() = _scannedDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<ApplicationDevice>>(emptyList())
    val pairedDevices: StateFlow<List<ApplicationDevice>>
        get() = _pairedDevices.asStateFlow()


    private val foundDeviceReceiver = FoundDeviceReceiver { device ->
        _scannedDevices.update { devices ->
            val newDevice = device.toApplicationDevice()
            if(newDevice in devices) devices else devices + newDevice
        }
    }

    private val bluetoothStateReceiver = BluetoothStateReceiver { isConnected, bluetoothDevice ->
        if (bluetoothAdapter?.bondedDevices?.contains(bluetoothDevice) == true) {
            _isConnected.update { isConnected }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                _errors.emit("No se pudo conectar al dispositivo.")
            }
        }
    }

    private var currentClientSocket: BluetoothSocket? = null
    private var dataTransferService: BluetoothDataTransferService? = null
    private var currentServerSocket: BluetoothServerSocket? = null

    init {
        context.registerReceiver(
            bluetoothStateReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
        )
    }

    fun startDiscovery() {
        if(!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }

        context.registerReceiver(
            foundDeviceReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND)
        )

        updatePairedDevices()

        bluetoothAdapter?.startDiscovery()
    }

    fun stopDiscovery() {
        if(!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }

        bluetoothAdapter?.cancelDiscovery()
    }

    fun connectToDevice(device: ApplicationDevice): Flow<ConnectionResult> {
        return flow {
            if(!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                throw SecurityException("No BLUETOOTH_CONNECT permission")
            }

            currentClientSocket = bluetoothAdapter
                ?.getRemoteDevice(device.address)
                ?.createRfcommSocketToServiceRecord(
                    UUID.fromString(SERVICE_UUID)
                )

            if ( currentClientSocket != null ) {
                try {
                    currentClientSocket!!.connect()
                    emit(ConnectionResult.ConnectionEstablished)

                    BluetoothDataTransferService(currentClientSocket!!).also {
                        dataTransferService = it
                        emitAll(
                            it.listenForIncomingMessages().map { message ->
                                ConnectionResult.TransferSucceeded(message)
                            }
                        )
                    }
                } catch (e: IOException) {
                    currentClientSocket!!.close()
                    currentClientSocket = null
                    emit(ConnectionResult.Error("La conexión fue interrumpida."))
                }
            } else {
                emit(ConnectionResult.Error("La conexión fue interrumpida."))
            }
        }.onCompletion {
            closeConnection()
        }.flowOn(Dispatchers.IO)
    }

    @SuppressLint("HardwareIds")
    fun getLocalDevice(): ApplicationDevice {
        if(!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            throw SecurityException("No BLUETOOTH_CONNECT permission")
        }

        return ApplicationDevice(
            name = bluetoothAdapter?.name ?: "Device",
            address = bluetoothAdapter?.address ?: ""
        )
    }

    suspend fun sendBluetoothMessage(message: String, id: String): BluetoothMessage? {
        if(!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            throw SecurityException("No BLUETOOTH_CONNECT permission")
        }

        if(dataTransferService == null) {
            return null
        }

        val bluetoothMessage = BluetoothMessage(
            id = id,
            action = message,
            userId = "",
            "",
            "",
            "",
            "",
            ""
        )

        dataTransferService?.sendMessage(bluetoothMessage.toByteArray())

        return bluetoothMessage
    }

    fun closeConnection() {
        try {
            currentClientSocket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothController", "Error al cerrar socket: ${e.message}")
        } finally {
            currentClientSocket = null
            dataTransferService = null
        }
    }

    fun release() {
        context.unregisterReceiver(foundDeviceReceiver)
        context.unregisterReceiver(bluetoothStateReceiver)
        closeConnection()
    }

    private fun updatePairedDevices() {
        if(!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }

        bluetoothAdapter
            ?.bondedDevices
            ?.map { it.toApplicationDevice() }
            ?.also { devices ->
                _pairedDevices.update { devices }
            }
    }

    private fun hasPermission(permission: String): Boolean = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val SERVICE_UUID = "ee355d8f-3148-4677-944f-0b52f3516979"
    }
}