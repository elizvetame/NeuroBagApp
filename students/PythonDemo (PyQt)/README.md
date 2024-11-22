# Python + BrainBit + Emotions using PyQt

## Настройка проекта

Перед первым запуском необходимо установить зависимые бибилотеки:

```
pip install pyneurosdk2
pip install pyem-st-artifacts
pip install PyQt6
```

## Структура проекта

Проект состоит из трех файлов:
1. файл интерфейса [Mainwindow.ui](https://gitlab.com/neurosdk2/cybergarden2024/-/blob/main/students/PythonDemo%20(PyQt)/ui/MainWindow.ui?ref_type=heads). В нем ничего интересного
2. Точка входа в приложение [main.py](https://gitlab.com/neurosdk2/cybergarden2024/-/blob/main/students/PythonDemo%20(PyQt)/main.py?ref_type=heads). В нем же находится описание интерфейса и вывод рассчитанных дначений в интерфейс
3. самый важный файл [brain_bit_controller.py](https://gitlab.com/neurosdk2/cybergarden2024/-/blob/main/students/PythonDemo%20(PyQt)/brain_bit_controller.py?ref_type=heads)

**BrainBitController** - это основной класс, на который нужно обратить внимание. В нем происходит все взаимодействие с девайсом BrainBit, а так же получение эмоциональных состояний из дополнитеной библиотеки. Каждый метод ожидает одним из аргументов мак-адрес устройства. Это означает, что объект **BrainBitController** хранит в себе все подключенные подльзователем устройства и различает их по мак-адресу. Все оповещения помимо основной информации так же отправляют мак-адрес устройства, от которого пришло это оповещение. Этот класс должен быть синглтоном, поэтому сразу после имплементации заведена переменная [brain_bit_controller](https://gitlab.com/neurosdk2/cybergarden2024/-/blob/main/students/PythonDemo%20(PyQt)/brain_bit_controller.py?ref_type=heads#L283), с помощью которой нужно обращаться к контроллеру.

#### Поиск устройства

Поиск представлен методом **search_with_result** с аргументами:

1. время поиска. Указывается в секундах. Тип данных **int**
2. список мак-адресов устройств для поиска. Если передать пустой список - найдутся все девайся типа BrainBit. Тим данных - **List[str]**.

Эта функция асинхронна. Чтобы получить список найденных девайсов нужно подключиться к нужному сигналу.

Как использовать:

1. подключиться к сигналу:
    ```python
    def on_bb_founded(sensors: list[BrainBitInfo]):
        pass

    brain_bit_controller.founded.connect(on_bb_founded)
    ``` 

2. запустить поиск устройств:
    ```python
    brain_bit_controller.search_with_result(5, [])
    ``` 

В примере кода показан поиск любого девайса в течении 5 сек.

Список найденных девайсов приходит в списке типа **list[BrainBitInfo]**. **BrainBitInfo** содержит два значимых поля:
 1. Имя девайса. Тип данных **str**
 2. Мак-адрес девайса. Тип данных **str**


#### Подключение к устройству

Для подключения используется метод **connect_to**. Метод асинхронный, поэтому о статусе подключения можно узнать от соответствующего сигнала. Метод принимает следующие аргументы:

 1. Информацию об устройстве. Тип **BrainBitInfo**
 2. Нужно ли устройство переподключать при отключении. Тип **bool**

Если вторым аргументом передано true:
 1. при незапланированном отключении девайса он подключится обратно
 2. если во время отключения было запущено сопротивление - его нужно будет включать отдельно
 3. если во время отключения был запущен сигнал - он включится самостоятельно, никаких действий предпринимать не нужно

Состояние уже подключенного девайса можно так же получить с помощью сигнала **connectionStateChanged**.

1. подключиться к сигналу:
    ```python
    def on_device_connected(address: str, state: ConnectionState):
        if address==selected_BB.Address and state==ConnectionState.Connected:
            pass

    brain_bit_controller.connectionStateChanged.connect(on_device_connected)
    ``` 

2. подключиться к девайсу:

    ```python
    selected_BB = # получить BrainBitInfo из списка найденных устройств как показано выше
    brain_bit_controller.connect_to(info=selected_BB, need_reconnect=True)
    ```

#### Проверка качества наложения

Для проверки качества наложения используются значения сопротивления. Чтобы получить эти значения нужно:
 1. подключиться к сигналу. Данные начнут приходить только после запуска сопротивлений.

    ```python
    def on_resist_received(addr: str, resist_states: ResistValues):
        if address==selected_BB.Address:
            pass

    brain_bit_controller.resistValuesUpdated.connect(on_resist_received)
    ```

 2. запустить проверку сопротивлений

    ```python
    selected_BB_address = # сохраненный адрес нужного девайса
    brain_bit_controller.start_resist(selected_BB_address)
    ```

 3. как только достигнуто желаемое качество остановить сьем данных и отключиться от сигнала

    ```python
    selected_BB_address = # сохраненный адрес нужного девайса
    brain_bit_controller.resistValuesUpdated.disconnect()
    brain_bit_controller.stop_resist(selected_BB_address)
    ```

Структура **ResistValues** содержит 4 поля для каждого канала - O1, O2, T3, T4. Состояние сопротивления представлено перечислением. Сопротивление может быть плохим или нормальным:
 
 1. Bad
 2. Normal

#### Получение эмоциональных соостояний

Эмоциональные состояния представлены двумя параметрами - расслаблением и вниманием. Каждый параметр находится в диапазоне 0..100. Параметры представлены в процентах. Только один параметр может быть отличен от 0.

Так же нужно учитывать следующее:
1. для получения данных нужно **откалиброваться**. Калибровка начинается одновременно с началом вычислений. О ее прогрессе можно узнать с помощью соответствующего оповещения. Оповещение содержит мак-адрес калибрующегося девайса, а так же процент прогресса калибровки.
2. отслеживать **качество сигнала**. Качество сигнала можно получить с помощью сигнала **isArtefacted**. Если в сигнале присутствуют артефакты калибровка не будет проходить, а данные эмоциональных состояний не будут меняться.
3. Получить эмоциональные состояния можно только **после** калибровки

Чтобы получить эмоциональные состояния нужно:

1. подписаться на сигналы

    ```python
    selected_BB_address = # сохраненный адрес нужного девайса
    def is_artefacted(address: str, artefacted: bool):
        if address == selected_BB_address:
            pass

    def calibration_progress_changed(address: str, progress: int):
        if address == selected_BB_address:
            pass

    def mind_data_changed(address: str, mind_data: MindDataReal):
        if address == selected_BB_address:
            pass

    brain_bit_controller.isArtefacted.connect(is_artefacted)
    brain_bit_controller.calibrationProcessChanged.connect(calibration_progress_changed)
    brain_bit_controller.mindDataUpdated.connect(mind_data_changed)
    ```

2. запустить вычисления

    ```python
    # здесь происходит подпись на сигналы

    selected_BB_address = # сохраненный адрес нужного девайса
    brain_bit_controller.start_calculations(selected_BB_address) # начало вычислений
    ```
3. по завершению работы отписаться от ивентов и остановить вычисления

    ```python
    # отключаем сигналы
    brain_bit_controller.isArtefacted.disconnect()
    brain_bit_controller.calibrationProcessChanged.disconnect()
    brain_bit_controller.mindDataUpdated.disconnect()

    brain_bit_controller.stop_calculations(selected_BB_address)
    ```

Структура **MindDataReal** состоит из следующих полей:
 1. Attention. Внимание. Тип **float**
 2. Relaxation. Расслабление. Тип **float**


