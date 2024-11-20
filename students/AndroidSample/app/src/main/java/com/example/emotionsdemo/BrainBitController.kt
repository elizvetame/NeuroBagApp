package com.example.emotionsdemo

import com.neurosdk2.neuro.BrainBit
import com.neurosdk2.neuro.Scanner
import com.neurosdk2.neuro.Sensor
import com.neurosdk2.neuro.interfaces.BrainBitResistDataReceived
import com.neurosdk2.neuro.interfaces.BrainBitSignalDataReceived
import com.neurosdk2.neuro.types.SensorCommand
import com.neurosdk2.neuro.types.SensorFamily
import com.neurosdk2.neuro.types.SensorInfo
import com.neurosdk2.neuro.types.SensorState
import com.neurotech.emstartifcats.ArtifactDetectSetting
import com.neurotech.emstartifcats.EmotionalMath
import com.neurotech.emstartifcats.MathLibSetting
import com.neurotech.emstartifcats.MentalAndSpectralSetting
import com.neurotech.emstartifcats.RawChannels
import com.neurotech.emstartifcats.ShortArtifactDetectSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.EmptyCoroutineContext

enum class ResistState {
    normal, bad
}

data class ResistValues(
    var o1: ResistState,
    var o2: ResistState,
    var t3: ResistState,
    var t4: ResistState
)

data class MindDataReal(var attention: Double, var relaxation: Double)

enum class ConnectionState {
    connection, // 0
    connected, // 1
    disconnection, // 2
    disconnected, // 3
    error // 4
}

data class BrainBitInfo(val name: String, val address: String, val sensorInfo: SensorInfo)

data class BrainBitAdditional(
    var bb: BrainBit,
    val needReconnect: Boolean,
    var isSignal: Boolean = false,
    var isCalibrated: Boolean = false,
    var emotionalMath: EmotionalMath = EmotionalMath(
        MathLibSetting(
            /* samplingRate = */        250,
            /* processWinFreq = */      25,
            /* fftWindow = */           500,
            /* nFirstSecSkipped = */    6,
            /* bipolarMode = */         true,
            /* channelsNumber = */      4,
            /* channelForAnalysis = */  0
        ), ArtifactDetectSetting(
            /* artBord = */                 110,
            /* allowedPercentArtpoints = */ 70,
            /* rawBetapLimit = */           800_000,
            /* totalPowBorder = */          30000000,
            /* globalArtwinSec = */         4,
            /* spectArtByTotalp = */        false,
            /* hanningWinSpectrum = */      false,
            /* hammingWinSpectrum = */      true,
            /* numWinsForQualityAvg = */    100
        ), ShortArtifactDetectSetting(
            /* amplArtDetectWinSize = */    200,
            /* amplArtZerodArea = */        200,
            /* amplArtExtremumBorder = */   25
        ), MentalAndSpectralSetting(
            /* nSecForInstantEstimation = */    2,
            /* nSecForAveraging = */            2
        )
    ),
)

@Singleton
class BrainBitAdapter @Inject constructor() {

    private var _scope = CoroutineScope(EmptyCoroutineContext)

    var connectionStateChanged: ((String, ConnectionState) -> Unit)? = null
    var batteryChanged: ((String, Int) -> Unit)? = null

    var resistUpdated: ((String, ResistValues) -> Unit)? = null
    var artefactsReceived: ((String, Boolean) -> Unit)? = null
    var mindStateUpdated: ((String, MindDataReal) -> Unit)? = null
    var calibrationProgress: ((String, Int) -> Unit)? = null

    //<editor-fold desc="Scanner">
    private var _scanner: Scanner? = null
    private var _connectedDevices = mutableMapOf<String, BrainBitAdditional>()
    private var _disconnectedDevices = mutableListOf<String>()

    var connectedDevices = _connectedDevices.keys

