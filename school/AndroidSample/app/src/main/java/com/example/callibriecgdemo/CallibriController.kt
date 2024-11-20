package com.example.callibriecgdemo

import android.telecom.Call
import android.util.Log
import com.neurosdk2.neuro.Callibri
import com.neurosdk2.neuro.Scanner
import com.neurosdk2.neuro.Sensor
import com.neurosdk2.neuro.interfaces.CallibriElectrodeStateChanged
import com.neurosdk2.neuro.interfaces.CallibriEnvelopeDataReceived
import com.neurosdk2.neuro.interfaces.CallibriSignalDataReceived
import com.neurosdk2.neuro.types.*
import com.neurotech.callibriutils.CallibriMath
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.LinkedList
import java.util.Queue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.EmptyCoroutineContext

enum class ConnectionState {
    connection, // 0
    connected, // 1
    disconnection, // 2
    disconnected, // 3
    error // 4
}

data class CallibriInfo(val name: String, val address: String, val sensorInfo: SensorInfo)

data class CallibriAdditional(
    var callibri: Callibri,
    val needReconnect: Boolean,
    var isSignal: Boolean = false,
    var ecgMath: CallibriMath = CallibriMath(1000, 500, 30),
    val bufSize: Int = 100,
    var signalData: Queue<Double> = LinkedList()
)

@Singleton
class CallibriAdapter @Inject constructor() {

    private var _scope = CoroutineScope(EmptyCoroutineContext)

    var connectionStateChanged: ((String, ConnectionState) -> Unit)? = null
    var batteryChanged: ((String, Int) -> Unit)? = null
    var hrValue: ((String, Double) -> Unit)? = null
    var signalQuality: ((String, Boolean) -> Unit)? = null

    //<editor-fold desc="Scanner">
    private var _scanner: Scanner? = null
    private var _connectedDevices = mutableMapOf<String, CallibriAdditional>()
    private var _disconnectedDevices = mutableListOf<String>()

    var connectedDevices = _connectedDevices.keys

