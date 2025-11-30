package ui;

import burp.api.montoya.MontoyaApi;
import utils.ADBHelper;
import utils.FridaHelper;

import javax.swing.*;
import java.awt.*;

public class FridaSetupPanel extends JPanel {
    private final MontoyaApi api;
    private final JComboBox<String> architectureCombo;
    private final JTextField customVersionField;
    private final JTextArea logOutput;
    private final FridaHelper fridaHelper;
    private final ADBHelper adbHelper;

    public FridaSetupPanel(MontoyaApi api) {
        this.api = api;
        this.fridaHelper = new FridaHelper(api);
        this.adbHelper = new ADBHelper(api);

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel titleLabel = new JLabel("Frida Server Setup");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        add(titleLabel, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));

        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        configPanel.add(new JLabel("Architecture:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        String[] architectures = {"arm64", "arm", "x86", "x86_64"};
        architectureCombo = new JComboBox<>(architectures);
        configPanel.add(architectureCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        configPanel.add(new JLabel("Version (optional):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        customVersionField = new JTextField("latest", 20);
        configPanel.add(customVersionField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        JLabel infoLabel = new JLabel("<html><i>Leave 'latest' to download the newest version</i></html>");
        infoLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        configPanel.add(infoLabel, gbc);

        contentPanel.add(configPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        buttonPanel.setBorder(BorderFactory.createTitledBorder("Actions"));

        JButton detectArchBtn = new JButton("Auto-Detect Architecture");
        detectArchBtn.addActionListener(e -> detectArchitecture());
        buttonPanel.add(detectArchBtn);

        JButton downloadBtn = new JButton("Download Frida Server");
        downloadBtn.addActionListener(e -> downloadFrida());
        buttonPanel.add(downloadBtn);

        JButton pushBtn = new JButton("Push to Device");
        pushBtn.addActionListener(e -> pushFrida());
        buttonPanel.add(pushBtn);

        JButton startBtn = new JButton("Start Frida Server");
        startBtn.addActionListener(e -> startFrida());
        buttonPanel.add(startBtn);

        JButton stopBtn = new JButton("Stop Frida Server");
        stopBtn.addActionListener(e -> stopFrida());
        buttonPanel.add(stopBtn);

        JButton checkStatusBtn = new JButton("Check Status");
        checkStatusBtn.addActionListener(e -> checkFridaStatus());
        buttonPanel.add(checkStatusBtn);

        JButton fullSetupBtn = new JButton("⚡ Full Auto Setup");
        fullSetupBtn.setFont(new Font("Arial", Font.BOLD, 12));
        fullSetupBtn.setBackground(new Color(76, 175, 80));
        fullSetupBtn.setForeground(Color.WHITE);
        fullSetupBtn.setOpaque(true);
        fullSetupBtn.addActionListener(e -> fullAutoSetup());

        JPanel fullSetupPanel = new JPanel(new BorderLayout());
        fullSetupPanel.add(fullSetupBtn, BorderLayout.CENTER);
        fullSetupPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel actionsPanel = new JPanel(new BorderLayout());
        actionsPanel.add(buttonPanel, BorderLayout.CENTER);
        actionsPanel.add(fullSetupPanel, BorderLayout.SOUTH);

        contentPanel.add(actionsPanel, BorderLayout.CENTER);

        JPanel logPanel = new JPanel(new BorderLayout(5, 5));
        logPanel.setBorder(BorderFactory.createTitledBorder("Log"));

        logOutput = new JTextArea(10, 50);
        logOutput.setEditable(false);
        logOutput.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(logOutput);
        logPanel.add(logScroll, BorderLayout.CENTER);

        JButton clearLogBtn = new JButton("Clear Log");
        clearLogBtn.addActionListener(e -> logOutput.setText(""));
        logPanel.add(clearLogBtn, BorderLayout.SOUTH);

        contentPanel.add(logPanel, BorderLayout.SOUTH);

        add(contentPanel, BorderLayout.CENTER);
    }

    private void detectArchitecture() {
        log("Detecting device architecture...");

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return adbHelper.getDeviceArchitecture();
            }

            @Override
            protected void done() {
                try {
                    String arch = get();
                    if (arch != null && !arch.isEmpty()) {
                        architectureCombo.setSelectedItem(arch);
                        log("✓ Detected architecture: " + arch);
                        JOptionPane.showMessageDialog(FridaSetupPanel.this,
                            "Detected architecture: " + arch,
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        log("✗ Failed to detect architecture");
                        JOptionPane.showMessageDialog(FridaSetupPanel.this,
                            "Failed to detect architecture",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    log("✗ Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void downloadFrida() {
        String arch = (String) architectureCombo.getSelectedItem();
        String version = customVersionField.getText().trim();

        log("Downloading Frida server for " + arch + " (version: " + version + ")...");

        SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() {
                return fridaHelper.downloadFridaServer(arch, version, this::publish);
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    log(message);
                }
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        log("✓ Download completed successfully");
                        JOptionPane.showMessageDialog(FridaSetupPanel.this,
                            "Frida server downloaded successfully",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        log("✗ Download failed");
                        JOptionPane.showMessageDialog(FridaSetupPanel.this,
                            "Failed to download Frida server",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    log("✗ Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void pushFrida() {
        log("Pushing Frida server to device...");

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return fridaHelper.pushFridaToDevice();
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        log("✓ Frida server pushed successfully");
                        JOptionPane.showMessageDialog(FridaSetupPanel.this,
                            "Frida server pushed to device",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        log("✗ Failed to push Frida server");
                        JOptionPane.showMessageDialog(FridaSetupPanel.this,
                            "Failed to push Frida server to device",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    log("✗ Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void startFrida() {
        log("Starting Frida server...");

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return fridaHelper.startFridaServer();
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        log("✓ Frida server started successfully");
                        JOptionPane.showMessageDialog(FridaSetupPanel.this,
                            "Frida server is now running",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        log("✗ Failed to start Frida server");
                        JOptionPane.showMessageDialog(FridaSetupPanel.this,
                            "Failed to start Frida server",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    log("✗ Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void stopFrida() {
        log("Stopping Frida server...");

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return fridaHelper.stopFridaServer();
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        log("✓ Frida server stopped");
                        JOptionPane.showMessageDialog(FridaSetupPanel.this,
                            "Frida server stopped",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        log("✗ Failed to stop Frida server");
                    }
                } catch (Exception e) {
                    log("✗ Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void checkFridaStatus() {
        log("Checking Frida server status...");

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return fridaHelper.getFridaStatus();
            }

            @Override
            protected void done() {
                try {
                    String status = get();
                    log("Status: " + status);
                    JOptionPane.showMessageDialog(FridaSetupPanel.this,
                        status,
                        "Frida Status", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    log("✗ Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void fullAutoSetup() {
        int result = JOptionPane.showConfirmDialog(this,
            "This will:\n1. Detect device architecture\n2. Download latest Frida server\n3. Push to device\n4. Start Frida server\n\nContinue?",
            "Full Auto Setup",
            JOptionPane.YES_NO_OPTION);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        log("========== Starting Full Auto Setup ==========");

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                publish("Step 1/4: Detecting architecture...");
                String arch = adbHelper.getDeviceArchitecture();
                if (arch == null || arch.isEmpty()) {
                    publish("✗ Failed to detect architecture");
                    return null;
                }
                publish("✓ Architecture: " + arch);
                architectureCombo.setSelectedItem(arch);

                publish("Step 2/4: Downloading Frida server...");
                boolean downloaded = fridaHelper.downloadFridaServer(arch, "latest", this::publish);
                if (!downloaded) {
                    publish("✗ Failed to download Frida");
                    return null;
                }
                publish("✓ Download completed");

                publish("Step 3/4: Pushing to device...");
                boolean pushed = fridaHelper.pushFridaToDevice();
                if (!pushed) {
                    publish("✗ Failed to push Frida");
                    return null;
                }
                publish("✓ Pushed successfully");

                publish("Step 4/4: Starting Frida server...");
                boolean started = fridaHelper.startFridaServer();
                if (!started) {
                    publish("✗ Failed to start Frida");
                    return null;
                }
                publish("✓ Frida server started");

                publish("========== Setup Complete! ==========");
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    log(message);
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(FridaSetupPanel.this,
                        "Full setup completed successfully!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    log("✗ Error during setup: " + e.getMessage());
                    JOptionPane.showMessageDialog(FridaSetupPanel.this,
                        "Setup failed: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void log(String message) {
        logOutput.append(message + "\n");
        logOutput.setCaretPosition(logOutput.getDocument().getLength());
        api.logging().logToOutput("[Frida Setup] " + message);
    }
}

