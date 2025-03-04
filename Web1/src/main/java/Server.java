
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
            System.out.println("Сервер запущен на порту " + port);
            while (!Thread.currentThread().isInterrupted()) {
                final var socket = serverSocket.accept();
                threadPool.submit(() -> handleConnection(socket));
            }
        } catch (IOException e) {
            System.err.println("Ошибка при запуске сервера: " + e.getMessage());
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
            final var parts = requestLine.split(" ");

            if (parts.length != 3) {

                return;
            }

            final var path = parts[1];
            if (!validPaths.contains(path)) {
                sendResponse(out, "HTTP/1.1 404 Not Found", "text/plain", 0, null);
                return;
            }

            final var filePath = Path.of(".", "public", path);
            final var mimeType = Files.probeContentType(filePath);

            if (path.equals("/classic.html")) {
                final var template = Files.readString(filePath);
                final var content = template.replace("{time}", LocalDateTime.now().toString()).getBytes();
                sendResponse(out, "HTTP/1.1 200 OK", mimeType, content.length, content);
                return;
            }

            final var length = Files.size(filePath);
            sendResponse(out, "HTTP/1.1 200 OK", mimeType, length, null);
            Files.copy(filePath, out);
        } catch (IOException e) {
            System.err.println("Ошибка при обработке подключения: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Ошибка при закрытии сокета: " + e.getMessage());
            }
        }
    }

    private void sendResponse(BufferedOutputStream out, String status, String mimeType, long contentLength, byte[] content) throws IOException {
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

    public static void main(String[] args) {
        final var validPaths = List.of(
                "/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css",
                "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js"
        );
        int port = 9999;
        int poolSize = 64; 

        Server server = new Server(port, validPaths, poolSize);
        server.start();
    }
}