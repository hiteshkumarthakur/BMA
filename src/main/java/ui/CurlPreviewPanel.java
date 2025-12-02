package ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import utils.CurlParser;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CurlPreviewPanel extends JPanel {
    private MontoyaApi api;
    private JTextArea curlTextArea;
    private JTextArea previewTextArea;
    private JButton sendToRepeaterButton;
    private HttpRequest lastParsedRequest;

    public CurlPreviewPanel(MontoyaApi api) {
        this.api = api;
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Title
        JLabel titleLabel = new JLabel("cURL to Repeater (Real-time Preview)");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        add(titleLabel, BorderLayout.NORTH);

        // Main split pane - HORIZONTAL for side-by-side
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        // Left panel - Input
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        JLabel inputLabel = new JLabel("Paste cURL command:");
        inputPanel.add(inputLabel, BorderLayout.NORTH);

        curlTextArea = new JTextArea();
        curlTextArea.setLineWrap(true);
        curlTextArea.setWrapStyleWord(true);
        curlTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // Add real-time parsing listener
        curlTextArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                parseCurlCommandSilently();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                parseCurlCommandSilently();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                parseCurlCommandSilently();
            }
        });

        JScrollPane curlScrollPane = new JScrollPane(curlTextArea);
        inputPanel.add(curlScrollPane, BorderLayout.CENTER);

        splitPane.setLeftComponent(inputPanel);

        // Right panel - Preview
        JPanel previewPanel = new JPanel(new BorderLayout(5, 5));
        JLabel previewLabel = new JLabel("Burp Repeater Format Preview:");
        previewPanel.add(previewLabel, BorderLayout.NORTH);

        previewTextArea = new JTextArea();
        previewTextArea.setEditable(false);
        previewTextArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        // Don't set background - let it use default theme colors
        JScrollPane previewScrollPane = new JScrollPane(previewTextArea);
        previewPanel.add(previewScrollPane, BorderLayout.CENTER);

        splitPane.setRightComponent(previewPanel);

        add(splitPane, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        sendToRepeaterButton = new JButton("Send to Repeater");
        sendToRepeaterButton.setEnabled(false);
        sendToRepeaterButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendToRepeater();
            }
        });
        buttonPanel.add(sendToRepeaterButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void parseCurlCommandSilently() {
        String curlCommand = curlTextArea.getText().trim();

        if (curlCommand.isEmpty()) {
            previewTextArea.setText("");
            sendToRepeaterButton.setEnabled(false);
            lastParsedRequest = null;
            return;
        }

        try {
            CurlParser parser = new CurlParser();
            lastParsedRequest = parser.parse(curlCommand, api);

            // Display the request in Burp format
            String requestPreview = formatRequestForDisplay(lastParsedRequest);
            previewTextArea.setText(requestPreview);
            sendToRepeaterButton.setEnabled(true);

            api.logging().logToOutput("✓ Successfully parsed curl command");

        } catch (Exception ex) {
            previewTextArea.setText("Error parsing cURL command:\n" + ex.getMessage());
            sendToRepeaterButton.setEnabled(false);
            lastParsedRequest = null;
        }
    }

    private void parseCurlCommand() {
        String curlCommand = curlTextArea.getText().trim();

        if (curlCommand.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please paste a cURL command first.",
                "No Input",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            api.logging().logToOutput("Parsing curl command...");
            CurlParser parser = new CurlParser();
            lastParsedRequest = parser.parse(curlCommand, api);

            // Display the request in Burp format
            String requestPreview = formatRequestForDisplay(lastParsedRequest);
            previewTextArea.setText(requestPreview);
            sendToRepeaterButton.setEnabled(true);

            api.logging().logToOutput("✓ Successfully parsed curl command");

        } catch (Exception ex) {
            api.logging().logToError("✗ Error: " + ex.getMessage());
            previewTextArea.setText("Error parsing cURL command:\n" + ex.getMessage());
            sendToRepeaterButton.setEnabled(false);

            JOptionPane.showMessageDialog(this,
                "Error parsing cURL command:\n" + ex.getMessage(),
                "Parse Error",
                JOptionPane.ERROR_MESSAGE);
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

        // Add the request
        display.append(request.toString());

        return display.toString();
    }

    private void sendToRepeater() {
        if (lastParsedRequest == null) {
            JOptionPane.showMessageDialog(this,
                "Please parse a cURL command first.",
                "No Request",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Send to Repeater
            api.repeater().sendToRepeater(lastParsedRequest);
            api.logging().logToOutput("✓ Request sent to Repeater successfully");

        } catch (Exception ex) {
            api.logging().logToError("✗ Error sending to Repeater: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                "Error sending to Repeater:\n" + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    // Method to set curl command programmatically (useful for testing)
    public void setCurlCommand(String curlCommand) {
        curlTextArea.setText(curlCommand);
    }

    // Auto-parse on set
    public void setCurlCommandAndParse(String curlCommand) {
        setCurlCommand(curlCommand);
        parseCurlCommand();
    }
}

