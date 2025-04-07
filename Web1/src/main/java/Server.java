import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final ExecutorService threadPool;
    private final int port;
    private final List<String> validPaths;

    public Server(int port, List<String> validPaths, int poolSize) {
        this.port = port;
        this.validPaths = validPaths;
        this.threadPool = Executors.newFixedThreadPool(poolSize);
    }

    public void start() {
        try (final var serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            while (!Thread.currentThread().isInterrupted()) {
                final var socket = serverSocket.accept();
                threadPool.submit(() -> handleConnection(socket));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    private void handleConnection(Socket socket) {
        try (
                final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final var out = new BufferedOutputStream(socket.getOutputStream());
        ) {
            final var requestLine = in.readLine();
            if (requestLine == null) return;

            try {
                final var request = new Request(requestLine);
                System.out.println("Request: " + request.getMethod() + " " + request.getPath() +
                        " QueryParams: " + request.getQueryParams());

                if (!validPaths.contains(request.getPath())) {
                    sendNotFoundResponse(out);
                    return;
                }

                final var filePath = Path.of("public", request.getPath()).normalize();

                // Security check
                if (!filePath.startsWith(Path.of("public").normalize())) {
                    sendForbiddenResponse(out);
                    return;
                }

                if (!Files.exists(filePath)) {
                    sendNotFoundResponse(out);
                    return;
                }

                final var mimeType = Files.probeContentType(filePath);

                if (request.getPath().equals("/classic.html")) {
                    handleClassicHtml(out, filePath, mimeType, request);
                    return;
                }

                sendFileResponse(out, filePath, mimeType);
            } catch (IllegalArgumentException e) {
                sendBadRequestResponse(out);
            }
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Socket close error: " + e.getMessage());
            }
        }
    }

    private void handleClassicHtml(BufferedOutputStream out, Path filePath, String mimeType, Request request)
            throws IOException {
        final var template = Files.readString(filePath);
        final var content = template.replace(
                "{time}",
                LocalDateTime.now() + " | Query: " + request.getQueryParams()
        ).getBytes();
        sendResponse(out, "HTTP/1.1 200 OK", mimeType, content.length, content);
    }

    private void sendFileResponse(BufferedOutputStream out, Path filePath, String mimeType)
            throws IOException {
        final var length = Files.size(filePath);
        sendResponse(out, "HTTP/1.1 200 OK", mimeType, length, null);
        Files.copy(filePath, out);
    }

    private void sendResponse(BufferedOutputStream out, String status, String mimeType,
                              long contentLength, byte[] content) throws IOException {
        out.write((status + "\r\n").getBytes());
        out.write(("Content-Type: " + mimeType + "\r\n").getBytes());
        out.write(("Content-Length: " + contentLength + "\r\n").getBytes());
        out.write("Connection: close\r\n".getBytes());
        out.write("\r\n".getBytes());
        if (content != null) {
            out.write(content);
        }
        out.flush();
    }

    private void sendNotFoundResponse(BufferedOutputStream out) throws IOException {
        sendResponse(out, "HTTP/1.1 404 Not Found", "text/plain", 0, null);
    }

    private void sendBadRequestResponse(BufferedOutputStream out) throws IOException {
        sendResponse(out, "HTTP/1.1 400 Bad Request", "text/plain", 0, null);
    }

    private void sendForbiddenResponse(BufferedOutputStream out) throws IOException {
        sendResponse(out, "HTTP/1.1 403 Forbidden", "text/plain", 0, null);
    }

    public static void main(String[] args) {
        final var validPaths = List.of(
                "/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css",
                "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js"
        );
        final var server = new Server(9999, validPaths, 64);
        server.start();
    }
}