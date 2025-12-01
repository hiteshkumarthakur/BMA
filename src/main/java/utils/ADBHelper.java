package utils;

import burp.api.montoya.MontoyaApi;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ADBHelper {
    private final MontoyaApi api;
    private static String ADB_COMMAND = null;
    private String selectedDeviceId = null; // Currently selected device for operations
    private static final String[] COMMON_ADB_PATHS = {
            "adb", // System PATH
            "/usr/local/bin/adb",
            "/usr/bin/adb",
            System.getProperty("user.home") + "/Library/Android/sdk/platform-tools/adb", // macOS
            System.getProperty("user.home") + "/Android/Sdk/platform-tools/adb", // Linux
            "C:\\Users\\" + System.getProperty("user.name") + "\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe", // Windows
            System.getProperty("user.home") + "/AppData/Local/Android/Sdk/platform-tools/adb.exe" // Windows alternative
    };

    public ADBHelper(MontoyaApi api) {
        this.api = api;
        if (ADB_COMMAND == null) {
            ADB_COMMAND = detectAdbPath();
        }
    }

    /**
     * Set the device ID to use for subsequent operations
     */
    public void setSelectedDevice(String deviceId) {
        this.selectedDeviceId = deviceId;
        api.logging().logToOutput("Selected device: " + (deviceId != null ? deviceId : "default"));
    }

    /**
     * Get the currently selected device ID
     */
    public String getSelectedDevice() {
        return selectedDeviceId;
    }

    /**
     * Auto-detect ADB path by checking common locations
     */
    private String detectAdbPath() {
        api.logging().logToOutput("Auto-detecting ADB location...");

        // Try common paths
        for (String path : COMMON_ADB_PATHS) {
            if (testAdbPath(path)) {
                api.logging().logToOutput("✓ ADB found at: " + path);
                return path;
            }
        }

        // Try 'which adb' on Unix-like systems
        try {
            String[] command = System.getProperty("os.name").toLowerCase().contains("win")
                    ? new String[]{"where", "adb"}
                    : new String[]{"which", "adb"};

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String path = reader.readLine();
            reader.close();
            process.waitFor();

            if (path != null && !path.isEmpty() && testAdbPath(path)) {
                api.logging().logToOutput("✓ ADB found via system command: " + path);
                return path;
            }
        } catch (Exception e) {
            // Ignore and continue
        }

        // Fallback to default
        api.logging().logToError("⚠ ADB not found automatically. Please set the path manually in Settings tab.");
        return "adb"; // Try system PATH as last resort
    }

    /**
     * Test if ADB exists and works at given path
     */
    private boolean testAdbPath(String path) {
        try {
            ProcessBuilder pb = new ProcessBuilder(path, "version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Manually set ADB path (called from UI)
     */
    public static void setAdbPath(String path) {
        ADB_COMMAND = path;
    }

    /**
     * Get current ADB path
     */
    public static String getAdbPath() {
        return ADB_COMMAND;
    }

    /**
     * Verify if current ADB path is valid
     */
    public boolean isAdbAvailable() {
        if (ADB_COMMAND == null) {
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(ADB_COMMAND, "version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> getConnectedDevices() {
        List<String> devices = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(ADB_COMMAND, "devices");
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
                if (!line.isEmpty() && line.contains("\t")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2 && parts[1].equals("device")) {
                        // Only return device ID
                        devices.add(parts[0]);
                    }
                }
            }

            process.waitFor();
            reader.close();
        } catch (Exception e) {
            api.logging().logToError("Error getting devices: " + e.getMessage());
        }

        return devices;
    }

    /**
     * Get detailed device information including status
     */
    public List<String> getConnectedDevicesWithStatus() {
        List<String> devices = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(ADB_COMMAND, "devices");
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
                if (!line.isEmpty() && line.contains("\t")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        devices.add(parts[0] + " (" + parts[1] + ")");
                    }
                }
            }

            process.waitFor();
            reader.close();
        } catch (Exception e) {
            api.logging().logToError("Error getting devices: " + e.getMessage());
        }

        return devices;
    }

    public boolean setProxy(String host, String port) {
        try {
            // Build command with device selection if specified
            ProcessBuilder pb;
            if (selectedDeviceId != null) {
                pb = new ProcessBuilder(
                        ADB_COMMAND, "-s", selectedDeviceId, "shell", "settings", "put", "global", "http_proxy", host + ":" + port
                );
            } else {
                pb = new ProcessBuilder(
                        ADB_COMMAND, "shell", "settings", "put", "global", "http_proxy", host + ":" + port
                );
            }

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                String deviceInfo = selectedDeviceId != null ? " on device " + selectedDeviceId : "";
                api.logging().logToOutput("Proxy set to " + host + ":" + port + deviceInfo);

                // Add reverse port forwarding
                ProcessBuilder reversePb;
                if (selectedDeviceId != null) {
                    reversePb = new ProcessBuilder(
                            ADB_COMMAND, "-s", selectedDeviceId, "reverse", "tcp:" + port, "tcp:" + port
                    );
                } else {
                    reversePb = new ProcessBuilder(
                            ADB_COMMAND, "reverse", "tcp:" + port, "tcp:" + port
                    );
                }

                Process reverseProcess = reversePb.start();
                int reverseExitCode = reverseProcess.waitFor();

                if (reverseExitCode == 0) {
                    api.logging().logToOutput("Port forwarding set: tcp:" + port + " -> tcp:" + port + deviceInfo);
                } else {
                    api.logging().logToError("Failed to set port forwarding" + deviceInfo);
                }

                return true;
            }
        } catch (Exception e) {
            api.logging().logToError("Error setting proxy: " + e.getMessage());
        }

        return false;
    }

    public boolean clearProxy() {
        try {
            ProcessBuilder pb;
            if (selectedDeviceId != null) {
                pb = new ProcessBuilder(
                        ADB_COMMAND, "-s", selectedDeviceId, "shell", "settings", "put", "global", "http_proxy", ":0"
                );
            } else {
                pb = new ProcessBuilder(
                        ADB_COMMAND, "shell", "settings", "put", "global", "http_proxy", ":0"
                );
            }

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                String deviceInfo = selectedDeviceId != null ? " on device " + selectedDeviceId : "";
                api.logging().logToOutput("Proxy cleared" + deviceInfo);
                return true;
            }
        } catch (Exception e) {
            api.logging().logToError("Error clearing proxy: " + e.getMessage());
        }

        return false;
    }

    public String getProxySettings() {
        try {
            ProcessBuilder pb;
            if (selectedDeviceId != null) {
                pb = new ProcessBuilder(
                        ADB_COMMAND, "-s", selectedDeviceId, "shell", "settings", "get", "global", "http_proxy"
                );
            } else {
                pb = new ProcessBuilder(
                        ADB_COMMAND, "shell", "settings", "get", "global", "http_proxy"
                );
            }

            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = reader.readLine();
            process.waitFor();
            reader.close();

            if (result == null || result.trim().isEmpty() || result.equals("null") || result.equals(":0")) {
                return "No proxy set";
            }

            return result.trim();
        } catch (Exception e) {
            api.logging().logToError("Error getting proxy: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    public String getDeviceArchitecture() {
        try {
            ProcessBuilder pb;
            if (selectedDeviceId != null) {
                pb = new ProcessBuilder(ADB_COMMAND, "-s", selectedDeviceId, "shell", "getprop", "ro.product.cpu.abi");
            } else {
                pb = new ProcessBuilder(ADB_COMMAND, "shell", "getprop", "ro.product.cpu.abi");
            }

            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = reader.readLine();
            process.waitFor();
            reader.close();

            if (result != null) {
                result = result.trim();
                if (result.startsWith("arm64")) {
                    return "arm64";
                } else if (result.startsWith("armeabi")) {
                    return "arm";
                } else if (result.startsWith("x86_64")) {
                    return "x86_64";
                } else if (result.startsWith("x86")) {
                    return "x86";
                }
                return result;
            }
        } catch (Exception e) {
            api.logging().logToError("Error getting architecture: " + e.getMessage());
        }

        return null;
    }

    public String executeCommand(String... command) {
        try {
            // Build full command with optional device selector
            String[] fullCommand;
            if (selectedDeviceId != null) {
                fullCommand = new String[command.length + 3];
                fullCommand[0] = ADB_COMMAND;
                fullCommand[1] = "-s";
                fullCommand[2] = selectedDeviceId;
                System.arraycopy(command, 0, fullCommand, 3, command.length);
            } else {
                fullCommand = new String[command.length + 1];
                fullCommand[0] = ADB_COMMAND;
                System.arraycopy(command, 0, fullCommand, 1, command.length);
            }

            ProcessBuilder pb = new ProcessBuilder(fullCommand);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            while ((line = errorReader.readLine()) != null) {
                output.append(line).append("\n");
            }

            process.waitFor();
            reader.close();
            errorReader.close();

            return output.toString();
        } catch (Exception e) {
            api.logging().logToError("Error executing command: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}

