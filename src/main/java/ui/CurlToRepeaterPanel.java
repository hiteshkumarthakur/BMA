package ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import utils.CurlParser;

import javax.swing.*;
import java.awt.*;

public class CurlToRepeaterPanel extends JPanel {
    private final MontoyaApi api;
    private final JTextArea curlInput;
    private final JTextArea logOutput;

    public CurlToRepeaterPanel(MontoyaApi api) {
        this.api = api;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel titleLabel = new JLabel("Curl to Repeater Converter");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        add(titleLabel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.6);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.add(new JLabel("Paste your curl command:"), BorderLayout.NORTH);

        curlInput = new JTextArea(10, 50);
        curlInput.setLineWrap(true);
        curlInput.setWrapStyleWord(true);
        curlInput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane inputScroll = new JScrollPane(curlInput);
        inputPanel.add(inputScroll, BorderLayout.CENTER);

        // Initialize logOutput before using in lambdas
        logOutput = new JTextArea(5, 50);
        logOutput.setEditable(false);
        logOutput.setFont(new Font("Monospaced", Font.PLAIN, 11));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton sendToRepeaterBtn = new JButton("Send to Repeater");
        sendToRepeaterBtn.addActionListener(e -> sendToRepeater());

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
            curlInput.setText("");
            logOutput.setText("");
        });

        buttonPanel.add(sendToRepeaterBtn);
        buttonPanel.add(clearBtn);
        inputPanel.add(buttonPanel, BorderLayout.SOUTH);

        splitPane.setTopComponent(inputPanel);

        JPanel logPanel = new JPanel(new BorderLayout(5, 5));
        logPanel.add(new JLabel("Log:"), BorderLayout.NORTH);

        JScrollPane logScroll = new JScrollPane(logOutput);
        logPanel.add(logScroll, BorderLayout.CENTER);

        splitPane.setBottomComponent(logPanel);

        add(splitPane, BorderLayout.CENTER);
    }

    private void sendToRepeater() {
        String curlCommand = curlInput.getText().trim();

        if (curlCommand.isEmpty()) {
            log("Error: Please enter a curl command");
            JOptionPane.showMessageDialog(this, "Please enter a curl command", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            log("Parsing curl command...");
            CurlParser parser = new CurlParser();
            HttpRequest request = parser.parse(curlCommand, api);

            if (request != null) {
                String host = request.httpService().host();
                api.repeater().sendToRepeater(request, "Curl: " + host);
                log("✓ Successfully sent to Repeater: " + host);
                api.logging().logToOutput("Sent curl command to Repeater: " + host);
                JOptionPane.showMessageDialog(this, "Request sent to Repeater successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                log("✗ Failed to parse curl command");
                JOptionPane.showMessageDialog(this, "Failed to parse curl command", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            log("✗ Error: " + e.getMessage());
            api.logging().logToError("Error parsing curl: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void log(String message) {
        logOutput.append(message + "\n");
        logOutput.setCaretPosition(logOutput.getDocument().getLength());
    }
}

