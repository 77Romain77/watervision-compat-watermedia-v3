package me.srrapero720.watervision.client.screens;

import me.srrapero720.watervision.WaterVision;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class VisionStreamingProxy {
    private static final String BUILD_TAG = "streaming-proxy-001";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Map<String, URI> ROUTES = new ConcurrentHashMap<>();

    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private static int port;

    private VisionStreamingProxy() {}

    static URI proxy(final URI remoteUri) throws IOException {
        ensureStarted();
        final String token = UUID.randomUUID().toString().replace("-", "");
        ROUTES.put(token, remoteUri);
        final URI proxyUri = URI.create("http://127.0.0.1:" + port + "/watervision/" + token + extension(remoteUri));
        WaterVision.LOGGER.info("WaterVision streaming proxy route created [{}]: proxyUri={}, remoteUri={}", BUILD_TAG, proxyUri, remoteUri);
        return proxyUri;
    }

    private static synchronized void ensureStarted() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) return;
        serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        port = serverSocket.getLocalPort();
        serverThread = new Thread(VisionStreamingProxy::acceptLoop, "WaterVision-StreamingProxy");
        serverThread.setDaemon(true);
        serverThread.start();
        WaterVision.LOGGER.info("WaterVision streaming proxy started [{}]: http://127.0.0.1:{}", BUILD_TAG, port);
    }

    private static void acceptLoop() {
        while (serverSocket != null && !serverSocket.isClosed()) {
            try {
                final Socket socket = serverSocket.accept();
                final Thread worker = new Thread(() -> handle(socket), "WaterVision-StreamingProxy-Client");
                worker.setDaemon(true);
                worker.start();
            } catch (final IOException exception) {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    WaterVision.LOGGER.warn("WaterVision streaming proxy accept failed [{}]", BUILD_TAG, exception);
                }
            }
        }
    }

    private static void handle(final Socket socket) {
        try (socket) {
            socket.setSoTimeout(30000);
            final InputStream input = socket.getInputStream();
            final OutputStream output = socket.getOutputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.ISO_8859_1));

            final String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                writeSimpleResponse(output, 400, "Bad Request");
                return;
            }

            final String[] requestParts = requestLine.split(" ", 3);
            if (requestParts.length < 2) {
                writeSimpleResponse(output, 400, "Bad Request");
                return;
            }

            final String method = requestParts[0].toUpperCase(Locale.ROOT);
            final String path = requestParts[1];
            String rangeHeader = null;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                final int separator = line.indexOf(':');
                if (separator <= 0) continue;
                final String headerName = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                final String headerValue = line.substring(separator + 1).trim();
                if ("range".equals(headerName)) rangeHeader = headerValue;
            }

            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                writeSimpleResponse(output, 405, "Method Not Allowed");
                return;
            }

            final URI remoteUri = route(path);
            if (remoteUri == null) {
                writeSimpleResponse(output, 404, "Not Found");
                return;
            }

            final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(remoteUri)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "WaterVision/0.1 streaming-proxy")
                    .header("Accept", "video/mp4,video/*,*/*");
            if (rangeHeader != null && !rangeHeader.isBlank()) requestBuilder.header("Range", rangeHeader);

            if ("HEAD".equals(method)) {
                final HttpResponse<Void> response = HTTP_CLIENT.send(requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
                writeResponseHeaders(output, response.statusCode(), response, true);
                return;
            }

            final HttpResponse<InputStream> response = HTTP_CLIENT.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            writeResponseHeaders(output, response.statusCode(), response, false);
            try (InputStream responseBody = response.body()) {
                responseBody.transferTo(output);
            }
            output.flush();
        } catch (final Exception exception) {
            WaterVision.LOGGER.warn("WaterVision streaming proxy request failed [{}]", BUILD_TAG, exception);
        }
    }

    private static URI route(final String rawPath) {
        final String path = rawPath == null ? "" : rawPath.split("\\?", 2)[0];
        final String prefix = "/watervision/";
        if (!path.startsWith(prefix)) return null;
        final String fileName = path.substring(prefix.length());
        final int dot = fileName.indexOf('.');
        final String token = dot >= 0 ? fileName.substring(0, dot) : fileName;
        return ROUTES.get(token);
    }

    private static void writeResponseHeaders(final OutputStream output, final int statusCode, final HttpResponse<?> response, final boolean headOnly) throws IOException {
        writeAscii(output, "HTTP/1.1 " + statusCode + " " + reason(statusCode) + "\r\n");
        writeAscii(output, "Connection: close\r\n");
        writeAscii(output, "Accept-Ranges: bytes\r\n");
        response.headers().firstValue("Content-Type").ifPresent(value -> writeHeader(output, "Content-Type", value));
        response.headers().firstValue("Content-Length").ifPresent(value -> writeHeader(output, "Content-Length", value));
        response.headers().firstValue("Content-Range").ifPresent(value -> writeHeader(output, "Content-Range", value));
        response.headers().firstValue("Last-Modified").ifPresent(value -> writeHeader(output, "Last-Modified", value));
        response.headers().firstValue("ETag").ifPresent(value -> writeHeader(output, "ETag", value));
        if (headOnly) writeAscii(output, "Content-Length: 0\r\n");
        writeAscii(output, "\r\n");
        output.flush();
    }

    private static void writeHeader(final OutputStream output, final String name, final String value) {
        try {
            writeAscii(output, name + ": " + value + "\r\n");
        } catch (final IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void writeSimpleResponse(final OutputStream output, final int statusCode, final String message) throws IOException {
        final byte[] body = message.getBytes(StandardCharsets.UTF_8);
        writeAscii(output, "HTTP/1.1 " + statusCode + " " + reason(statusCode) + "\r\n");
        writeAscii(output, "Connection: close\r\n");
        writeAscii(output, "Content-Type: text/plain; charset=utf-8\r\n");
        writeAscii(output, "Content-Length: " + body.length + "\r\n");
        writeAscii(output, "\r\n");
        output.write(body);
        output.flush();
    }

    private static void writeAscii(final OutputStream output, final String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String reason(final int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 206 -> "Partial Content";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 416 -> "Range Not Satisfiable";
            case 500 -> "Internal Server Error";
            default -> statusCode >= 200 && statusCode < 300 ? "OK" : "Error";
        };
    }

    private static String extension(final URI remoteUri) {
        final String path = remoteUri.getPath();
        if (path == null) return ".mp4";
        final int slash = path.lastIndexOf('/');
        final int dot = path.lastIndexOf('.');
        if (dot <= slash || dot < 0 || dot >= path.length() - 1) return ".mp4";
        final String extension = path.substring(dot).toLowerCase(Locale.ROOT);
        if (extension.length() > 12 || !extension.matches("\\.[a-z0-9]+")) return ".mp4";
        return extension;
    }
}
