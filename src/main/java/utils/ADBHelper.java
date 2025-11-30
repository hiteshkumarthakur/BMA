package utils;

import burp.api.montoya.MontoyaApi;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ADBHelper {
    private final MontoyaApi api;
    private static String ADB_COMMAND = null;
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
            // Set global proxy settings
            ProcessBuilder pb = new ProcessBuilder(
                    ADB_COMMAND, "shell", "settings", "put", "global", "http_proxy", host + ":" + port
            );

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                api.logging().logToOutput("Proxy set to " + host + ":" + port);

                // Add reverse port forwarding
                ProcessBuilder reversePb = new ProcessBuilder(
                        ADB_COMMAND, "reverse", "tcp:" + port, "tcp:" + port
                );

                Process reverseProcess = reversePb.start();
                int reverseExitCode = reverseProcess.waitFor();

                if (reverseExitCode == 0) {
                    api.logging().logToOutput("Port forwarding set: tcp:" + port + " -> tcp:" + port);
                } else {
                    api.logging().logToError("Failed to set port forwarding");
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
            ProcessBuilder pb = new ProcessBuilder(
                ADB_COMMAND, "shell", "settings", "put", "global", "http_proxy", ":0"
            );
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                api.logging().logToOutput("Proxy cleared");
                return true;
            }
        } catch (Exception e) {
            api.logging().logToError("Error clearing proxy: " + e.getMessage());
        }

        return false;
    }

    public String getProxySettings() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                ADB_COMMAND, "shell", "settings", "get", "global", "http_proxy"
            );
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
            ProcessBuilder pb = new ProcessBuilder(ADB_COMMAND, "shell", "getprop", "ro.product.cpu.abi");
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
            String[] fullCommand = new String[command.length + 1];
            fullCommand[0] = ADB_COMMAND;
            System.arraycopy(command, 0, fullCommand, 1, command.length);

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

