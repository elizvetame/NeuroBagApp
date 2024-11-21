import sys

from PyQt6.QtWidgets import QApplication, QMainWindow, QStackedWidget, QWidget
from PyQt6.uic import loadUi
from brain_bit_controller import brain_bit_controller, BrainBitInfo, ConnectionState, ResistValues, MindDataReal


class MainScreen(QMainWindow):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        loadUi("ui/MainWindow.ui", self)
        self.startResistButton.setVisible(False)
        self.stopResistButton.setVisible(False)
        self.label_2.setVisible(False)
        self.o1Value.setVisible(False)
        self.label_3.setVisible(False)
        self.o2Value.setVisible(False)
        self.label_4.setVisible(False)
        self.t3Value.setVisible(False)
        self.label_5.setVisible(False)
        self.t4Value.setVisible(False)
        self.startCalsButton.setVisible(False)
        self.stopCalcButton.setVisible(False)
        self.calibrationProgress.setVisible(False)
        self.label_6.setVisible(False)
        self.artefactLabel.setVisible(False)
        self.label1.setVisible(False)
        self.relaxLabel.setVisible(False)
        self.label2.setVisible(False)
        self.attentionLabel.setVisible(False)

        self.searchButton.clicked.connect(self.start_search)
        self.startResistButton.clicked.connect(self.start_resist)
        self.stopResistButton.clicked.connect(self.stop_resist)
        self.startCalsButton.clicked.connect(self.start_calc)
        self.stopCalcButton.clicked.connect(self.stop_calc)
        self.listWidget.itemClicked.connect(self.connect_to_device)
        self.__founded_sensors=list[BrainBitInfo]

    def start_search(self):
        self.listWidget.clear()
        self.searchButton.setText("Поиск...")
        self.searchButton.setEnabled(False)

        def on_bb_founded(sensors: list[BrainBitInfo]):
            try:
                self.__founded_sensors = sensors
                self.listWidget.addItems([sens.Name + ' (' + sens.Address + ')' for sens in sensors])
                self.searchButton.setText("Искать заново...")
                self.searchButton.setEnabled(True)
                brain_bit_controller.founded.disconnect(on_bb_founded)
            except Exception as err:
                print(err)

        brain_bit_controller.founded.connect(on_bb_founded)
        brain_bit_controller.search_with_result(5, [])

    def connect_to_device(self, item):
        item_number = self.listWidget.row(item)
        item_info=self.__founded_sensors[item_number]
        def on_device_connected(address: str, state: ConnectionState):
            item.setText(item_info.Name + ' (' + item_info.Address + '): ' + state.name)
            if address==item_info.Address and state==ConnectionState.Connected:
                self.startResistButton.setVisible(True)
                self.stopResistButton.setVisible(True)
                self.label_2.setVisible(True)
                self.o1Value.setVisible(True)
                self.label_3.setVisible(True)
                self.o2Value.setVisible(True)
                self.label_4.setVisible(True)
                self.t3Value.setVisible(True)
                self.label_5.setVisible(True)
                self.t4Value.setVisible(True)


        brain_bit_controller.connectionStateChanged.connect(on_device_connected)
        brain_bit_controller.connect_to(info=item_info, need_reconnect=True)


    def start_resist(self):
        current_bb=brain_bit_controller.connected_devices[0]
        def on_resist_received(addr, resist_states: ResistValues):
            if addr == current_bb:
                self.o1Value.setText(resist_states.O1.name)
                self.o2Value.setText(resist_states.O2.name)
                self.t3Value.setText(resist_states.T3.name)
                self.t4Value.setText(resist_states.T4.name)


        brain_bit_controller.resistValuesUpdated.connect(on_resist_received)
        brain_bit_controller.start_resist(current_bb)

    def stop_resist(self):
        try:
            brain_bit_controller.resistValuesUpdated.disconnect()
        except Exception as err:
            print(err)
        brain_bit_controller.stop_resist(brain_bit_controller.connected_devices[0])

        self.startCalsButton.setVisible(True)
        self.stopCalcButton.setVisible(True)
        self.calibrationProgress.setVisible(True)
        self.label_6.setVisible(True)
        self.artefactLabel.setVisible(True)
        self.label1.setVisible(True)
        self.relaxLabel.setVisible(True)
        self.label2.setVisible(True)
        self.attentionLabel.setVisible(True)


    def start_calc(self):
        current_bb = brain_bit_controller.connected_devices[0]
        def is_artefacted(address: str, artefacted: bool):
            if address == current_bb:
                self.artefactLabel.setText("Есть" if artefacted else "Нет")

        def calibration_progress_changed(address: str, progress: int):
            if address == current_bb:
                self.calibrationProgress.setValue(progress)

        def mind_data_changed(address: str, mind_data: MindDataReal):
            if address == current_bb:
                self.relaxLabel.setText("{}".format(mind_data.relaxation))
                self.attentionLabel.setText("{}".format(mind_data.attention))

        brain_bit_controller.isArtefacted.connect(is_artefacted)
        brain_bit_controller.calibrationProcessChanged.connect(calibration_progress_changed)
        brain_bit_controller.mindDataUpdated.connect(mind_data_changed)
        brain_bit_controller.start_calculations(current_bb)

    def stop_calc(self):
        try:
            brain_bit_controller.isArtefacted.disconnect()
            brain_bit_controller.calibrationProcessChanged.disconnect()
            brain_bit_controller.mindDataUpdated.disconnect()
        except Exception as err:
            print(err)
        brain_bit_controller.stop_calculations(brain_bit_controller.connected_devices[0])

app = QApplication(sys.argv)
window = MainScreen()
window.show()
app.exec()
brain_bit_controller.stop_all()
sys.exit()