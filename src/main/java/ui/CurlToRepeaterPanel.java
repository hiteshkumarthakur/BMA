package ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import utils.CurlParser;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class CurlToRepeaterPanel extends JPanel {
    private final MontoyaApi api;
    private final JTextArea curlInput;
    private final JTextArea previewOutput;
    private final JTextArea rawTextArea;
    private final JTextArea headersTextArea;
    private final JTextArea bodyTextArea;
    private final JTextArea logOutput;
    private HttpRequest lastParsedRequest;

    public CurlToRepeaterPanel(MontoyaApi api) {
        this.api = api;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel titleLabel = new JLabel("Curl to Repeater Converter");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        add(titleLabel, BorderLayout.NORTH);

        // Main split pane - divides input and preview/log
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplitPane.setResizeWeight(0.35);

        // Top panel - Input
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.add(new JLabel("Paste your curl command:"), BorderLayout.NORTH);

        curlInput = new JTextArea(10, 50);
        curlInput.setLineWrap(true);
        curlInput.setWrapStyleWord(true);
        curlInput.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // Initialize these before using in lambdas
        previewOutput = new JTextArea(15, 50);
        previewOutput.setEditable(false);
        previewOutput.setFont(new Font("Monospaced", Font.PLAIN, 11));
        // Don't set background - let it use default theme colors

        logOutput = new JTextArea(5, 50);
        logOutput.setEditable(false);
        logOutput.setFont(new Font("Monospaced", Font.PLAIN, 11));

        // Initialize inspector text areas
        rawTextArea = new JTextArea();
        rawTextArea.setEditable(false);
        rawTextArea.setFont(new Font("Monospaced", Font.PLAIN, 11));

        headersTextArea = new JTextArea();
        headersTextArea.setEditable(false);
        headersTextArea.setFont(new Font("Monospaced", Font.PLAIN, 11));

        bodyTextArea = new JTextArea();
        bodyTextArea.setEditable(false);
        bodyTextArea.setFont(new Font("Monospaced", Font.PLAIN, 11));

        // Add real-time parsing listener
        curlInput.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                parseAndPreviewSilently();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                parseAndPreviewSilently();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                parseAndPreviewSilently();
            }
        });

        JScrollPane inputScroll = new JScrollPane(curlInput);
        inputPanel.add(inputScroll, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton sendToRepeaterBtn = new JButton("Send to Repeater");
        sendToRepeaterBtn.addActionListener(e -> sendToRepeater());

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
            curlInput.setText("");
            previewOutput.setText("");
            rawTextArea.setText("");
            headersTextArea.setText("");
            bodyTextArea.setText("");
            logOutput.setText("");
            lastParsedRequest = null;
        });

        buttonPanel.add(sendToRepeaterBtn);
        buttonPanel.add(clearBtn);
        inputPanel.add(buttonPanel, BorderLayout.SOUTH);

        mainSplitPane.setTopComponent(inputPanel);

        // Bottom split pane - divides preview and log
        JSplitPane bottomSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        bottomSplitPane.setResizeWeight(0.7);

        // Preview panel with tabs (Inspector-like)
        JPanel previewPanel = new JPanel(new BorderLayout(5, 5));
        previewPanel.add(new JLabel("Request Inspector:"), BorderLayout.NORTH);

        JTabbedPane inspectorTabs = new JTabbedPane();

        // Raw tab
        JScrollPane rawScroll = new JScrollPane(rawTextArea);
        inspectorTabs.addTab("Raw", rawScroll);

        // Headers tab
        JScrollPane headersScroll = new JScrollPane(headersTextArea);
        inspectorTabs.addTab("Headers", headersScroll);

        // Body tab
        JScrollPane bodyScroll = new JScrollPane(bodyTextArea);
        inspectorTabs.addTab("Body", bodyScroll);

        previewPanel.add(inspectorTabs, BorderLayout.CENTER);

        bottomSplitPane.setTopComponent(previewPanel);

        // Log panel
        JPanel logPanel = new JPanel(new BorderLayout(5, 5));
        logPanel.add(new JLabel("Log:"), BorderLayout.NORTH);

        JScrollPane logScroll = new JScrollPane(logOutput);
        logPanel.add(logScroll, BorderLayout.CENTER);

        bottomSplitPane.setBottomComponent(logPanel);

        mainSplitPane.setBottomComponent(bottomSplitPane);

        add(mainSplitPane, BorderLayout.CENTER);
    }

    private void parseAndPreviewSilently() {
        String curlCommand = curlInput.getText().trim();

        if (curlCommand.isEmpty()) {
            previewOutput.setText("");
            rawTextArea.setText("");
            headersTextArea.setText("");
            bodyTextArea.setText("");
            lastParsedRequest = null;
            return;
        }

        try {
            CurlParser parser = new CurlParser();
            lastParsedRequest = parser.parse(curlCommand, api);

            if (lastParsedRequest != null) {
                // Update preview output
                String preview = formatRequestForDisplay(lastParsedRequest);
                previewOutput.setText(preview);

                // Update inspector tabs
                updateInspectorTabs(lastParsedRequest);
            } else {
                previewOutput.setText("");
                rawTextArea.setText("");
                headersTextArea.setText("");
                bodyTextArea.setText("");
            }
        } catch (Exception e) {
            String errorMsg = "Error parsing cURL command:\n" + e.getMessage();
            previewOutput.setText(errorMsg);
            rawTextArea.setText(errorMsg);
            headersTextArea.setText("");
            bodyTextArea.setText("");
            lastParsedRequest = null;
        }
    }

    private void parseAndPreview() {
        String curlCommand = curlInput.getText().trim();

        if (curlCommand.isEmpty()) {
            log("Error: Please enter a curl command");
            JOptionPane.showMessageDialog(this, "Please enter a curl command", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            log("Parsing curl command...");
            CurlParser parser = new CurlParser();
            lastParsedRequest = parser.parse(curlCommand, api);

            if (lastParsedRequest != null) {
                // Display the request in Burp format
                String preview = formatRequestForDisplay(lastParsedRequest);
                previewOutput.setText(preview);

                // Update inspector tabs
                updateInspectorTabs(lastParsedRequest);

                log("✓ Successfully parsed curl command");
                log("Ready to send to Repeater");
            } else {
                log("✗ Failed to parse curl command");
                previewOutput.setText("");
                rawTextArea.setText("");
                headersTextArea.setText("");
                bodyTextArea.setText("");
            }
        } catch (Exception e) {
            log("✗ Error: " + e.getMessage());
            api.logging().logToError("Error parsing curl: " + e.getMessage());
            String errorMsg = "Error parsing cURL command:\n" + e.getMessage();
            previewOutput.setText(errorMsg);
            rawTextArea.setText(errorMsg);
            headersTextArea.setText("");
            bodyTextArea.setText("");
            lastParsedRequest = null;
        }
    }

    private void updateInspectorTabs(HttpRequest request) {
        // Raw tab - full request
        rawTextArea.setText(request.toString());

        // Headers tab - just headers
        StringBuilder headersBuilder = new StringBuilder();
        headersBuilder.append("Target: ").append(request.httpService().host())
                     .append(":").append(request.httpService().port())
                     .append(" [").append(request.httpService().secure() ? "HTTPS" : "HTTP").append("]\n\n");

        request.headers().forEach(header -> {
            headersBuilder.append(header.name()).append(": ").append(header.value()).append("\n");
        });
        headersTextArea.setText(headersBuilder.toString());

        // Body tab - just body
        String body = request.bodyToString();
        if (body != null && !body.isEmpty()) {
            bodyTextArea.setText(body);
        } else {
            bodyTextArea.setText("[No body]");
        }
    }

    private String formatRequestForDisplay(HttpRequest request) {
        StringBuilder display = new StringBuilder();

        // Add service info
        display.append("═══════════════════════════════════════════════════════════════\n");
        display.append("Target: ").append(request.httpService().host());
        display.append(":").append(request.httpService().port());
        display.append(" [").append(request.httpService().secure() ? "HTTPS" : "HTTP").append("]\n");
        display.append("═══════════════════════════════════════════════════════════════\n\n");

        // Add the raw request
        display.append(request.toString());

        return display.toString();
    }

    private void sendToRepeater() {
        // If not already parsed, parse first
        if (lastParsedRequest == null) {
            log("✗ Cannot send to Repeater: No valid request");
            return;
        }

        try {
            String host = lastParsedRequest.httpService().host();
            api.repeater().sendToRepeater(lastParsedRequest, "Curl: " + host);
            log("✓ Successfully sent to Repeater: " + host);
            api.logging().logToOutput("Sent curl command to Repeater: " + host);
        } catch (Exception e) {
            log("✗ Error sending to Repeater: " + e.getMessage());
            api.logging().logToError("Error sending to Repeater: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void log(String message) {
        logOutput.append(message + "\n");
        logOutput.setCaretPosition(logOutput.getDocument().getLength());
    }
}