    suspend fun startSearchWithResult(seconds: Long, addresses: List<String>): List<BrainBitInfo> {
        try {
            createScanner()
            _scanner?.start()
            delay(seconds * 1000)
            _scanner?.stop()
            val founded = _scanner?.sensors!!
            if (founded.isNotEmpty()) {
                return founded.filter { si -> addresses.isEmpty() || addresses.contains(si.address) }
                    .map { si ->
                        BrainBitInfo(
                            name = si.name,
                            address = si.address,
                            sensorInfo = si
                        )
                    }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return emptyList()
    }

    private fun createScanner() {
        if (_scanner == null) {
            _scanner = Scanner(SensorFamily.SensorLEBrainBit)
            _scanner?.sensorsChanged = Scanner.ScannerCallback { scanner, sensorInfos ->
                eventSensorsChanged(scanner, sensorInfos)
            }
        }
    }

    private fun eventSensorsChanged(scanner: Scanner, sensors: MutableList<SensorInfo>): Unit {
        _scope.launch {
            val founded = sensors.filter { _disconnectedDevices.contains(it.address) }
            for (si in founded) {
                _scanner?.stop()
                _connectedDevices[si.address]?.bb?.connect()
                if (_connectedDevices[si.address]?.bb?.state == SensorState.StateInRange) {
                    if (_connectedDevices[si.address]!!.isSignal) {
                        executeCommand(
                            _connectedDevices[si.address]?.bb!!,
                            SensorCommand.StartSignal
                        )
                    }
                    _disconnectedDevices.remove(founded.first().address)
                }
            }
            if (_disconnectedDevices.isNotEmpty()) scanner.start()
        }
    }
    //</editor-fold>

    //<editor-fold desc="Sensor state">
    suspend fun connectTo(info: BrainBitInfo, needReconnect: Boolean) {
        try {
            deviceConnectionState(info.address, ConnectionState.connection)
            val sensor = _scanner?.createSensor(info.sensorInfo) as BrainBit

            if (sensor.state == SensorState.StateInRange) {
                sensor.sensorStateChanged = Sensor.SensorStateChanged { sensorState ->
                    deviceConnectionState(
                        info.address,
                        if (sensorState == SensorState.StateInRange) ConnectionState.connected else ConnectionState.disconnected
                    )
                    if (sensorState == SensorState.StateOutOfRange && _connectedDevices.containsKey(
                            info.address
                        ) && _connectedDevices[info.address]?.needReconnect == true
                    ) {
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

                _connectedDevices[info.address] =
                    BrainBitAdditional(bb = sensor, needReconnect = needReconnect)

                deviceConnectionState(info.address, ConnectionState.connected)
            } else {
                deviceConnectionState(info.address, ConnectionState.error)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            deviceConnectionState(info.address, ConnectionState.error)
        }
    }

    fun disconnectFrom(info: BrainBitInfo) {
        _scope.launch {
            try {
                deviceConnectionState(info.address, ConnectionState.disconnection)
                if (_disconnectedDevices.contains(info.address)) {
                    _disconnectedDevices.remove(info.address)
                    if (_disconnectedDevices.isEmpty()) _scanner?.stop()
                }
                if (_connectedDevices[info.address]?.bb?.state == SensorState.StateInRange) {
                    val tmp = _connectedDevices[info.address]?.bb!!
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

    //<editor-fold desc="Resist">

    fun startResist(address: String) {
        _connectedDevices[address]?.bb?.brainBitResistDataReceived = BrainBitResistDataReceived {
            _scope.launch {
                if(resistUpdated != null){
                    resistUpdated!!(address, ResistValues(
                        o1 = if (it.o1 > 2500000) ResistState.bad else ResistState.normal,
                        o2 = if (it.o2 > 2500000) ResistState.bad else ResistState.normal,
                        t3 = if (it.t3 > 2500000) ResistState.bad else ResistState.normal,
                        t4 = if (it.t4 > 2500000) ResistState.bad else ResistState.normal
                    ))
                }
            }
        }
        executeCommand(_connectedDevices[address]?.bb!!, SensorCommand.StartResist)
    }

    fun stopResist(address: String) {
        _connectedDevices[address]?.bb?.brainBitResistDataReceived = null
        executeCommand(_connectedDevices[address]?.bb!!, SensorCommand.StopResist)
    }

    //</editor-fold>

    //<editor-fold desc="Calculations">
    fun startCalculations(address: String) {
        _connectedDevices[address]?.bb?.brainBitSignalDataReceived =
            BrainBitSignalDataReceived {
                try {
                    val brainMath = _connectedDevices[address]?.emotionalMath!!
                    brainMath.pushData(Array(it.size) { i ->
                        RawChannels(it[i].t3 - it[i].o1, it[i].t4 - it[i].o2)
                    })
                    brainMath.processDataArr()

                    _scope.launch {
                        if(artefactsReceived != null){
                            artefactsReceived!!(address,
                                brainMath.isBothSidesArtifacted)
                        }
                    }
                    if (_connectedDevices[address]?.isCalibrated!!) {
                        if (brainMath.calibrationFinished()) {
                            _scope.launch {
                                if(calibrationProgress != null){
                                    calibrationProgress!!(address, 100)
                                }
                            }
                            _connectedDevices[address]?.isCalibrated = false
                        } else {
                            val progress = brainMath.callibrationPercents
                            _scope.launch {
                                if(calibrationProgress != null){
                                    calibrationProgress!!(address, progress)
                                }
                            }
                        }
                    } else {
                        val mindData = brainMath.readMentalDataArr()
                        if (mindData != null && mindData.isNotEmpty()) {
                            val lastMindData = mindData.last()
                            _scope.launch {
                                if(mindStateUpdated != null){
                                    mindStateUpdated!!(address,
                                        MindDataReal(
                                            relaxation = lastMindData.relRelaxation,
                                            attention = lastMindData.relAttention
                                        ))
                                }
                            }
                        }
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        executeCommand(_connectedDevices[address]?.bb!!, SensorCommand.StartSignal)
        _connectedDevices[address]?.isSignal = true
    }

    fun stopCalculations(address: String) {
        _connectedDevices[address]?.bb?.brainBitSignalDataReceived = null
        executeCommand(_connectedDevices[address]?.bb!!, SensorCommand.StopSignal)
        _connectedDevices[address]?.isSignal = false
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