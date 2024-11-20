using NeuroSDK;
using SignalMath;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Runtime.CompilerServices;
using System.Threading.Tasks;
using UnityEngine;

public enum ResistState
{
    Normal, Bad
}
public struct ResistValues
{
    public ResistState O1;
    public ResistState O2;
    public ResistState T3;
    public ResistState T4;
}

public class MindDataReal
{
    public double Attention;
    public double Relaxation;
}

public class BrainBitInfo
{
    public string Name { get; set; }
    public string Address { get; set; }
    public SensorInfo SensInfo { get; set; }
    public ConnectionState ConnectionState { get; set; }
}

public enum ConnectionState
{
    Connection,
    Connected,
    Disconnection,
    Disconnected,
    Error
}

class BrainBitAdditional
{
    public bool needReconnect = false;
    public BrainBitSensor bb;
    public EegEmotionalMath emotionalMath;
    public bool isSignal = false;
    public bool calibrationStarted = false;

    public BrainBitAdditional(bool needReconnect, BrainBitSensor bb)
    {
        this.needReconnect = needReconnect;
        this.bb = bb;
        emotionalMath = CreateEmotionalMath();
    }

    private EegEmotionalMath CreateEmotionalMath()
    {
        int samplingFrequencyHz = 250;
        var mathLib = new MathLibSetting
        {
            sampling_rate = (uint)samplingFrequencyHz,
            process_win_freq = 25,
            fft_window = (uint)samplingFrequencyHz * 2,
            n_first_sec_skipped = 4,
            bipolar_mode = true,
            squared_spectrum = true,
            channels_number = (uint)1,
            channel_for_analysis = 0
        };

        var artsDetect = new ArtifactDetectSetting
        {
            art_bord = 110,
            allowed_percent_artpoints = 70,
            raw_betap_limit = 800_000,
            total_pow_border = (uint)(8 * 1e7),
            global_artwin_sec = 4,
            spect_art_by_totalp = false,
            hanning_win_spectrum = false,
            hamming_win_spectrum = true,
            num_wins_for_quality_avg = 100
        };

        var shortArtsDetect = new ShortArtifactDetectSetting
        {
            ampl_art_detect_win_size = 200,
            ampl_art_zerod_area = 200,
            ampl_art_extremum_border = 25
        };

        var mentalAndSpectralSettings = new MentalAndSpectralSetting
        {
            n_sec_for_instant_estimation = 2,
            n_sec_for_averaging = 4
        };
        return new EegEmotionalMath(mathLib, artsDetect, shortArtsDetect, mentalAndSpectralSettings);
    }
}

public class BrainBitController : MonoBehaviour
{
    #region Init
    void Awake()
    {
        DontDestroyOnLoad(this.gameObject);
    }
    #endregion

    #region MainThreadImpl
    private Queue<Action> jobs = new Queue<Action>();

    void Update()
    {
        while (jobs.Count > 0)
        {
            Debug.Log("Job invoked");
            jobs.Dequeue().Invoke();
        }
            
    }

    internal void AddJob(Action newJob)
    {
        jobs.Enqueue(newJob);
    }
    #endregion


    #region Fields
    private Scanner scanner;
    private Dictionary<string, BrainBitAdditional> connectedDevices = new();
    private List<string> disconnectedDevices = new();

    public IReadOnlyList<string> ConnectedDevices => connectedDevices.Keys.ToList();

    #endregion

    #region Interface
    public event Action<string, ConnectionState> EventConnectionStateChanged;
    public event Action<string, int> EventBatteryChanged;

    public async Task<IEnumerable<BrainBitInfo>> SearchWithResult(int seconds, List<string> addresses)
    {
        return await Task.Run(async () =>
        {
            createScanner();
            scanner.Start();
            await Task.Delay(seconds * 1000);
            scanner.Stop();
            var founded = scanner.Sensors;
            if (founded.Count > 0)
            {
                return founded.Where(si => addresses.Count() < 1 || addresses.Contains(si.Address))
                    .Select(si => new BrainBitInfo()
                    {
                        Name = si.Name,
                        Address = si.Address,
                        SensInfo = si,
                        ConnectionState=ConnectionState.Disconnected
                    });
            }
            return new List<BrainBitInfo>();
        });
    }

