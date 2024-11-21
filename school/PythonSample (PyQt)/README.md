# Python + Callibri + ECG using PyQt

## Настройка проекта

Перед первым запуском необходимо установить зависимые бибилотеки:

```
pip install pyneurosdk2
pip install pycallibri-ecg
pip install PyQt6
```

## Структура проекта

Проект состоит из трех файлов:
1. файл интерфейса [Mainwindow.ui](https://gitlab.com/neurosdk2/cybergarden2024/-/tree/main/school/PythonSample%20(PyQt)/ui). В нем ничего интересного
2. Точка входа в приложение [main.py](https://gitlab.com/neurosdk2/cybergarden2024/-/blob/main/school/PythonSample%20(PyQt)/main.py). В нем же находится описание интерфейса и вывод рассчитанных дначений в интерфейс
3. самый важный файл [callibri_controller.py](https://gitlab.com/neurosdk2/cybergarden2024/-/blob/main/school/PythonSample%20(PyQt)/callibri_controller.py)

**CallibriController** - это основной класс, на который нужно обратить внимание. В нем происходит все взаимодействие с девайсом Callibri, а так же получение ЧСС из дополнительной библиотеки. Каждый метод ожидает одним из аргументов мак-адрес устройства. Это означает, что объект **CallibriController** хранит в себе все подключенные пользователем устройства и различает их по мак-адресу. Все оповещения помимо основной информации так же отправляют мак-адрес устройства, от которого пришло это оповещение. Этот класс должен быть синглтоном, поэтому сразу после имплементации заведена переменная [callibri_controller](https://gitlab.com/neurosdk2/cybergarden2024/-/blob/main/school/PythonSample%20(PyQt)/callibri_controller.py#L215), с помощью которой нужно обращаться к контроллеру.

#### Поиск устройства

Поиск представлен методом **search_with_result** с аргументами:

1. время поиска. Указывается в секундах. Тип данных **int**
2. список мак-адресов устройств для поиска. Если передать пустой список - найдутся все девайся типа Callibri. Тим данных - **List[str]**.

Эта функция асинхронна. Чтобы получить список найденных девайсов нужно подключиться к нужному сигналу.

Как использовать:

1. подключиться к сигналу:
    ```python
    def on_callibri_founded(sensors: list[CallibriInfo]):
        pass

    callibri_controller.founded.connect(on_callibri_founded)
    ``` 

2. запустить поиск устройств:
    ```python
    callibri_controller.search_with_result(5, [])
    ``` 

В примере кода показан поиск любого девайса в течении 5 сек.

Список найденных девайсов приходит в списке типа **list[CallibriInfo]**. **CallibriInfo** содержит два значимых поля:
 1. Имя девайса. Тип данных **str**
 2. Мак-адрес девайса. Тип данных **str**


#### Подключение к устройству

Для подключения используется метод **connect_to**. Метод асинхронный, поэтому о статусе подключения можно узнать от соответствующего сигнала. Метод принимает следующие аргументы:

 1. Информацию об устройстве. Тип **CallibriInfo**
 2. Нужно ли устройство переподключать при отключении. Тип **bool**

Если вторым аргументом передано true:
 1. при незапланированном отключении девайса он подключится обратно
 2. если во время отключения было запущено сопротивление - его нужно будет включать отдельно
 3. если во время отключения был запущен сигнал - он включится самостоятельно, никаких действий предпринимать не нужно

Состояние уже подключенного девайса можно так же получить с помощью сигнала **connectionStateChanged**.

1. подключиться к сигналу:
    ```python
    selected_Callibri = # получить CallibriInfo из списка найденных устройств

    def on_device_connected(address: str, state: ConnectionState):
        if address==selected_Callibri.Address and state==ConnectionState.Connected:
            pass

    callibri_controller.connectionStateChanged.connect(on_device_connected)
    ``` 

2. подключиться к девайсу:

    ```python
    selected_Callibri = # получить CallibriInfo из списка найденных устройств
    callibri_controller.connect_to(info=selected_Callibri, need_reconnect=True)
    ```

#### Получение ЧСС

ЧСС является числом типа **float**. Это значение приходит не сразу, а после набора определеннгого количества данных. По окончанию набора данных библиотека оповестит сигналом **hasRRPicks**.

Чтобы получить ЧСС нужно:

1. подписаться на сигналы

    ```python
    selected_callibri_address = # сохраненный адрес нужного девайса
    def hr_values_updated(address: str, hr: float):
        if address == current_device:
            pass

    def has_rr_picks(address: str, has_picks: bool): # когда в этом методе появятся пики (придет значение True) появится первое значение ЧСС
        if address == current_device:
            pass

    callibri_controller.hrValuesUpdated.connect(hr_values_updated)
    callibri_controller.hasRRPicks.connect(has_rr_picks)
    ```

2. запустить вычисления

    ```python
    # здесь происходит подпись на сигналы

    selected_callibri_address = # сохраненный адрес нужного девайса
    callibri_controller.start_calculations(selected_callibri_address) # начало вычислений
    ```
3. по завершению работы отписаться от ивентов и остановить вычисления

    ```python
    # отключаем сигналы
    callibri_controller.hrValuesUpdated.disconnect()
    callibri_controller.hasRRPicks.disconnect()

    callibri_controller.stop_calculations(selected_callibri_address)
    ```


