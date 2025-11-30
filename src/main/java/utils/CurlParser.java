package utils;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CurlParser {

    public HttpRequest parse(String curlCommand, MontoyaApi api) throws Exception {
        curlCommand = curlCommand.trim().replaceAll("\\\\\\s*\\n\\s*", " ");

        String url = extractUrl(curlCommand);
        if (url == null || url.isEmpty()) {
            throw new Exception("Could not extract URL from curl command");
        }

        String method = extractMethod(curlCommand);
        List<HttpHeader> headers = extractHeaders(curlCommand);
        String body = extractBody(curlCommand);

        // Parse URL to get host, port, and protocol
        URL parsedUrl = new URL(url);
        String host = parsedUrl.getHost();
        int port = parsedUrl.getPort();
        if (port == -1) {
            port = parsedUrl.getDefaultPort();
        }
        boolean isHttps = "https".equalsIgnoreCase(parsedUrl.getProtocol());

        // Build request string
        StringBuilder requestBuilder = new StringBuilder();
        requestBuilder.append(method).append(" ").append(extractPath(url)).append(" HTTP/1.1\r\n");
        requestBuilder.append("Host: ").append(host);
        if ((isHttps && port != 443) || (!isHttps && port != 80)) {
            requestBuilder.append(":").append(port);
        }
        requestBuilder.append("\r\n");

        for (HttpHeader header : headers) {
            requestBuilder.append(header.name()).append(": ").append(header.value()).append("\r\n");
        }

        requestBuilder.append("\r\n");

        if (body != null && !body.isEmpty()) {
            requestBuilder.append(body);
        }

        HttpRequest request = HttpRequest.httpRequest(
            HttpService.httpService(host, port, isHttps),
            requestBuilder.toString()
        );

        return request;
    }

    private String extractUrl(String curlCommand) {
        // Method 1: Look for --url option explicitly
        Pattern pattern = Pattern.compile("--url\\s+['\"]?([^'\"\\s]+)['\"]?");
        Matcher matcher = pattern.matcher(curlCommand);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Method 2: Direct pattern matching for http(s):// URLs
        pattern = Pattern.compile("(https?://[^\\s'\"]+)");
        matcher = pattern.matcher(curlCommand);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Method 3: Universal tokenization approach
        // Split by spaces while preserving quoted strings
        List<String> tokens = tokenizeCurlCommand(curlCommand);

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            // Skip 'curl' command itself
            if (token.equals("curl")) {
                continue;
            }

            // If token starts with '-', it's a flag
            if (token.startsWith("-")) {
                // Check if this flag expects an argument (most single-dash flags do)
                // Long flags (--) almost always have arguments
                // Short flags without letters after them (like -X, -H, -d) have arguments
                // Standalone flags (like -v, -i, -L, -k) don't have arguments
                if (isStandaloneFlag(token)) {
                    // This flag doesn't take an argument, continue to next token
                    continue;
                } else {
                    // This flag takes an argument, skip the next token
                    i++;
                    continue;
                }
            }

            // If we reach here, it's not a flag - likely the URL
            // Additional validation: check if it looks like a URL
            if (token.startsWith("http://") || token.startsWith("https://") || token.contains(".")) {
                return token;
            }
        }

        return null;
    }

    /**
     * Tokenize curl command while respecting quoted strings
     */
    private List<String> tokenizeCurlCommand(String command) {
        List<String> tokens = new ArrayList<>();
        Pattern pattern = Pattern.compile("'[^']*'|\"[^\"]*\"|\\S+");
        Matcher matcher = pattern.matcher(command);

        while (matcher.find()) {
            String token = matcher.group();
            // Remove surrounding quotes
            token = token.replaceAll("^['\"]|['\"]$", "");
            tokens.add(token);
        }

        return tokens;
    }

    /**
     * Check if a flag is standalone (doesn't take an argument)
     * Universal approach: most standalone flags are single letter without value
     */
    private boolean isStandaloneFlag(String flag) {
        // Common standalone flags that don't take arguments
        Set<String> standaloneFlags = new HashSet<>(Arrays.asList(
            "-v", "--verbose",
            "-i", "--include",
            "-I", "--head",
            "-L", "--location",
            "-k", "--insecure",
            "-s", "--silent",
            "-S", "--show-error",
            "-#", "--progress-bar",
            "-n", "--netrc",
            "-N", "--no-buffer",
            "-l", "--list-only",
            "-f", "--fail",
            "-g", "--globoff",
            "-j", "--junk-session-cookies",
            "-J", "--remote-header-name",
            "-O", "--remote-name",
            "-q", "--disable",
            "-V", "--version",
            "-h", "--help",
            "--compressed",
            "--no-keepalive",
            "--http1.0",
            "--http1.1",
            "--http2"
        ));

        return standaloneFlags.contains(flag);
    }

    private String extractPath(String urlString) {
        try {
            URL url = new URL(urlString);
            String path = url.getPath();
            String query = url.getQuery();

            if (query != null && !query.isEmpty()) {
                return path + "?" + query;
            }
            return path.isEmpty() ? "/" : path;
        } catch (Exception e) {
            return "/";
        }
    }

    private String extractMethod(String curlCommand) {
        Pattern pattern = Pattern.compile("-X\\s+([A-Z]+)|--request\\s+([A-Z]+)");
        Matcher matcher = pattern.matcher(curlCommand);
        if (matcher.find()) {
            String method = matcher.group(1);
            return method != null ? method : matcher.group(2);
        }

        if (curlCommand.matches(".*(-d|--data|--data-raw|--data-binary|--data-urlencode)\\s+.*")) {
            return "POST";
        }

        return "GET";
    }

    private List<HttpHeader> extractHeaders(String curlCommand) {
        List<HttpHeader> headers = new ArrayList<>();
        List<String> tokens = tokenizeCurlCommand(curlCommand);

        for (int i = 0; i < tokens.size() - 1; i++) {
            String token = tokens.get(i);
            String nextToken = tokens.get(i + 1);

            // Handle -H or --header flags
            if (token.equals("-H") || token.equals("--header")) {
                if (nextToken.contains(":")) {
                    String[] parts = nextToken.split(":", 2);
                    String name = parts[0].trim();
                    String value = parts[1].trim();
                    headers.add(HttpHeader.httpHeader(name, value));
                }
            }
            // Handle --cookie or -b flags
            else if (token.equals("--cookie") || token.equals("-b")) {
                headers.add(HttpHeader.httpHeader("Cookie", nextToken));
            }
            // Handle --user-agent or -A flags
            else if (token.equals("--user-agent") || token.equals("-A")) {
                headers.add(HttpHeader.httpHeader("User-Agent", nextToken));
            }
            // Handle --referer or -e flags
            else if (token.equals("--referer") || token.equals("-e")) {
                headers.add(HttpHeader.httpHeader("Referer", nextToken));
            }
        }

        return headers;
    }

    private String extractBody(String curlCommand) {
        StringBuilder body = new StringBuilder();
        List<String> tokens = tokenizeCurlCommand(curlCommand);

        for (int i = 0; i < tokens.size() - 1; i++) {
            String token = tokens.get(i);
            String nextToken = tokens.get(i + 1);

            // Handle all data-related flags
            if (token.equals("-d") || token.equals("--data") ||
                token.equals("--data-raw") || token.equals("--data-binary") ||
                token.equals("--data-urlencode")) {

                if (body.length() > 0) {
                    body.append("&");
                }
                body.append(nextToken);
            }
        }

        return !body.isEmpty() ? body.toString() : null;
    }
}