    suspend fun startSearchWithResult(seconds: Long, addresses: List<String>): List<CallibriInfo> {
        try {
            createScanner()
            _scanner?.start()
            delay(seconds * 1000)
            _scanner?.stop()
            val founded = _scanner?.sensors!!
            if (founded.isNotEmpty()) {
                return founded.filter { si -> addresses.isEmpty() || addresses.contains(si.address) }
                    .map { si -> CallibriInfo(name = si.name, address = si.address, sensorInfo = si) }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return emptyList()
    }

    private fun createScanner() {
        if(_scanner == null){
            _scanner = Scanner(SensorFamily.SensorLECallibri, SensorFamily.SensorLEKolibri)
            _scanner?.sensorsChanged = Scanner.ScannerCallback { scanner, sensorInfos ->
                eventSensorsChanged(scanner, sensorInfos)
            }
        }
    }

    private fun eventSensorsChanged(scanner: Scanner, sensors: MutableList<SensorInfo>): Unit {
        _scope.launch {
            Log.i("EventSensorFounded", "Founded: " + sensors.size);
            val founded = sensors.filter { _disconnectedDevices.contains(it.address) }
            for (si in founded) {
                _scanner?.stop()
                _connectedDevices[si.address]?.callibri?.connect()
                if (_connectedDevices[si.address]?.callibri?.state == SensorState.StateInRange) {
                    if(_connectedDevices[si.address]!!.isSignal){
                        executeCommand(_connectedDevices[si.address]?.callibri!!, SensorCommand.StartSignal)
                    }
                    _disconnectedDevices.remove(founded.first().address)
                }
            }
            if (_disconnectedDevices.isNotEmpty()) scanner.start()
        }
    }
    //</editor-fold>

    //<editor-fold desc="Sensor state">

    suspend fun connectTo(info: CallibriInfo, needReconnect: Boolean) {
        try {
            deviceConnectionState(info.address, ConnectionState.connection)
            val sensor = _scanner?.createSensor(info.sensorInfo) as Callibri

            if (sensor.state == SensorState.StateInRange) {
                sensor.sensorStateChanged = Sensor.SensorStateChanged { sensorState ->
                    deviceConnectionState(
                        info.address,
                        if (sensorState == SensorState.StateInRange) ConnectionState.connected else ConnectionState.disconnected
                    )
                    if (sensorState == SensorState.StateOutOfRange && _connectedDevices.containsKey(info.address) && _connectedDevices[info.address]?.needReconnect == true) {
                        _disconnectedDevices.add(info.address)
                        _scope.launch {
                            createScanner()
                            _scanner?.start()
                        }
                    }
                }
                sensor.batteryChanged = Sensor.BatteryChanged {
                    deviceBattery(info.address, it)
                }

                try {
                    sensor.samplingFrequency = SensorSamplingFrequency.FrequencyHz1000
                    sensor.signalType = CallibriSignalType.ECG
                    sensor.hardwareFilters = listOf(
                        SensorFilter.FilterBSFBwhLvl2CutoffFreq45_55Hz,
                        SensorFilter.FilterHPFBwhLvl1CutoffFreq1Hz
                    );

                    _connectedDevices[info.address] =
                        CallibriAdditional(callibri = sensor, needReconnect = needReconnect)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }

                deviceConnectionState(info.address, ConnectionState.connected)
            } else {
                deviceConnectionState(info.address, ConnectionState.error)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            deviceConnectionState(info.address, ConnectionState.error)
        }
    }

    fun disconnectFrom(info: CallibriInfo) {
        _scope.launch {
            try {
                deviceConnectionState(info.address, ConnectionState.disconnection)
                if (_disconnectedDevices.contains(info.address)) {
                    _disconnectedDevices.remove(info.address)
                    if (_disconnectedDevices.isEmpty()) _scanner?.stop()
                }
                if (_connectedDevices[info.address]?.callibri?.state == SensorState.StateInRange) {
                    val tmp = _connectedDevices[info.address]?.callibri!!
                    tmp.disconnect()
                    tmp.close()
                    _connectedDevices.remove(info.address)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun deviceConnectionState(addr: String, state: ConnectionState) {
        _scope.launch {
            if(connectionStateChanged != null){
                connectionStateChanged!!(addr, state)
            }
        }
    }

    private fun deviceBattery(addr: String, battery: Int) {
        _scope.launch {
            if(batteryChanged != null){
                batteryChanged!!(addr, battery)
            }
        }
    }

    //</editor-fold>

    //<editor-fold desc="Calculations">
    fun startCalculations(callibriAddress: String) {
        _connectedDevices[callibriAddress]?.callibri?.callibriSignalDataReceived =
            CallibriSignalDataReceived {
                try {
                    val math = _connectedDevices[callibriAddress]?.ecgMath!!
                    val bufSize = _connectedDevices[callibriAddress]?.bufSize!!
                    val buffer = _connectedDevices[callibriAddress]?.signalData!!
                    for (samples in it) {
                        for (sample in samples.samples) {
                            buffer.add(sample)
                        }
                    }
                    if (buffer.size >= bufSize) {
                        val rawData = DoubleArray(bufSize)

                        for (i in 0..<bufSize) {
                            val sample = buffer.remove()
                            rawData[i] = sample
                        }
                        math.pushData(rawData)
                        val rrDetecrted = math.rrDetected()
                        _scope.launch {
                            if(signalQuality != null){
                                signalQuality!!(callibriAddress, rrDetecrted)
                            }
                            if (rrDetecrted) {
                                if(hrValue != null){
                                    hrValue!!(callibriAddress, math.hr)
                                }
                            }
                        }
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        executeCommand(_connectedDevices[callibriAddress]?.callibri!!, SensorCommand.StartSignal)
        _connectedDevices[callibriAddress]?.isSignal = true
    }

    fun stopCalculations(callibriAddress: String) {
        _connectedDevices[callibriAddress]?.callibri?.callibriSignalDataReceived = null
        executeCommand(_connectedDevices[callibriAddress]?.callibri!!, SensorCommand.StopSignal)
        _connectedDevices[callibriAddress]?.isSignal = false
    }
    //</editor-fold>

    private fun executeCommand(sensor: Sensor, command: SensorCommand) =
        _scope.launch {
            try {
                if (sensor.isSupportedCommand(command)) {
                    sensor.execCommand(command)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
}
