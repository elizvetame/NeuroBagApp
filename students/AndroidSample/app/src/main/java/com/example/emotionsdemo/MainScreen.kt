package com.example.emotionsdemo

import android.widget.ProgressBar
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel


@Composable
fun MainScreen(vm: MainScreenViewModel = hiltViewModel(), modifier: Modifier = Modifier) {
    val foundedSensors = vm.foundDevices.collectAsState()
    val connectionState = vm.connectionState.collectAsState()
    val resists = vm.resists.collectAsState()
    val calibrationProgress = vm.calibrationProgress.collectAsState()
    val mindData = vm.mindData.collectAsState()
    val artefacts = vm.artefacts.collectAsState()
    val searchState = vm.searchState.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (searchState.value) {
            SearchState.notStarted ->
                Button(onClick = { vm.startSearch() }) {
                    Text(text = "Поиск")
                }

            SearchState.searching ->
                Button(
                    onClick = { },
                    enabled = false
                ) {
                    Text(text = "Поиск...")
                }

            SearchState.finished ->
                Button(onClick = { vm.startSearch() }) {
                    Text(text = "Искать заново")
                }
        }

        LazyColumn(Modifier.height(Dp(100f))) {
            items(foundedSensors.value, key = { it.address }) {
                TextButton(onClick = {
                    vm.connect(it)
                }) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(it.name + " (" + it.address + ")")
                        Spacer(Modifier.fillMaxHeight())
                        Text(connectionState.value.name)
                    }
                }
            }
        }
        Row {
            Button(onClick = { vm.startResist() }) {
                Text(text = "Запустить сопротивления")
            }
            Button(onClick = { vm.stopResist() }) {
                Text(text = "Остановить сопротивления")
            }
        }

        Column {
            Row {
                Text(text = "O1: ")
                Text(text = resists.value.o1.name)
            }
            Row {
                Text(text = "O2: ")
                Text(text = resists.value.o2.name)
            }
            Row {
                Text(text = "T3: ")
                Text(text = resists.value.t3.name)
            }
            Row {
                Text(text = "T4: ")
                Text(text = resists.value.t4.name)
            }
        }

        Row {
            Button(onClick = { vm.startCalculations() }) {
                Text(text = "Начать вычисления")
            }
            Button(onClick = { vm.stopCalculations() }) {
                Text(text = "Завершить вычисления")
            }
        }
        Column {
            Row {
                Text(text = "Качество сигнала: ")
                Text(text = if (artefacts.value) "Плохой сигнал" else "Хороший сигнал")
            }
            LinearProgressIndicator(
                progress = { calibrationProgress.value.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Text(text = "Расслабление: ")
                Text(text = "" + mindData.value.relaxation)
            }
            Row {
                Text(text = "Внимание: ")
                Text(text = "" + mindData.value.attention)
            }
        }

    }
}