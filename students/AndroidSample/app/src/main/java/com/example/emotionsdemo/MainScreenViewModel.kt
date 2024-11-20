package com.example.emotionsdemo

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.EmptyCoroutineContext


enum class SearchState{
    notStarted, searching, finished
}

@HiltViewModel
class MainScreenViewModel @Inject constructor (private val adapter: BrainBitAdapter) : ViewModel() {
    private val _scope = CoroutineScope(EmptyCoroutineContext)

    private val _foundDevices = MutableStateFlow<List<BrainBitInfo>>(emptyList())
    val foundDevices = _foundDevices.asStateFlow()

    private val _allDevicesState = MutableStateFlow<List<String>>(emptyList())
    val allDevices = _allDevicesState.asStateFlow()

    private val _searchState = MutableStateFlow<SearchState>(SearchState.notStarted)
    val searchState = _searchState.asStateFlow()

    private var _connectionState =
        MutableStateFlow(ConnectionState.disconnected)
    var connectionState = _connectionState.asStateFlow()

    private var _batteryState = MutableStateFlow(0)
    var batteryState = _batteryState.asStateFlow()

    private var _resistUpdated = MutableStateFlow(
        ResistValues(ResistState.bad, ResistState.bad, ResistState.bad, ResistState.bad)
    )
    var resists = _resistUpdated.asStateFlow()

    private var _mindState = MutableStateFlow(MindDataReal(0.0, 0.0))
    var mindData = _mindState.asStateFlow()

    private var _artefactsState = MutableStateFlow(false)
    var artefacts = _artefactsState.asStateFlow()

    private var _calibrationProgress = MutableStateFlow(0)
    var calibrationProgress = _calibrationProgress.asStateFlow()

    var _selectedDevice = ""

    init {
        adapter.connectionStateChanged = {addr, state ->
            if(addr == _selectedDevice)
                _scope.launch {
                    _connectionState.emit(state)
                }
        }
        adapter.resistUpdated = {addr, values ->
            if(addr == _selectedDevice){
                _scope.launch {
                    _resistUpdated.emit(values)
                }
            }
        }
        adapter.calibrationProgress = {addr, p ->
            if(addr == _selectedDevice){
                _scope.launch {
                    _calibrationProgress.emit(p)
                }
            }
        }
        adapter.artefactsReceived = {addr, state ->
            if(addr == _selectedDevice){
                _scope.launch {
                    _artefactsState.emit(state)
                }
            }
        }
        adapter.mindStateUpdated = {addr, data ->
            if(addr == _selectedDevice){
                _scope.launch {
                    _mindState.emit(data)
                }
            }
        }
    }

    fun startSearch(){
        _scope.launch {
            _searchState.emit(SearchState.searching)
            val founded = adapter.startSearchWithResult(10, emptyList())
            _foundDevices.emit(founded)
            _searchState.emit(SearchState.finished)
        }
    }

    fun connect(info: BrainBitInfo){
        _selectedDevice = info.address
        _scope.launch {
            adapter.connectTo(info, true)
            _allDevicesState.emit(adapter.connectedDevices.toList())
        }
    }

    fun startResist(){
        adapter.startResist(_selectedDevice)
    }

    fun stopResist(){
        adapter.stopResist(_selectedDevice)
    }

    fun startCalculations(){
        adapter.startCalculations(_selectedDevice)
    }

    fun stopCalculations(){
        adapter.stopCalculations(_selectedDevice)
    }
}