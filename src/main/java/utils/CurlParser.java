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
        Pattern pattern = Pattern.compile("curl\\s+(?:-[^\\s]+\\s+)*['\"]?([^'\"\\s]+)['\"]?");
        Matcher matcher = pattern.matcher(curlCommand);
        if (matcher.find()) {
            return matcher.group(1);
        }

        pattern = Pattern.compile("--url\\s+['\"]?([^'\"\\s]+)['\"]?");
        matcher = pattern.matcher(curlCommand);
        if (matcher.find()) {
            return matcher.group(1);
        }

        pattern = Pattern.compile("(https?://[^\\s'\"]+)");
        matcher = pattern.matcher(curlCommand);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
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

        Pattern pattern = Pattern.compile("-H\\s+['\"]([^'\"]+)['\"]|--header\\s+['\"]([^'\"]+)['\"]");
        Matcher matcher = pattern.matcher(curlCommand);

        while (matcher.find()) {
            String headerLine = matcher.group(1);
            if (headerLine == null) {
                headerLine = matcher.group(2);
            }

            if (headerLine != null && headerLine.contains(":")) {
                String[] parts = headerLine.split(":", 2);
                String name = parts[0].trim();
                String value = parts[1].trim();
                headers.add(HttpHeader.httpHeader(name, value));
            }
        }

        pattern = Pattern.compile("--cookie\\s+['\"]([^'\"]+)['\"]|-b\\s+['\"]([^'\"]+)['\"]");
        matcher = pattern.matcher(curlCommand);
        if (matcher.find()) {
            String cookie = matcher.group(1);
            if (cookie == null) {
                cookie = matcher.group(2);
            }
            if (cookie != null) {
                headers.add(HttpHeader.httpHeader("Cookie", cookie));
            }
        }

        pattern = Pattern.compile("--user-agent\\s+['\"]([^'\"]+)['\"]|-A\\s+['\"]([^'\"]+)['\"]");
        matcher = pattern.matcher(curlCommand);
        if (matcher.find()) {
            String userAgent = matcher.group(1);
            if (userAgent == null) {
                userAgent = matcher.group(2);
            }
            if (userAgent != null) {
                headers.add(HttpHeader.httpHeader("User-Agent", userAgent));
            }
        }

        pattern = Pattern.compile("--referer\\s+['\"]([^'\"]+)['\"]|-e\\s+['\"]([^'\"]+)['\"]");
        matcher = pattern.matcher(curlCommand);
        if (matcher.find()) {
            String referer = matcher.group(1);
            if (referer == null) {
                referer = matcher.group(2);
            }
            if (referer != null) {
                headers.add(HttpHeader.httpHeader("Referer", referer));
            }
        }

        return headers;
    }

    private String extractBody(String curlCommand) {
        Pattern pattern = Pattern.compile("(?:-d|--data|--data-raw|--data-binary)\\s+['\"]([^'\"]*)['\"]");
        Matcher matcher = pattern.matcher(curlCommand);

        StringBuilder body = new StringBuilder();
        while (matcher.find()) {
            if (body.length() > 0) {
                body.append("&");
            }
            body.append(matcher.group(1));
        }

        return body.length() > 0 ? body.toString() : null;
    }
}

