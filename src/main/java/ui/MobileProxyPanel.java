package ui;

import burp.api.montoya.MontoyaApi;
import utils.ADBHelper;
import utils.EmulatorHelper;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobileProxyPanel extends JPanel {
    private final MontoyaApi api;
    private final JTextArea deviceListArea;
    private final JTextField proxyHostField;
    private final JTextField proxyPortField;
    private final JTextArea logOutput;
    private final ADBHelper adbHelper;

    // Emulator components
    private final EmulatorHelper emulatorHelper;
    private final JList<String> emulatorList;
    private final DefaultListModel<String> emulatorListModel;
    private final JTextArea runningEmulatorsArea;
    private final JCheckBox writableSystemCheck;
    private final JTextField dnsServerField;
    private final Map<String, Process> runningEmulatorProcesses;

    public MobileProxyPanel(MontoyaApi api) {
        this.api = api;
        this.adbHelper = new ADBHelper(api);
        this.emulatorHelper = new EmulatorHelper(api);
        this.runningEmulatorProcesses = new HashMap<>();

        // Initialize all final UI components FIRST before using them
        deviceListArea = new JTextArea(4, 25);
        proxyHostField = new JTextField("127.0.0.1", 20);
        proxyPortField = new JTextField("8080", 20);
        logOutput = new JTextArea(6, 25);
        emulatorListModel = new DefaultListModel<>();
        emulatorList = new JList<>(emulatorListModel);
        writableSystemCheck = new JCheckBox("Writable System (-writable-system)");
        dnsServerField = new JTextField("8.8.8.8", 15);
        runningEmulatorsArea = new JTextArea(4, 25);

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel titleLabel = new JLabel("Mobile Proxy Setup & Emulator Management");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        add(titleLabel, BorderLayout.NORTH);

        // Create split pane: Left = Proxy Setup, Right = Emulator Management
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(0.5);

        // LEFT PANEL - Proxy Setup
        JPanel leftPanel = createProxyPanel();

        // RIGHT PANEL - Emulator Management
        JPanel rightPanel = createEmulatorPanel();

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);

        add(splitPane, BorderLayout.CENTER);

        // Auto-refresh devices and emulators on load
        SwingUtilities.invokeLater(() -> {
            refreshDevices();
            refreshEmulatorList();
            refreshRunningEmulators();
        });
    }

    private JPanel createProxyPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));

        // Device list panel
        JPanel devicePanel = new JPanel(new BorderLayout(5, 5));
        devicePanel.setBorder(BorderFactory.createTitledBorder("Connected Devices"));


        deviceListArea.setEditable(false);
        deviceListArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane deviceScroll = new JScrollPane(deviceListArea);
        devicePanel.add(deviceScroll, BorderLayout.CENTER);

        JButton refreshDevicesBtn = new JButton("Refresh Devices");
        refreshDevicesBtn.addActionListener(e -> refreshDevices());
        devicePanel.add(refreshDevicesBtn, BorderLayout.SOUTH);

        contentPanel.add(devicePanel, BorderLayout.NORTH);

        // Proxy settings panel
        JPanel proxyPanel = new JPanel(new GridBagLayout());
        proxyPanel.setBorder(BorderFactory.createTitledBorder("Proxy Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        proxyPanel.add(new JLabel("Proxy Host:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;

        proxyPanel.add(proxyHostField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        proxyPanel.add(new JLabel("Proxy Port:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;

        proxyPanel.add(proxyPortField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton setProxyBtn = new JButton("Set Proxy");
        setProxyBtn.addActionListener(e -> setProxy());
        buttonPanel.add(setProxyBtn);

        JButton clearProxyBtn = new JButton("Clear Proxy");
        clearProxyBtn.addActionListener(e -> clearProxy());
        buttonPanel.add(clearProxyBtn);

        JButton checkProxyBtn = new JButton("Check Proxy");
        checkProxyBtn.addActionListener(e -> checkProxy());
        buttonPanel.add(checkProxyBtn);

        proxyPanel.add(buttonPanel, gbc);

        contentPanel.add(proxyPanel, BorderLayout.CENTER);

        // Log panel
        JPanel logPanel = new JPanel(new BorderLayout(5, 5));
        logPanel.setBorder(BorderFactory.createTitledBorder("Log"));

        logOutput.setEditable(false);
        logOutput.setFont(new Font("Monospaced", Font.PLAIN, 10));
        JScrollPane logScroll = new JScrollPane(logOutput);
        logPanel.add(logScroll, BorderLayout.CENTER);

        JButton clearLogBtn = new JButton("Clear Log");
        clearLogBtn.addActionListener(e -> logOutput.setText(""));
        logPanel.add(clearLogBtn, BorderLayout.SOUTH);

        contentPanel.add(logPanel, BorderLayout.SOUTH);

        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createEmulatorPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Emulator Management"));

        // Top: Available emulators list
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(new JLabel("Available Emulators (AVDs):"), BorderLayout.NORTH);

        emulatorList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        emulatorList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane emulatorScroll = new JScrollPane(emulatorList);
        emulatorScroll.setPreferredSize(new Dimension(250, 100));
        topPanel.add(emulatorScroll, BorderLayout.CENTER);

        JButton refreshEmulatorBtn = new JButton("Refresh List");
        refreshEmulatorBtn.addActionListener(e -> refreshEmulatorList());
        topPanel.add(refreshEmulatorBtn, BorderLayout.SOUTH);

        panel.add(topPanel, BorderLayout.NORTH);

        // Middle: Emulator options
        JPanel optionsPanel = new JPanel(new GridBagLayout());
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Launch Options"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        writableSystemCheck.setSelected(true);
        optionsPanel.add(writableSystemCheck, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        optionsPanel.add(new JLabel("DNS Server:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        optionsPanel.add(dnsServerField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        JPanel actionPanel = new JPanel(new GridLayout(1, 2, 5, 5));

        JButton startBtn = new JButton("▶ Start Selected");
        startBtn.setBackground(new Color(76, 175, 80));
        startBtn.setForeground(Color.WHITE);
        startBtn.setOpaque(true);
        startBtn.addActionListener(e -> startSelectedEmulator());
        actionPanel.add(startBtn);

        JButton stopBtn = new JButton("■ Stop Selected");
        stopBtn.setBackground(new Color(244, 67, 54));
        stopBtn.setForeground(Color.WHITE);
        stopBtn.setOpaque(true);
        stopBtn.addActionListener(e -> stopSelectedEmulator());
        actionPanel.add(stopBtn);

        optionsPanel.add(actionPanel, gbc);

        panel.add(optionsPanel, BorderLayout.CENTER);

        // Bottom: Running emulators
        JPanel runningPanel = new JPanel(new BorderLayout(5, 5));
        runningPanel.setBorder(BorderFactory.createTitledBorder("Running Emulators"));

        runningEmulatorsArea.setEditable(false);
        runningEmulatorsArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane runningScroll = new JScrollPane(runningEmulatorsArea);
        runningPanel.add(runningScroll, BorderLayout.CENTER);

        JButton refreshRunningBtn = new JButton("Refresh Running");
        refreshRunningBtn.addActionListener(e -> refreshRunningEmulators());
        runningPanel.add(refreshRunningBtn, BorderLayout.SOUTH);

        panel.add(runningPanel, BorderLayout.SOUTH);

        return panel;
    }

    // ...existing proxy methods...

    private void refreshDevices() {
        log("Checking for connected devices...");
        deviceListArea.setText("Scanning...");

        SwingWorker<List<String>, Void> worker = new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return adbHelper.getConnectedDevices();
            }

            @Override
            protected void done() {
                try {
                    List<String> devices = get();
                    if (devices.isEmpty()) {
                        deviceListArea.setText("No devices connected");
                        log("✗ No devices found");
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < devices.size(); i++) {
                            sb.append((i + 1)).append(". ").append(devices.get(i)).append("\n");
                        }
                        deviceListArea.setText(sb.toString());
                        log("✓ Found " + devices.size() + " device(s)");
                    }
                } catch (Exception e) {
                    deviceListArea.setText("Error: " + e.getMessage());
                    log("✗ Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void setProxy() {
        String host = proxyHostField.getText().trim();
        String port = proxyPortField.getText().trim();

        if (host.isEmpty() || port.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter both host and port", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        log("Setting proxy to " + host + ":" + port + "...");

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return adbHelper.setProxy(host, port);
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        log("✓ Proxy set successfully");
                        JOptionPane.showMessageDialog(MobileProxyPanel.this,
                            "Proxy set to " + host + ":" + port,
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        log("✗ Failed to set proxy");
                        JOptionPane.showMessageDialog(MobileProxyPanel.this,
                            "Failed to set proxy. Check if device is connected.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    log("✗ Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void clearProxy() {
        log("Clearing proxy settings...");

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return adbHelper.clearProxy();
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        log("✓ Proxy cleared successfully");
                        JOptionPane.showMessageDialog(MobileProxyPanel.this,
                            "Proxy cleared successfully",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        log("✗ Failed to clear proxy");
                    }
                } catch (Exception e) {
                    log("✗ Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void checkProxy() {
        log("Checking current proxy settings...");

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return adbHelper.getProxySettings();
            }

            @Override
            protected void done() {
                try {
                    String settings = get();
                    log("Current proxy: " + settings);
                    JOptionPane.showMessageDialog(MobileProxyPanel.this,
                        "Current proxy settings:\n" + settings,
                        "Proxy Status", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    log("✗ Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void log(String message) {
        logOutput.append(message + "\n");
        logOutput.setCaretPosition(logOutput.getDocument().getLength());
        api.logging().logToOutput("[Mobile Proxy] " + message);
    }

    // Emulator Management Methods

    private void refreshEmulatorList() {
        log("Refreshing emulator list...");
        emulatorListModel.clear();

        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() {
                return emulatorHelper.listAvailableEmulators();
            }

            @Override
            protected void done() {
                try {
                    List<String> emulators = get();
                    if (emulators.isEmpty()) {
                        emulatorListModel.addElement("No AVDs found");
                        log("✗ No emulators found. Create AVDs in Android Studio.");
                    } else {
                        for (String emu : emulators) {
                            emulatorListModel.addElement(emu);
                        }
                        log("✓ Found " + emulators.size() + " AVD(s)");
                    }
                } catch (Exception e) {
                    log("✗ Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void refreshRunningEmulators() {
        log("Checking running emulators...");

        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() {
                return emulatorHelper.getRunningEmulators();
            }

            @Override
            protected void done() {
                try {
                    List<String> running = get();
                    if (running.isEmpty()) {
                        runningEmulatorsArea.setText("No emulators running");
                        log("No emulators currently running");
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < running.size(); i++) {
                            sb.append((i + 1)).append(". ").append(running.get(i)).append("\n");
                        }
                        runningEmulatorsArea.setText(sb.toString());
                        log("✓ " + running.size() + " emulator(s) running");
                    }
                } catch (Exception e) {
                    runningEmulatorsArea.setText("Error: " + e.getMessage());
                    log("✗ Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void startSelectedEmulator() {
        String selected = emulatorList.getSelectedValue();

        if (selected == null || selected.equals("No AVDs found")) {
            JOptionPane.showMessageDialog(this,
                "Please select an emulator to start",
                "No Selection",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean writableSystem = writableSystemCheck.isSelected();
        String dnsServer = dnsServerField.getText().trim();

        log("Starting emulator: " + selected);
        log("Options: writable-system=" + writableSystem + ", dns=" + dnsServer);

        SwingWorker<Process, Void> worker = new SwingWorker<>() {
            @Override
            protected Process doInBackground() {
                return emulatorHelper.startEmulator(selected, writableSystem, dnsServer);
            }

            @Override
            protected void done() {
                try {
                    Process process = get();
                    if (process != null) {
                        runningEmulatorProcesses.put(selected, process);
                        log("✓ Emulator started: " + selected);
                        log("⏳ Waiting for emulator to boot (this may take 1-2 minutes)...");

                        JOptionPane.showMessageDialog(MobileProxyPanel.this,
                            "Emulator '" + selected + "' is starting!\n\n" +
                            "Please wait 1-2 minutes for it to fully boot.\n" +
                            "Click 'Refresh Running' to check status.",
                            "Emulator Starting",
                            JOptionPane.INFORMATION_MESSAGE);

                        // Auto-refresh after 30 seconds
                        Timer timer = new Timer(30000, e -> refreshRunningEmulators());
                        timer.setRepeats(false);
                        timer.start();
                    } else {
                        log("✗ Failed to start emulator");
                        JOptionPane.showMessageDialog(MobileProxyPanel.this,
                            "Failed to start emulator.\nCheck Settings tab for emulator path.",
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    log("✗ Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void stopSelectedEmulator() {
        String selected = emulatorList.getSelectedValue();

        if (selected == null || selected.equals("No AVDs found")) {
            JOptionPane.showMessageDialog(this,
                "Please select an emulator to stop",
                "No Selection",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        Process process = runningEmulatorProcesses.get(selected);

        if (process == null || !process.isAlive()) {
            JOptionPane.showMessageDialog(this,
                "Emulator '" + selected + "' is not running from this extension.\n" +
                "To stop manually: Close the emulator window or use:\n" +
                "adb -s emulator-XXXX emu kill",
                "Not Running",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        log("Stopping emulator: " + selected);

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                return emulatorHelper.stopEmulator(process);
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        runningEmulatorProcesses.remove(selected);
                        log("✓ Emulator stopped: " + selected);

                        // Refresh running list
                        Timer timer = new Timer(2000, e -> refreshRunningEmulators());
                        timer.setRepeats(false);
                        timer.start();
                    } else {
                        log("✗ Failed to stop emulator");
                    }
                } catch (Exception e) {
                    log("✗ Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }
}
