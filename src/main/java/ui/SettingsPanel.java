package ui;

import burp.api.montoya.MontoyaApi;
import utils.ADBHelper;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class SettingsPanel extends JPanel {
    private final MontoyaApi api;
    private final JTextField adbPathField;
    private final JLabel statusLabel;

    public SettingsPanel(MontoyaApi api) {
        this.api = api;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel titleLabel = new JLabel("Settings & Configuration");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        add(titleLabel, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));

        // ADB Configuration Panel
        JPanel adbPanel = new JPanel(new GridBagLayout());
        adbPanel.setBorder(BorderFactory.createTitledBorder("ADB Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Current ADB Path Display
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        adbPanel.add(new JLabel("ADB Path:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        adbPathField = new JTextField(ADBHelper.getAdbPath() != null ? ADBHelper.getAdbPath() : "", 40);
        adbPanel.add(adbPathField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.0;
        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> browseForAdb());
        adbPanel.add(browseBtn, gbc);

        // Status Label
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        statusLabel = new JLabel();
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        adbPanel.add(statusLabel, gbc);

        // Button Panel
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton testBtn = new JButton("Test ADB Connection");
        testBtn.addActionListener(e -> testAdbConnection());
        buttonPanel.add(testBtn);

        JButton saveBtn = new JButton("Save ADB Path");
        saveBtn.addActionListener(e -> saveAdbPath());
        buttonPanel.add(saveBtn);

        JButton autoDetectBtn = new JButton("Auto-Detect");
        autoDetectBtn.addActionListener(e -> autoDetectAdb());
        buttonPanel.add(autoDetectBtn);

        adbPanel.add(buttonPanel, gbc);

        // Info Panel
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JTextArea infoText = new JTextArea();
        infoText.setEditable(false);
        infoText.setBackground(getBackground());
        infoText.setFont(new Font("Monospaced", Font.PLAIN, 11));
        infoText.setText(
            "Common ADB Locations:\n\n" +
            "macOS:\n" +
            "  ~/Library/Android/sdk/platform-tools/adb\n" +
            "  /usr/local/bin/adb\n\n" +
            "Linux:\n" +
            "  ~/Android/Sdk/platform-tools/adb\n" +
            "  /usr/bin/adb\n\n" +
            "Windows:\n" +
            "  C:\\Users\\YourName\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe\n\n" +
            "To install ADB:\n" +
            "  macOS: brew install android-platform-tools\n" +
            "  Linux: sudo apt-get install adb\n" +
            "  Windows: Download Android SDK Platform Tools"
        );

        infoPanel.add(infoText, BorderLayout.CENTER);
        adbPanel.add(infoPanel, gbc);

        contentPanel.add(adbPanel, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        // Initial status check
        updateStatus();
    }

    private void browseForAdb() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select ADB Executable");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        // Set initial directory to common locations
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home") + "/Library/Android/sdk/platform-tools"));
        } else if (os.contains("win")) {
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home") + "/AppData/Local/Android/Sdk/platform-tools"));
        } else {
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home") + "/Android/Sdk/platform-tools"));
        }

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            adbPathField.setText(selectedFile.getAbsolutePath());
        }
    }

    private void testAdbConnection() {
        String path = adbPathField.getText().trim();

        if (path.isEmpty()) {
            setStatus("⚠ Please enter ADB path", Color.ORANGE);
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(path, "version");
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                setStatus("✓ ADB is working correctly!", Color.GREEN);

                // Also test device connection
                pb = new ProcessBuilder(path, "devices");
                process = pb.start();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));

                int deviceCount = 0;
                String line;
                boolean firstLine = true;
                while ((line = reader.readLine()) != null) {
                    if (firstLine) {
                        firstLine = false;
                        continue;
                    }
                    if (!line.trim().isEmpty() && line.contains("\t")) {
                        deviceCount++;
                    }
                }
                reader.close();

                if (deviceCount > 0) {
                    setStatus("✓ ADB working! " + deviceCount + " device(s) connected", Color.GREEN);
                } else {
                    setStatus("✓ ADB working! No devices connected", new Color(34, 139, 34));
                }

            } else {
                setStatus("✗ ADB test failed", Color.RED);
            }
        } catch (Exception e) {
            setStatus("✗ Error: " + e.getMessage(), Color.RED);
            api.logging().logToError("ADB test error: " + e.getMessage());
        }
    }

    private void saveAdbPath() {
        String path = adbPathField.getText().trim();

        if (path.isEmpty()) {
            setStatus("⚠ Please enter ADB path", Color.ORANGE);
            return;
        }

        ADBHelper.setAdbPath(path);
        api.logging().logToOutput("ADB path set to: " + path);
        setStatus("✓ ADB path saved: " + path, Color.GREEN);

        JOptionPane.showMessageDialog(this,
            "ADB path saved successfully!\n\n" +
            "Path: " + path + "\n\n" +
            "Click 'Test ADB Connection' to verify.",
            "Success",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private void autoDetectAdb() {
        setStatus("🔍 Auto-detecting ADB...", Color.BLUE);

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                // Create a temporary ADBHelper to trigger auto-detection
                ADBHelper tempHelper = new ADBHelper(api);
                return ADBHelper.getAdbPath();
            }

            @Override
            protected void done() {
                try {
                    String detectedPath = get();
                    if (detectedPath != null && !detectedPath.equals("adb")) {
                        adbPathField.setText(detectedPath);
                        setStatus("✓ ADB auto-detected at: " + detectedPath, Color.GREEN);

                        // Auto-test the detected path
                        testAdbConnection();
                    } else {
                        setStatus("⚠ ADB not found. Please set manually.", Color.ORANGE);
                    }
                } catch (Exception e) {
                    setStatus("✗ Auto-detection failed", Color.RED);
                }
            }
        };
        worker.execute();
    }

    private void updateStatus() {
        String currentPath = ADBHelper.getAdbPath();
        if (currentPath != null && !currentPath.isEmpty()) {
            setStatus("Current: " + currentPath, Color.YELLOW);
        } else {
            setStatus("ADB path not configured", Color.ORANGE);
        }
    }

    private void setStatus(String message, Color color) {
        statusLabel.setText(message);
        statusLabel.setForeground(color);
    }
}