    public async Task ConnectTo(BrainBitInfo info, bool needReconnect = false)
    {
        await Task.Run(() =>
        {
            createScanner();
            SendConnectionEvent(info.Address, ConnectionState.Connection);

            try
            {
#if ANDROID
                AddJob(() =>
#endif
                {
                    if (scanner.CreateSensor(info.SensInfo) is BrainBitSensor bb)
                    {
                        bb.EventSensorStateChanged += eventSensorStateChanged;
                        bb.EventBatteryChanged += eventBatteryChanged;
                        connectedDevices.Add(info.Address, new BrainBitAdditional(needReconnect, bb));
                        SendConnectionEvent(info.Address, ConnectionState.Connected);
                    }
                    else
                    {
                        SendConnectionEvent(info.Address, ConnectionState.Error);
                    }

                }
#if ANDROID
                );
#endif
            }
            catch (Exception ex)
            {
                SendConnectionEvent(info.Address, ConnectionState.Error);
            }
        });
    }

    private void SendConnectionEvent(string address, ConnectionState state)
    {
        Debug.Log("SendConnectionEvent");
        AddJob(() => {
            Debug.Log("SendConnectionEvent from job");
            EventConnectionStateChanged?.Invoke(address, state);
        });
    }

    public async Task DisconnectFrom(string address)
    {
        await Task.Run(() =>
        {
            SendConnectionEvent(address, ConnectionState.Disconnection);
            if (disconnectedDevices.Contains(address))
            {
                disconnectedDevices.Remove(address);
                if (disconnectedDevices.Count() < 1) scanner.Stop();
            }
            var tmp = connectedDevices[address];
            connectedDevices.Remove(address);
            tmp.bb.Disconnect();
            tmp.bb.Dispose();
            tmp.bb = null;
            SendConnectionEvent(address, ConnectionState.Disconnected);
        });
    }

#endregion

    #region Resistance
    public event Action<string, ResistValues> EventResistValueReceived;
    public async void StartResist(string address)
    {
        await Task.Run(() => {
            var tmp = connectedDevices[address].bb;
            tmp.EventBrainBitResistDataRecived += eventBrainBitResistDataRecived;
            tmp.ExecCommand(SensorCommand.CommandStartResist);
        });
    }

    public async void StopResist(string address)
    {
        await Task.Run(() => {
            var tmp = connectedDevices[address].bb;
            tmp.EventBrainBitResistDataRecived -= eventBrainBitResistDataRecived;
            tmp.ExecCommand(SensorCommand.CommandStopResist);
        });
    }

    private void eventBrainBitResistDataRecived(ISensor sensor, BrainBitResistData data)
    {
        AddJob(() => {
            EventResistValueReceived?.Invoke(sensor.Address, new ResistValues()
            {
                O1 = data.O1 < 2500000 ? ResistState.Normal : ResistState.Bad,
                O2 = data.O2 < 2500000 ? ResistState.Normal : ResistState.Bad,
                T3 = data.T3 < 2500000 ? ResistState.Normal : ResistState.Bad,
                T4 = data.T4 < 2500000 ? ResistState.Normal : ResistState.Bad,
            });
        });
    }
    #endregion

    #region Internal
    private void eventBatteryChanged(ISensor sensor, int battPower)
    {
        AddJob(() =>
        {
            EventBatteryChanged?.Invoke(sensor.Address, battPower);
        });
        Debug.Log($"Power ({sensor.Address}): {battPower}");
    }

    private void eventSensorStateChanged(ISensor sensor, SensorState sensorState)
    {
        SendConnectionEvent(sensor.Address, sensorState == SensorState.StateInRange ? ConnectionState.Connected : ConnectionState.Disconnected);
        
        if (sensorState == SensorState.StateOutOfRange && connectedDevices.ContainsKey(sensor.Address) && connectedDevices[sensor.Address].needReconnect)
        {
            disconnectedDevices.Add(sensor.Address);
            AddJob(() =>
            {
                if (scanner == null) createScanner();
                scanner.Start();
            });
        }
    }

