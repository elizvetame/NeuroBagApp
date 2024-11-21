# Python + Callibri + ECG from console

## Настройка проекта

Перед первым запуском необходимо установить зависимые бибилотеки:

```
pip install pyneurosdk2
pip install pycallibri-ecg
```

## Структура проекта

Проект состоит из одного файла, но сам файл содержит условно две части:
1. класс [CallibriController](https://gitlab.com/neurosdk2/cybergarden2024/-/blob/main/school/PythonSample%20(Console)/callibri_ecg_console_demo.py?ref_type=heads#L39)
2. [пример](https://gitlab.com/neurosdk2/cybergarden2024/-/blob/main/school/PythonSample%20(Console)/callibri_ecg_console_demo.py?ref_type=heads#L204) его использования 

**CallibriController** - это основной класс, на который нужно обратить внимание. В нем происходит все взаимодействие с девайсом Callibri, а так же получение ЧСС из дополнительной библиотеки. Каждый метод ожидает одним из аргументов мак-адрес устройства. Это означает, что объект **CallibriController** хранит в себе все подключенные пользователем устройства и различает их по мак-адресу. Все оповещения помимо основной информации так же отправляют мак-адрес устройства, от которого пришло это оповещение. Этот класс должен быть синглтоном, поэтому сразу после имплементации заведена переменная [callibri_controller](https://gitlab.com/neurosdk2/cybergarden2024/-/blob/main/school/PythonSample%20(Console)/callibri_ecg_console_demo.py?ref_type=heads#L201), с помощью которой нужно обращаться к контроллеру.

#### Поиск устройства

Поиск представлен методом **search_with_result** с аргументами:

1. время поиска. Указывается в секундах. Тип данных **int**
2. список мак-адресов устройств для поиска. Если передать пустой список - найдутся все девайся типа Callibri. Тим данных - **List[str]**.

Эта функция асинхронна. Чтобы получить список найденных девайсов нужно подключиться к нужному колбеку.

Как использовать:

1. подключиться к колбеку:
    ```python
    def on_device_founded(sensors: list[CallibriInfo]):
        pass

    callibri_controller.foundedDevices = on_device_founded
    ``` 

2. запустить поиск устройств:
    ```python
    callibri_controller.search_with_result(5, [])
    ``` 

Т.к. вся работа происходит из консоли необходимо дождаться окончания поиска. Здесь показан пример активного ожидания с использованием глобальной пременной:

    ```python
    is_scan_ended = False # флаг, который поднимается по окончании поиска
    founded_sensors = list() # один из вариантов получения списка девайсов. Список задается в колбеке
    def on_device_founded(sensors: list[CallibriInfo]):
        global is_scan_ended
        global founded_sensors
        founded_sensors = sensors
        callibri_controller.foundedDevices = None
        is_scan_ended = True
    
    callibri_controller.foundedDevices = on_device_founded
    callibri_controller.search_with_result(5, [])
    
    while not is_scan_ended: # активное ожидание
        print("Поиск...")
        time.sleep(1)

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

Состояние уже подключенного девайса можно так же получить с помощью колбека **connectionStateChanged**. Ожидание подключения так же происходит с помощью [активного ожидания](https://gitlab.com/neurosdk2/cybergarden2024/-/blob/main/school/PythonSample%20(Console)/callibri_ecg_console_demo.py?ref_type=heads#L242) по такому же принципу как и поиск.

1. подписатьсмя на колбек:
    ```python
    selected_Callibri = # получить CallibriInfo из списка найденных устройств

    def on_device_connection_state_changed(address: str, state: ConnectionState):
        if address==selected_Callibri.Address and state==ConnectionState.Connected:
            pass

    callibri_controller.connectionStateChanged = on_device_connection_state_changed
    ``` 

2. подключиться к девайсу:

    ```python
    selected_Callibri = # получить CallibriInfo из списка найденных устройств
    callibri_controller.connect_to(info=selected_Callibri, need_reconnect=True)
    ```

#### Получение ЧСС

ЧСС является числом типа **float**. Это значение приходит не сразу, а после набора определеннгого количества данных. По окончанию набора данных библиотека оповестит колбеком **hasRRPicks**.

Чтобы получить ЧСС нужно:

1. подписаться на колбеки

    ```python
    selected_callibri_address = # сохраненный адрес нужного девайса
    def hr_values_updated(address: str, hr: float):
        if address == current_device:
            pass

    def has_rr_picks(address: str, has_picks: bool): # когда в этом методе появятся пики (придет значение True) появится первое значение ЧСС
        if address == current_device:
            pass

    callibri_controller.hrValuesUpdated = hr_values_updated
    callibri_controller.hasRRPicks = has_rr_picks
    ```

2. запустить вычисления

    ```python
    # здесь происходит подпись на сигналы

    selected_callibri_address = # сохраненный адрес нужного девайса
    callibri_controller.start_calculations(selected_callibri_address) # начало вычислений
    ```
3. по завершению работы отписаться от ивентов и остановить вычисления

    ```python
    # отписываемся от колбеков
    callibri_controller.hrValuesUpdated = None
    callibri_controller.hasRRPicks = None

    callibri_controller.stop_calculations(selected_callibri_address)
    ```


