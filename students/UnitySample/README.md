# Unity + BrainBit + Emotions

## Структура проекта

В проекте используется UI Toolkit, весь интерфейс строится на uxml. В проекте три основных части (файла):
 1. [BrainBitController](https://gitlab.com/neurosdk2/cybergarden2024/-/blob/main/students/UnitySample/Assets/Scripts/BrainBitController.cs?ref_type=heads) - собственно взаимодействие с устройством
 2. [MainWindowController](https://gitlab.com/neurosdk2/cybergarden2024/-/blob/main/students/UnitySample/Assets/Scripts/MainWindowController.cs?ref_type=heads) - соединение данных от устройства и интерфейса
 3. [ViewKeeper](https://gitlab.com/neurosdk2/cybergarden2024/-/blob/main/students/UnitySample/Assets/Scripts/ViewKeeper.cs?ref_type=heads) - содежит все view-элементы проекта

**BrainBitController** - это основной класс, на который нужно обратить внимание. В нем происходит все взаимодействие с девайсом BrainBit, а так же получение эмоциональных состояний из дополнитеной библиотеки. Каждый метод ожидает одним из аргументов мак-адрес устройства. Это означает, что объект **BrainBitController** хранит в себе все подключенные подльзователем устройства и различает их по мак-адресу. Все оповещения помимо основной информации так же отправляют мак-адрес устройства, от которого пришло это оповещение. Этот класс должен быть только один в проекте и при переходе между сценами не уничтожаться. Для этого его нужно поместить на отдельный объект на сцене, далее он самостоятельно поднимет флаг **DontDestroyOnLoad**.

#### Поиск устройства

Поиск представлен методом **SearchWithResult** с аргументами:

1. время поиска. Указывается в секундах. Тип данных **int**
2. список мак-адресов устройств для поиска. Если передать пустой список - найдутся все девайся типа BrainBit. Тим данных - **List\<string\>**.

Эта функция асинхронна, т.е. вызывать ее нужно со словом **await**.

Возвращаемый тип данных **IEnumerable\<BrainBitInfo\>**. **BrainBitInfo** содержит два значимых поля:
 1. Имя девайса. Тип данных **string**
 2. Мак-адрес девайа. Тип данных **string**

Пример использования:

```cs
var sensors = await brainBitController.SearchWithResult(10, new List<string>());
``` 

#### Подключение к устройству

Для подключения используется метод **ConnectTo**. Метод так же асинхронный, но не возвращает ничего, поэтому ожидать завершения не обязательно. После подключения к устройству отправится ивент о состоянии подключения устройства. Метод принимает следующие аргументы:

 1. Информацию об устройстве. Тип **BrainBitInfo**
 2. Нужно ли устройство переподключать при отключении. Тип **bool**

Если вторым аргументом передано true:
 1. при незапланированном отключении девайса он подключится обратно
 2. если во время отключения было запущено сопротивление - его нужно будет включать отдельно
 3. если во время отключения был запущен сигнал - он включится самостоятельно, никаких действий предпринимать не нужно

```cs
await brainBitController.ConnectTo(selectedBB, true);
```

Получение состояния подключения:

```cs
private void BrainBitController_EventConnectionStateChanged(string address, ConnectionState state)
{
    Debug.Log($"Device {address} is {state}");
}

private async void ConnectToDevice(string selectedBB)
{
    brainBitController.EventConnectionStateChanged += BrainBitController_EventConnectionStateChanged; 
    await brainBitController.ConnectTo(selectedBB, true);
}
```

Если больше не нужно считывать состояние подключения девайса нужно отписаться от ивента в любом месте проекта:

```cs
brainBitController.EventConnectionStateChanged -= BrainBitController_EventConnectionStateChanged; 
```

#### Проверка качества наложения

Для проверки качества наложения используются значения сопротивления. Чтобы получить эти значения нужно:
 1. подписаться на ивент. Данные начнут приходить только после запуска сопротивлений.

    ```cs
    private void eventResistValueReceived(string address, ResistValues resists)
    {
        Debug.Log($"Device is {address} with resists {resists}");
    }

    void StartResist() 
    {
        brainBitController.EventResistValueReceived += eventResistValueReceived; 
    }
    ```

 2. запустить проверку сопротивлений

    ```cs
    void StartResist() 
    {
        brainBitController.EventResistValueReceived += eventResistValueReceived; 

        string selectedBB = ""; // сюда нужно указать мак-адрес вашего устройства
        brainBitController.StartResist(selectedBB);
    }
    ```

 3. как только достигнуто желаемое качество остановить сьем данных и отписаться от ивента

    ```cs
    void StopResist()
    {
        string selectedBB = ""; // сюда нужно указать мак-адрес вашего устройства
        brainBitController.EventResistValueReceived -= eventResistValueReceived; // отписываемся от ивента
        brainBitController.StopResist(selectedBB); // останавливаем сопротивление
    }
    ```

Структура **ResistValues** содержит 4 поля для каждого канала - O1, O2, T3, T4. Состояние сопротивления представлено перечислением. Сопротивление может быть плохим или нормальным:
 
 1. Bad
 2. Normal

#### Получение эмоциональных соостояний

Эмоциональные состояния представлены двумя параметрами - расслаблением и вниманием. Каждый параметр находится в диапазоне 0..100. Параметры представлены в процентах. Только один параметр может быть отличен от 0.

Так же нужно учитывать следующее:
1. для получения данных нужно **откалиброваться**. Калибровка начинается одновременно с началом вычислений. О ее прогрессе можно узнать с помощью соответствующего оповещения. Оповещение содержит мак-адрес калибрующегося девайса, а так же процент прогресса калибровки.
2. отслеживать **качество сигнала**. Качество сигнала можно получить с помощью ивента **EventArtefactFounded**. Если в сигнале присутствуют артефакты калибровка не будет проходить, а данные эмоциональных состояний не будут меняться.
3. Получить эмоциональные состояния можно только **после** калибровки

Чтобы получить эмоциональные состояния нужно:

1. подписаться на ивенты

    ```cs
    private void eventMindDataUpdated(string address, MindDataReal mindData)
    {
        Debug.Log($"Device is {address} with relax {mindData.Relaxation} and attention {mindData.Attention}");
    }

    private void eventCalibrationProgressChanged(string address, int percent)
    {
        Debug.Log($"Device is {address} calibration progress {percent}");
    }
    
    private void eventArtefactFounded(string address, bool artefacted)
    {
        Debug.Log($"Device is {address} artefacts {artefacted}");
    }

    private void StartCalculations()
    {
        brainBitController.EventCalibrationProgressChanged += eventCalibrationProgressChanged; // оповещение о состоянии калибровки
        brainBitController.EventMindDataUpdated += eventMindDataUpdated; // оповещение об изменении эмоциональных состояний
        brainBitController.EventArtefactFounded += eventArtefactFounded; // оповещение о  плохои качестве сигнала
    }
    ```

2. запустить вычисления

    ```cs
    private void StartCalculations()
    {
        // здесь происходит подпись на оповещения

        string selectedBB = ""; // сюда нужно указать мак-адрес вашего устройства
        brainBitController.StartCalculations(selectedBB); // начало вычислений
    }
    ```
3. по завершению работы отписаться от ивентов и остановить вычисления

    ```cs
    private void StartCalculations()
    {
        brainBitController.EventCalibrationProgressChanged -= eventCalibrationProgressChanged; // оповещение о состоянии калибровки
        brainBitController.EventMindDataUpdated -= eventMindDataUpdated; // оповещение об изменении эмоциональных состояний
        brainBitController.EventArtefactFounded -= eventArtefactFounded; // оповещение о  плохои качестве сигнала

        string selectedBB = ""; // сюда нужно указать мак-адрес вашего устройства
        brainBitController.StopCalculations(selectedBB);
    }
    ```

Структура **MindDataReal** состоит из следующих полей:
 1. Attention. Внимание. Тип **double**
 2. Relaxation. Расслабление. Тип **double**