    private void eventSensorsChanged(IScanner scanner, IReadOnlyList<SensorInfo> sensors)
    {
        var founded = sensors.Where(si => disconnectedDevices.Contains(si.Address));
        if (founded.Count() > 0)
        {
            scanner.Stop();
            var bbAdditional = connectedDevices[founded.First().Address];
            bbAdditional.bb.Connect();
            if (bbAdditional.bb.State != SensorState.StateInRange)
            {
                scanner.Start();
            }
            else
            {
                if (bbAdditional.isSignal)
                {
                    bbAdditional.bb.ExecCommand(SensorCommand.CommandStartSignal);
                }
                disconnectedDevices.Remove(founded.First().Address);
            }
        }
    }

    private void createScanner()
    {
        if(scanner == null)
        {
            scanner = new Scanner(SensorFamily.SensorLEBrainBit);
            scanner.EventSensorsChanged += eventSensorsChanged;
        }
        
    }
    #endregion

    #region Calculations
    public event Action<string, MindDataReal> EventMindDataUpdated;
    public event Action<string, int> EventCalibrationProgressChanged;
    public event Action<string, bool> EventArtefactFounded;

    public async void StartCalculations(string address)
    {
        await Task.Run(() => {
            var tmp = connectedDevices[address].bb;
            connectedDevices[address].calibrationStarted = true;
            connectedDevices[address].emotionalMath.StartCalibration();
            tmp.EventBrainBitSignalDataRecived += eventBrainBitSignalDataRecived;
            tmp.ExecCommand(SensorCommand.CommandStartSignal);
            connectedDevices[address].isSignal = true;
        });
    }

    private void eventBrainBitSignalDataRecived(ISensor sensor, BrainBitSignalData[] data)
    {
        var samples = new RawChannels[data.Length];

        for (var i = 0; i < data.Length; i++)
        {
            samples[i].LeftBipolar = data[i].T3 - data[i].O1;
            samples[i].RightBipolar = data[i].T4 - data[i].O2;
        }

        var math = connectedDevices[sensor.Address].emotionalMath;

        try
        {
            math.PushData(samples);
            math.ProcessDataArr();
            AddJob(() =>
            {
                EventArtefactFounded?.Invoke(sensor.Address, math.IsBothSidesArtifacted());
            });
            Debug.Log($"Is artefacted: {math.IsBothSidesArtifacted()}");
            if (connectedDevices[sensor.Address].calibrationStarted)
            {
                if (math.CalibrationFinished())
                {
                    connectedDevices[sensor.Address].calibrationStarted = false;
                    AddJob(() => 
                    {
                        EventCalibrationProgressChanged?.Invoke(sensor.Address, 100);
                    });
                }
                else {
                    int progress = math.GetCallibrationPercents();
                    AddJob(() =>
                    {
                        EventCalibrationProgressChanged?.Invoke(sensor.Address, progress);
                    });
                }
            }
            else
            {
                MindData[] mindData = math?.ReadMentalDataArr();
                if (mindData.Length > 0)
                {
                    MindData md = mindData.Last();
                    AddJob(() => {
                        EventMindDataUpdated?.Invoke(sensor.Address, new MindDataReal()
                        {
                            Relaxation = md.RelRelaxation,
                            Attention = md.RelAttention
                        });
                    });
                }
            }
        }
        catch (Exception e)
        {
            Debug.Log(e.Message);
        }
    }

    public async void StopCalculations(string address) 
    {
        await Task.Run(() => {
            var tmp = connectedDevices[address].bb;
            connectedDevices[address].calibrationStarted = false;
            tmp.EventBrainBitSignalDataRecived -= eventBrainBitSignalDataRecived;
            tmp.ExecCommand(SensorCommand.CommandStopSignal);
            connectedDevices[address].isSignal = true;
        });
    }
    #endregion

    public void StopAll()
    {
        if (scanner != null)
        {
            scanner.EventSensorsChanged -= eventSensorsChanged;
            scanner.Stop();
            scanner.Dispose();
            scanner = null;
        }
        foreach(var bb in connectedDevices.Values)
        {
            bb.bb.Disconnect();
            bb.bb.Dispose();
        }
        connectedDevices.Clear();
    }
}
