package integration;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.junit5.TextReportExtension;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.codeborne.selenide.Browsers.WEBKIT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public abstract class BaseWebKitTest {
  private static final Object LOCK = new Object();

  @RegisterExtension
  static final TextReportExtension textReport = new TextReportExtension()
    .onFailedTest(true)
    .onSucceededTest(true);

  @Nullable
  protected static HttpServer server;

  @BeforeAll
  static void ensureServerAndSelenideConfigured() throws IOException, URISyntaxException {
    synchronized (LOCK) {
      if (server == null) {
        Path sandboxPath = Path.of(requireNonNull(BaseWebKitTest.class.getResource("/sandbox")).toURI()).toAbsolutePath();
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", exchange -> serveStaticFile(exchange, sandboxPath));
        httpServer.start();
        server = httpServer;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
          if (server != null) {
            server.stop(0);
          }
        }, "sandbox-http-server-shutdown"));

        int port = httpServer.getAddress().getPort();
        Configuration.browser = WEBKIT;
        Configuration.baseUrl = "http://127.0.0.1:" + port;
        Configuration.browserSize = "1440x900";
        Configuration.timeout = 10_000;
        Configuration.pageLoadTimeout = 30_000;
        Configuration.screenshots = true;
        Configuration.savePageSource = true;
      }
    }
  }

  private static void serveStaticFile(HttpExchange exchange, Path sandboxPath) throws IOException {
    String rawPath = exchange.getRequestURI().getPath();
    String relative = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
    if (relative.isEmpty()) {
      relative = "index.html";
    }
    Path file = sandboxPath.resolve(relative).normalize();
    if (!file.startsWith(sandboxPath) || !Files.isRegularFile(file)) {
      byte[] notFound = "Not Found".getBytes(UTF_8);
      exchange.sendResponseHeaders(404, notFound.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(notFound);
      }
      return;
    }
    byte[] bytes = Files.readAllBytes(file);
    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
    exchange.sendResponseHeaders(200, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  @AfterAll
  static void closeClassBrowser() {
    Selenide.closeWebDriver();
  }
}
