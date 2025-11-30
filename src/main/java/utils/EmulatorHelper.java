package utils;

import burp.api.montoya.MontoyaApi;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class EmulatorHelper {
    private final MontoyaApi api;
    private static String EMULATOR_COMMAND = null;
    private static final String[] COMMON_EMULATOR_PATHS = {
        "emulator", // System PATH
        System.getProperty("user.home") + "/Library/Android/sdk/emulator/emulator", // macOS
        System.getProperty("user.home") + "/Android/Sdk/emulator/emulator", // Linux
        "C:\\Users\\" + System.getProperty("user.name") + "\\AppData\\Local\\Android\\Sdk\\emulator\\emulator.exe", // Windows
        System.getProperty("user.home") + "/AppData/Local/Android/Sdk/emulator/emulator.exe" // Windows alternative
    };

    public EmulatorHelper(MontoyaApi api) {
        this.api = api;
        if (EMULATOR_COMMAND == null) {
            EMULATOR_COMMAND = detectEmulatorPath();
        }
    }

    /**
     * Auto-detect emulator path
     */
    private String detectEmulatorPath() {
        api.logging().logToOutput("Auto-detecting Emulator location...");

        for (String path : COMMON_EMULATOR_PATHS) {
            File file = new File(path);
            if (file.exists() && file.canExecute()) {
                api.logging().logToOutput("✓ Emulator found at: " + path);
                return path;
            }
        }

        api.logging().logToError("⚠ Emulator not found automatically");
        return "emulator";
    }

    /**
     * Get current emulator path
     */
    public static String getEmulatorPath() {
        return EMULATOR_COMMAND;
    }

    /**
     * Manually set emulator path
     */
    public static void setEmulatorPath(String path) {
        EMULATOR_COMMAND = path;
    }

    /**
     * List all available AVDs (Android Virtual Devices)
     */
    public List<String> listAvailableEmulators() {
        List<String> emulators = new ArrayList<>();

        try {
            ProcessBuilder pb = new ProcessBuilder(EMULATOR_COMMAND, "-list-avds");
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    emulators.add(line);
                }
            }

            process.waitFor();
            reader.close();

            api.logging().logToOutput("Found " + emulators.size() + " AVD(s)");

        } catch (Exception e) {
            api.logging().logToError("Error listing emulators: " + e.getMessage());
        }

        return emulators;
    }

    /**
     * Start an emulator
     */
    public Process startEmulator(String avdName, boolean writableSystem, String dnsServer) {
        try {
            List<String> command = new ArrayList<>();
            command.add(EMULATOR_COMMAND);
            command.add("-avd");
            command.add(avdName);

            if (writableSystem) {
                command.add("-writable-system");
            }

            if (dnsServer != null && !dnsServer.isEmpty()) {
                command.add("-dns-server");
                command.add(dnsServer);
            }

            // Run in background
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            api.logging().logToOutput("Starting emulator: " + avdName);
            api.logging().logToOutput("Command: " + String.join(" ", command));

            Process process = pb.start();

            // Start a thread to read output
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("ERROR") || line.contains("WARNING")) {
                            api.logging().logToOutput("[Emulator] " + line);
                        }
                    }
                } catch (Exception e) {
                    // Process terminated
                }
            }).start();

            return process;

        } catch (Exception e) {
            api.logging().logToError("Error starting emulator: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if emulator is running
     */
    public boolean isEmulatorRunning(String avdName) {
        try {
            // Use adb to check for emulator devices
            String adbPath = ADBHelper.getAdbPath();
            if (adbPath == null) {
                return false;
            }

            ProcessBuilder pb = new ProcessBuilder(adbPath, "devices");
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean found = false;

            while ((line = reader.readLine()) != null) {
                if (line.contains("emulator-") && line.contains("device")) {
                    found = true;
                    break;
                }
            }

            process.waitFor();
            reader.close();

            return found;

        } catch (Exception e) {
            api.logging().logToError("Error checking emulator status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Stop emulator (kills the process)
     */
    public boolean stopEmulator(Process emulatorProcess) {
        if (emulatorProcess != null && emulatorProcess.isAlive()) {
            emulatorProcess.destroy();

            try {
                Thread.sleep(2000);
                if (emulatorProcess.isAlive()) {
                    emulatorProcess.destroyForcibly();
                }
                api.logging().logToOutput("Emulator stopped");
                return true;
            } catch (Exception e) {
                api.logging().logToError("Error stopping emulator: " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * Get running emulator devices from ADB
     */
    public List<String> getRunningEmulators() {
        List<String> emulators = new ArrayList<>();

        try {
            String adbPath = ADBHelper.getAdbPath();
            if (adbPath == null) {
                return emulators;
            }

            ProcessBuilder pb = new ProcessBuilder(adbPath, "devices");
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                line = line.trim();
                if (line.contains("emulator-") && !line.isEmpty()) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        emulators.add(parts[0] + " (" + parts[1] + ")");
                    }
                }
            }

            process.waitFor();
            reader.close();

        } catch (Exception e) {
            api.logging().logToError("Error getting running emulators: " + e.getMessage());
        }

        return emulators;
    }
}

