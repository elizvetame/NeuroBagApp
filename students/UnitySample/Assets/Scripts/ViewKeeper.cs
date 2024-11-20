using SignalMath;
using System;
using System.Collections.Generic;
using Unity.VisualScripting;
using UnityEngine;
using UnityEngine.UIElements;

public class ViewKeeper : MonoBehaviour
{

    [SerializeField] VisualTreeAsset m_ListEntryTemplate;

    private Button m_startSearchButton;
    
    private ListView m_DeviceList;
    
    private VisualElement m_resistButtons;
    private Button m_startResistButton;
    private Button m_stopResistButton;
    
    private VisualElement m_resistLabels;
    private Label m_o1ResistLabel;
    private Label m_o2ResistLabel;
    private Label m_t3ResistLabel;
    private Label m_t4ResistLabel;
    
    private Button m_startCalcButton;
    private Button m_stopCalcButton;

    private Label m_artefactLabel;
    private Label m_relaxLabel;
    private Label m_attentionLabel;
    
    private VisualElement m_CalcButtonsElement;
    private VisualElement m_MindElement;
    
    private ProgressBar m_calibrationProgressBar;
    private void OnEnable()
    {
        VisualElement root = GetComponent<UIDocument>().rootVisualElement;

        m_startSearchButton = root.Q("searchButton") as Button;

        m_DeviceList = root.Q<ListView>("devices-list");

        m_CalcButtonsElement = root.Q<VisualElement>("calc-buttons-element");
        m_CalcButtonsElement.visible = false;

        m_resistButtons = root.Q<VisualElement>("resist-buttons");
        m_resistButtons.visible = false;
        m_resistLabels = root.Q<VisualElement>("resist-values");
        m_resistLabels.visible = false;

        m_o1ResistLabel = root.Q<Label>("o1-resist");
        m_o2ResistLabel = root.Q<Label>("o2-resist");
        m_t3ResistLabel = root.Q<Label>("t3-resist");
        m_t4ResistLabel = root.Q<Label>("t4-resist");

        m_startResistButton = root.Q <Button>("start-resist");
        m_stopResistButton = root.Q<Button>("stop-resist");

        m_startCalcButton = root.Q<Button>("start-calc");
        m_stopCalcButton = root.Q<Button>("stop-calc");

        m_artefactLabel = root.Q<Label>("artefact-label");
        m_relaxLabel = root.Q<Label>("relax-label");
        m_attentionLabel = root.Q<Label>("attention-label");

        m_calibrationProgressBar = root.Q<ProgressBar>("calibration-progress");

        m_MindElement = root.Q<VisualElement>("mind-viewer");
        m_MindElement.visible = false;
    }

    public void InitEvents(EventCallback<ClickEvent> startSearch,
        Action<IEnumerable<object>> deviceSelected,
        EventCallback<ClickEvent> startResist,
        EventCallback<ClickEvent> stopResist,
        EventCallback<ClickEvent> startCalcs,
        EventCallback<ClickEvent> stopCalcs)
    {
        m_startSearchButton.RegisterCallback(startSearch);
        m_startResistButton.RegisterCallback(startResist);
        m_stopResistButton.RegisterCallback(stopResist);
        m_startCalcButton.RegisterCallback(startCalcs);
        m_stopCalcButton.RegisterCallback(stopCalcs);
        m_DeviceList.selectionChanged += deviceSelected;

    }

    public void DeinitEvents(EventCallback<ClickEvent> startSearch,
        Action<IEnumerable<object>> deviceSelected,
        EventCallback<ClickEvent> startResist,
        EventCallback<ClickEvent> stopResist,
        EventCallback<ClickEvent> startCalcs,
        EventCallback<ClickEvent> stopCalcs)
    {
        m_startSearchButton.UnregisterCallback(startSearch);
        m_startResistButton.UnregisterCallback(startResist);
        m_stopResistButton.UnregisterCallback(stopResist);
        m_startCalcButton.UnregisterCallback(startCalcs);
        m_stopCalcButton.UnregisterCallback(stopCalcs);
        m_DeviceList.selectionChanged -= deviceSelected;
    }

    public void SetSearchState(SearchState state)
    {
        switch (state)
        {
            case SearchState.NotStarted:
                break;
            case SearchState.Searching:
                if (m_DeviceList != null && m_DeviceList.itemsSource != null)
                {
                    m_DeviceList.itemsSource.Clear();
                    m_DeviceList.Rebuild();
                }
                m_startSearchButton.text = "Поиск...";
                m_startSearchButton.SetEnabled(false);
                break;
            case SearchState.Finished:
                m_startSearchButton.text = "Искать заново";
                m_startSearchButton.SetEnabled(true);
                break;
        }

    }

    public void InitializeDevicesList(List<BrainBitInfo> infos)
    {
        m_DeviceList.makeItem = () =>
        {
            var newListEntry = m_ListEntryTemplate.Instantiate();
            var newListEntryLogic = new DeviceListEntryController();
            newListEntry.userData = newListEntryLogic;
            newListEntryLogic.SetVisualElement(newListEntry);
            return newListEntry;
        };

        m_DeviceList.bindItem = (item, index) =>
        {
            (item.userData as DeviceListEntryController)?.SetDeviceData(infos[index]);
        };

        m_DeviceList.fixedItemHeight = 50;
        m_DeviceList.itemsSource = infos;
    }

    public BrainBitInfo GetSelectedItem()
    {
        return m_DeviceList.selectedItem as BrainBitInfo;
    }

    public void UpdateDeviceState(string address, ConnectionState connectionState)
    {
        int i = 0;
        foreach (BrainBitInfo item in m_DeviceList.itemsSource)
        {
            if (item.Address == address)
            {
                item.ConnectionState = connectionState;
                m_DeviceList.RefreshItem(i);
                break;
            }
            i++;
        }
        if(connectionState == ConnectionState.Connected)
        {
            m_resistButtons.visible = true;
            m_resistLabels.visible = true;
        }
    }

    public void SetResistState(ResistValues resist)
    {
        m_o1ResistLabel.text = $"O1: {resist.O1}";
        m_o2ResistLabel.text = $"O2: {resist.O2}";
        m_t3ResistLabel.text = $"T3: {resist.T3}";
        m_t4ResistLabel.text = $"T4: {resist.T4}";
    }

    public void SetArtefacts(bool isArtefacted)
    {
        m_artefactLabel.text = isArtefacted ? "Артефакты: Есть" : "Артефакты: Нет";
    }

    public void SetEmotions(double relax, double attention)
    {
        m_relaxLabel.text = $"Расслабление: {relax}";
        m_attentionLabel.text = $"Внимание: {attention}";
    }

    public void SetCalibrationProgress(int percent)
    {
        m_calibrationProgressBar.value = percent;
        m_calibrationProgressBar.title = $"{percent}%";
    }

    public void EnableEmotions()
    {
        m_CalcButtonsElement.visible = true;
        m_MindElement.visible = true;
    }

}
