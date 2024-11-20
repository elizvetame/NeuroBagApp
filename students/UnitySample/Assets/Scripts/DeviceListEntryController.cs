using UnityEngine.UIElements;

public class DeviceListEntryController
{
    Label m_NameLabel;
    Label m_ConnectingLabel;

    public void SetVisualElement(VisualElement visualElement)
    {
        m_NameLabel = visualElement.Q<Label>("device-name");
        m_ConnectingLabel = visualElement.Q<Label>("connecting-label");
        m_ConnectingLabel.visible = false;
    }

    public void SetDeviceData(BrainBitInfo brainBitInfo)
    {
        m_ConnectingLabel.visible = false;
        m_NameLabel.text = brainBitInfo.Name + " (" + brainBitInfo.Address + ")";

        switch (brainBitInfo.ConnectionState)
        {
            case ConnectionState.Connection:
                m_ConnectingLabel.visible = true;
                m_ConnectingLabel.text = "Подключение...";
                break;
            case ConnectionState.Connected:
                m_ConnectingLabel.visible = true;
                m_ConnectingLabel.text = "Подключено";
                break;
            case ConnectionState.Disconnection:
            case ConnectionState.Disconnected:
                m_ConnectingLabel.visible = false;
                break;
            case ConnectionState.Error:
                m_ConnectingLabel.visible = true;
                m_ConnectingLabel.text = "Ошибка подключения";
                break;
        }
    }
}
