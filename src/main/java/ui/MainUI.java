package ui;

import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import java.awt.*;

public class MainUI {
    private final MontoyaApi api;
    private final JPanel mainPanel;

    public MainUI(MontoyaApi api) {
        this.api = api;
        this.mainPanel = createMainPanel();
    }

    private JPanel createMainPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTabbedPane tabbedPane = new JTabbedPane();


        tabbedPane.addTab("Mobile Proxy Setup", new MobileProxyPanel(api));
        tabbedPane.addTab("Frida Setup", new FridaSetupPanel(api));
        tabbedPane.addTab("Curl to Repeater", new CurlToRepeaterPanel(api));
        tabbedPane.addTab("Settings", new SettingsPanel(api));

        panel.add(tabbedPane, BorderLayout.CENTER);

        return panel;
    }

    public Component getComponent() {
        return mainPanel;
    }
}

