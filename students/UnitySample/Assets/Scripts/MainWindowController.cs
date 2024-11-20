using SignalMath;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using UnityEngine;
using UnityEngine.Android;
using UnityEngine.UIElements;

public enum SearchState
{
    NotStarted, Searching, Finished
}

public class MainWindowController : MonoBehaviour
{
    ViewKeeper vKeeper;

    private BrainBitController brainBitController;


    private void OnEnable()
    {
        vKeeper = GetComponent<ViewKeeper>();

        brainBitController = GameObject.FindWithTag("NeuroController").GetComponent<BrainBitController>();
    }

    private void Start() 
    {
        vKeeper.InitEvents(StartSearchMessage, ConnectToDevice, StartResistMessage, StopResistMessage, StartCalculationsMessage, StopCalculationsMessage);

#if UNITY_ANDROID
        if (SystemInfo.operatingSystem.Contains("31") ||
            SystemInfo.operatingSystem.Contains("32") ||
            SystemInfo.operatingSystem.Contains("33") ||
            SystemInfo.operatingSystem.Contains("34"))
        {
            Permission.RequestUserPermission("android.permission.BLUETOOTH_SCAN");
            Permission.RequestUserPermission("android.permission.BLUETOOTH_CONNECT");
        }
        else
        {
            Permission.RequestUserPermission("android.permission.ACCESS_FINE_LOCATION");
            Permission.RequestUserPermission("android.permission.ACCESS_COARSE_LOCATION");
        }
#endif
    }

    private void OnDisable()
    {
        vKeeper.DeinitEvents(StartSearchMessage, ConnectToDevice, StartResistMessage, StopResistMessage, StartCalculationsMessage, StopCalculationsMessage);

        brainBitController.StopAll();
    }

    private void StartResistMessage(ClickEvent evt) 
    {
        brainBitController.EventResistValueReceived += eventResistValueReceived; 
        brainBitController.StartResist(brainBitController.ConnectedDevices[0]);
    }

    private void StopResistMessage(ClickEvent evt)
    {
        brainBitController.EventResistValueReceived -= eventResistValueReceived;
        brainBitController.StopResist(brainBitController.ConnectedDevices[0]);
        vKeeper.EnableEmotions();
    }

    private void eventResistValueReceived(string address, ResistValues resists)
    {
        if(brainBitController.ConnectedDevices[0] == address)
        {
            vKeeper.SetResistState(resists);
        }
    }

    private async void StartSearchMessage(ClickEvent evt)
    {
        vKeeper.SetSearchState(SearchState.Searching);

        var sensors = await brainBitController.SearchWithResult(10, new List<string>());

        vKeeper.SetSearchState(SearchState.Finished);

        vKeeper.InitializeDevicesList(sensors.ToList());
    }

    private async void ConnectToDevice(IEnumerable<object> selectedItems)
    {
        brainBitController.EventConnectionStateChanged += vKeeper.UpdateDeviceState;
        var selectedBB = vKeeper.GetSelectedItem();
        if (selectedBB.ConnectionState == ConnectionState.Connected || selectedBB.ConnectionState == ConnectionState.Connection) return;

        await brainBitController.ConnectTo(selectedBB, true);
    }

    private void StartCalculationsMessage(ClickEvent evt)
    {
        brainBitController.StartCalculations(brainBitController.ConnectedDevices[0]);
        brainBitController.EventCalibrationProgressChanged += eventCalibrationProgressChanged;
        brainBitController.EventMindDataUpdated += eventMindDataUpdated;
        brainBitController.EventArtefactFounded += eventArtefactFounded;
    }

    private void StopCalculationsMessage(ClickEvent evt)
    {
        brainBitController.EventCalibrationProgressChanged -= eventCalibrationProgressChanged;
        brainBitController.EventMindDataUpdated -= eventMindDataUpdated;
        brainBitController.EventArtefactFounded -= eventArtefactFounded;
        brainBitController.StopCalculations(brainBitController.ConnectedDevices[0]);
    }

    private void eventArtefactFounded(string address, bool artefacted)
    {
        if (brainBitController.ConnectedDevices[0] == address)
        {
            vKeeper.SetArtefacts(artefacted);
        }
    }

    private void eventMindDataUpdated(string address, MindDataReal mindData)
    {
        if (brainBitController.ConnectedDevices[0] == address)
        {
            vKeeper.SetEmotions(mindData.Relaxation, mindData.Attention);
        }
    }

    private void eventCalibrationProgressChanged(string address, int percent)
    {
        if (brainBitController.ConnectedDevices[0] == address)
        {
            vKeeper.SetCalibrationProgress(percent);
        }
    }

}
