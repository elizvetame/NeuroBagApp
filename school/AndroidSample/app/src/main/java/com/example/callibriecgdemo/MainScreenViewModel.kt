package com.example.callibriecgdemo

import android.telecom.Call
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
class MainScreenViewModel @Inject constructor (private val adapter: CallibriAdapter) : ViewModel() {
    private val _scope = CoroutineScope(EmptyCoroutineContext)

    private val _foundDevices = MutableStateFlow<List<CallibriInfo>>(emptyList())
    val foundDevices = _foundDevices.asStateFlow()

    private val _allDevicesState = MutableStateFlow<List<String>>(emptyList())
    val allDevices = _allDevicesState.asStateFlow()

    private val _searchState = MutableStateFlow<SearchState>(SearchState.notStarted)
    val searchState = _searchState.asStateFlow()

    private var _connectionState =
        MutableStateFlow(ConnectionState.disconnected)
    var connectionState = _connectionState.asStateFlow()

    val _signalQuality = MutableStateFlow(false)
    val signalQuality = _signalQuality.asStateFlow()

    val _hrState = MutableStateFlow(0.0)
    val hrState = _hrState.asStateFlow()

    var _selectedDevice = ""

    init {
        adapter.connectionStateChanged = {addr, state ->
            if(addr == _selectedDevice)
                _scope.launch {
                    _connectionState.emit(state)
                }
        }
        adapter.signalQuality = {addr, hasRRPeacks ->
            if(addr == _selectedDevice)
                _scope.launch {
                    _signalQuality.emit(hasRRPeacks)
                }
        }
        adapter.hrValue = {addr, hr ->
            if(addr == _selectedDevice)
                _scope.launch {
                    _hrState.emit(hr)
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

    fun connect(info: CallibriInfo){
        _scope.launch {
            adapter.connectTo(info, true)
            _allDevicesState.emit(adapter.connectedDevices.toList())
        }
    }

    fun startCalculations(){
        adapter.startCalculations(_selectedDevice)
    }

    fun stopCalculations(){
        adapter.stopCalculations(_selectedDevice)
    }
}