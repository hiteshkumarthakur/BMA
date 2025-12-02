package utils;

import burp.api.montoya.MontoyaApi;
import org.tukaani.xz.XZInputStream;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public class FridaHelper {
    private final MontoyaApi api;
    private final ADBHelper adbHelper;
    private static final String FRIDA_DOWNLOAD_BASE = "https://github.com/frida/frida/releases/download/";
    private static final String FRIDA_SERVER_DIR = "/data/local/tmp/";
    private String fridaServerPath = "/data/local/tmp/frida-server";
    private String currentVersion = null;
    private Path localFridaPath;
    private Path tempDir;

    public FridaHelper(MontoyaApi api) {
        this.api = api;
        this.adbHelper = new ADBHelper(api);
        // Clean up any old Frida downloads on initialization
        cleanupOldDownloads();
    }

    /**
     * Set the device ID to use for Frida operations
     */
    public void setSelectedDevice(String deviceId) {
        adbHelper.setSelectedDevice(deviceId);
    }

    /**
     * Get the currently selected device ID
     */
    public String getSelectedDevice() {
        return adbHelper.getSelectedDevice();
    }

    public boolean downloadFridaServer(String architecture, String version, Consumer<String> logger) {
        try {
            // Clean up previous download if exists
            cleanupCurrentDownload();

            if (version.equals("latest")) {
                version = getLatestFridaVersion();
                if (version == null) {
                    logger.accept("✗ Failed to get latest Frida version");
                    return false;
                }
                logger.accept("Latest version: " + version);
            }

            // Store current version
            currentVersion = version;

            // Set versioned Frida server path
            fridaServerPath = FRIDA_SERVER_DIR + "frida-server-" + version + "-android-" + architecture;

            String fileName = "frida-server-" + version + "-android-" + architecture + ".xz";
            String downloadUrl = FRIDA_DOWNLOAD_BASE + version + "/" + fileName;

            logger.accept("Downloading from: " + downloadUrl);

            tempDir = Files.createTempDirectory("frida");
            Path downloadPath = tempDir.resolve(fileName);

            URL url = new URL(downloadUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                logger.accept("✗ Download failed with status: " + responseCode);
                return false;
            }

            long fileSize = connection.getContentLengthLong();
            logger.accept("File size: " + (fileSize / 1024 / 1024) + " MB");

            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(downloadPath.toFile())) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalRead = 0;
                int lastPercent = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    if (fileSize > 0) {
                        int percent = (int) ((totalRead * 100) / fileSize);
                        if (percent != lastPercent && percent % 10 == 0) {
                            logger.accept("Downloaded: " + percent + "%");
                            lastPercent = percent;
                        }
                    }
                }
            }

            logger.accept("✓ Download complete, decompressing...");

            Path decompressedPath = tempDir.resolve("frida-server-" + version + "-android-" + architecture);
            decompressXZ(downloadPath, decompressedPath);

            localFridaPath = decompressedPath;

            logger.accept("✓ Decompression complete");
            logger.accept("Version: " + currentVersion);
            api.logging().logToOutput("Frida server downloaded to: " + localFridaPath);
            api.logging().logToOutput("Will be pushed as: " + fridaServerPath);

            return true;
        } catch (Exception e) {
            logger.accept("✗ Error: " + e.getMessage());
            api.logging().logToError("Error downloading Frida: " + e.getMessage());
            return false;
        }
    }

    private String getLatestFridaVersion() {
        try {
            URL url = new URL("https://api.github.com/repos/frida/frida/releases/latest");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            String json = response.toString();
            int tagNameIndex = json.indexOf("\"tag_name\"");
            if (tagNameIndex != -1) {
                int start = json.indexOf("\"", tagNameIndex + 11) + 1;
                int end = json.indexOf("\"", start);
                return json.substring(start, end);
            }
        } catch (Exception e) {
            api.logging().logToError("Error getting latest version: " + e.getMessage());
        }

        return null;
    }

    private void decompressXZ(Path input, Path output) throws IOException {
        try (FileInputStream fin = new FileInputStream(input.toFile());
             BufferedInputStream bin = new BufferedInputStream(fin);
             XZInputStream xzIn = new XZInputStream(bin);
             FileOutputStream out = new FileOutputStream(output.toFile())) {

            byte[] buffer = new byte[8192];
            int n;
            while ((n = xzIn.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        }
    }

    public boolean pushFridaToDevice() {
        if (localFridaPath == null || !Files.exists(localFridaPath)) {
            api.logging().logToError("Frida server not downloaded yet");
            return false;
        }

        try {
            // Push with versioned name
            String result = adbHelper.executeCommand("push", localFridaPath.toString(), fridaServerPath);
            api.logging().logToOutput("Push result: " + result);

            // Make executable
            adbHelper.executeCommand("shell", "chmod", "755", fridaServerPath);

            api.logging().logToOutput("Frida server pushed to: " + fridaServerPath);
            api.logging().logToOutput("Version: " + (currentVersion != null ? currentVersion : "unknown"));

            // Clean up temporary files after successful push
            cleanupCurrentDownload();
            api.logging().logToOutput("Cleaned up temporary download files");

            return true;
        } catch (Exception e) {
            api.logging().logToError("Error pushing Frida: " + e.getMessage());
            return false;
        }
    }

    public boolean startFridaServer() {
        try {
            // Check if already running
            String checkStatus = adbHelper.executeCommand("shell", "ps", "|", "grep", "frida-server");
            if (!checkStatus.trim().isEmpty() && !checkStatus.contains("grep")) {
                api.logging().logToOutput("Frida server is already running");
                return true;
            }

            // Stop any existing instances
            stopFridaServer();
            Thread.sleep(1000);

            api.logging().logToOutput("Starting Frida server: " + fridaServerPath);

            // Method 1: Try with nohup in background (no root)
            String result = adbHelper.executeCommand("shell", "nohup", fridaServerPath, ">/dev/null", "2>&1", "&");
            Thread.sleep(2000);

            // Check if running
            String status = adbHelper.executeCommand("shell", "ps", "|", "grep", "frida-server");

            if (status.trim().isEmpty() || !status.contains("frida-server") || status.contains("grep frida-server")) {
                // Method 2: Try with su -c (root)
                api.logging().logToOutput("Trying with root privileges...");

                result = adbHelper.executeCommand("shell", "su", "-c", fridaServerPath + " &");
                Thread.sleep(2000);

                // Check again
                status = adbHelper.executeCommand("shell", "ps", "|", "grep", "frida-server");

                if (status.trim().isEmpty() || !status.contains("frida-server") || status.contains("grep frida-server")) {
                    // Method 3: Try with su -c and nohup
                    api.logging().logToOutput("Trying alternate method...");

                    result = adbHelper.executeCommand("shell", "su", "-c", "nohup " + fridaServerPath + " >/dev/null 2>&1 &");
                    Thread.sleep(2000);

                    // Final check
                    status = adbHelper.executeCommand("shell", "ps", "|", "grep", "frida-server");

                    if (status.trim().isEmpty() || !status.contains("frida-server") || status.contains("grep frida-server")) {
                        api.logging().logToError("Failed to start Frida server. It may already be bound to port 27042.");
                        api.logging().logToError("Try stopping it first or check device logs");
                        return false;
                    }
                }

                api.logging().logToOutput("Frida server started with root privileges");
            } else {
                api.logging().logToOutput("Frida server started successfully");
            }

            if (currentVersion != null) {
                api.logging().logToOutput("Running version: " + currentVersion);
            }

            return true;
        } catch (Exception e) {
            api.logging().logToError("Error starting Frida: " + e.getMessage());
            return false;
        }
    }

    public boolean stopFridaServer() {
        try {
            // Method 1: Find all frida-server processes and kill by PID
            String psResult = adbHelper.executeCommand("shell", "ps", "-A", "|", "grep", "frida");

            if (!psResult.trim().isEmpty()) {
                String[] lines = psResult.split("\n");

                for (String line : lines) {
                    if (line.contains("frida-server") && !line.contains("grep")) {
                        // Extract PID (usually second column)
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 2) {
                            String pid = parts[1];

                            // Try with root first
                            String killResult = adbHelper.executeCommand("shell", "su", "-c", "kill -9 " + pid);

                            // If root fails, try without
                            if (killResult.contains("not found") || killResult.contains("Permission denied")) {
                                adbHelper.executeCommand("shell", "kill", "-9", pid);
                            }

                            api.logging().logToOutput("Killed frida-server process (PID: " + pid + ")");
                        }
                    }
                }
            }

            // Method 2: Fallback with killall
            adbHelper.executeCommand("shell", "su", "-c", "killall -9 frida-server");

            Thread.sleep(1000);

            // Verify all processes are killed
            String finalCheck = adbHelper.executeCommand("shell", "ps", "-A", "|", "grep", "frida");

            if (finalCheck.trim().isEmpty() || !finalCheck.contains("frida-server")) {
                api.logging().logToOutput("✓ All Frida server processes stopped");
                return true;
            } else {
                api.logging().logToError("Some Frida processes may still be running (zombie/defunct)");
                return false;
            }

        } catch (Exception e) {
            api.logging().logToError("Error stopping Frida: " + e.getMessage());
            return false;
        }
    }


    public String getFridaStatus() {
        try {
            String result = adbHelper.executeCommand("shell", "ps", "|", "grep", "frida-server");

            if (result.trim().isEmpty()) {
                return "Frida server is NOT running" +
                        (currentVersion != null ? "\nLast configured version: " + currentVersion : "");
            } else {
                String versionInfo = currentVersion != null ? "\nVersion: " + currentVersion : "";
                return "Frida server is RUNNING" + versionInfo + "\n\n" + result;
            }
        } catch (Exception e) {
            return "Error checking status: " + e.getMessage();
        }
    }

    /**
     * Clean up the current temporary download directory
     */
    private void cleanupCurrentDownload() {
        if (tempDir != null && Files.exists(tempDir)) {
            try {
                deleteDirectory(tempDir);
                api.logging().logToOutput("Cleaned up temporary directory: " + tempDir);
                tempDir = null;
                localFridaPath = null;
            } catch (IOException e) {
                api.logging().logToError("Failed to clean up temp directory: " + e.getMessage());
            }
        }
    }

    /**
     * Clean up all old Frida download directories in the system temp folder
     */
    private void cleanupOldDownloads() {
        try {
            Path tempRoot = Files.createTempDirectory("frida").getParent();

            if (tempRoot != null && Files.exists(tempRoot)) {
                Files.list(tempRoot)
                    .filter(path -> path.getFileName().toString().startsWith("frida"))
                    .filter(Files::isDirectory)
                    .forEach(path -> {
                        try {
                            deleteDirectory(path);
                            api.logging().logToOutput("Cleaned up old Frida directory: " + path.getFileName());
                        } catch (IOException e) {
                            api.logging().logToError("Failed to delete old directory: " + path + " - " + e.getMessage());
                        }
                    });
            }
        } catch (Exception e) {
            api.logging().logToError("Error during cleanup of old downloads: " + e.getMessage());
        }
    }

    /**
     * Recursively delete a directory and its contents
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                .sorted((a, b) -> b.compareTo(a)) // Reverse order to delete files before directories
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        api.logging().logToError("Failed to delete: " + path + " - " + e.getMessage());
                    }
                });
        }
    }
}

