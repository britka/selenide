package org.selenide.webkit;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.net.PortProber;
import org.openqa.selenium.os.ExternalProcess;
import org.openqa.selenium.remote.service.DriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;

/**
 * Manages the life cycle of a native WebKit WebDriver server ({@code WebDriver.exe} or {@code WebKitWebDriver})
 * and its associated {@code MiniBrowser} instance.
 */
public class WebKitDriverService extends DriverService {
  private static final Logger log = LoggerFactory.getLogger(WebKitDriverService.class);

  public static final String WEBKIT_DRIVER_NAME = "WebKitDriver";
  public static final String WEBKIT_DRIVER_EXE_PROPERTY = "webdriver.webkit.driver";
  public static final String WEBKIT_DRIVER_EXE_ENVIRONMENT_VARIABLE = "WEBKIT_DRIVER_EXE";
  public static final String WEBKIT_DRIVER_LOG_PROPERTY = "webdriver.webkit.logfile";

  private static final Object LOCK = new Object();
  private static final Set<Integer> ALLOCATED_PORTS = ConcurrentHashMap.newKeySet();

  private final int managedInspectorPort;
  @Nullable
  private ProcessHandle browserHandle;
  @Nullable
  private Thread shutdownHook;

  public WebKitDriverService(
    File executable,
    int port,
    Duration timeout,
    List<String> args,
    Map<String, String> environment
  ) throws IOException {
    this(executable, port, timeout, args, environment, 0);
  }

  public WebKitDriverService(
    File executable,
    int port,
    Duration timeout,
    List<String> args,
    Map<String, String> environment,
    int managedInspectorPort
  ) throws IOException {
    super(executable, port, timeout, args, environment);
    this.managedInspectorPort = managedInspectorPort;
  }

  public static int allocateUniqueFreePort() {
    synchronized (LOCK) {
      for (int i = 0; i < 50; i++) {
        int candidate = PortProber.findFreePort();
        if (ALLOCATED_PORTS.add(candidate)) {
          return candidate;
        }
      }
      return PortProber.findFreePort();
    }
  }

  public static WebKitDriverService createDefaultService() {
    return new Builder().usingPort(allocateUniqueFreePort()).build();
  }

  @Override
  public String getDriverName() {
    return WEBKIT_DRIVER_NAME;
  }

  @Override
  public String getDriverProperty() {
    return WEBKIT_DRIVER_EXE_PROPERTY;
  }

  @Override
  public String getDriverEnvironmentVariable() {
    return WEBKIT_DRIVER_EXE_ENVIRONMENT_VARIABLE;
  }

  @Override
  protected Capabilities getDefaultDriverOptions() {
    return WebKitDriver.defaultCapabilities();
  }

  @Override
  public void start() throws IOException {
    if (process != null) {
      return;
    }

    String exe = Objects.requireNonNull(getExecutable(), "The path to the driver executable must be set");
    File driverFile = new File(exe);
    File driverDir = driverFile.getParentFile() != null ? driverFile.getParentFile() : new File(".");
    String newPath = buildPathWithDriverDir(driverDir);

    synchronized (LOCK) {
      if (managedInspectorPort > 0 && WebKitManager.isWindows()) {
        startManagedMiniBrowser(driverDir, newPath, managedInspectorPort);
      }

      log.info("Starting WebKit WebDriver from working directory: {}", driverDir.getAbsolutePath());
      process = startDriverProcess(exe, driverDir, newPath);

      shutdownHook = new Thread(this::stopProcessesQuietly, "webkit-driver-shutdown-" + getUrl().getPort());
      Runtime.getRuntime().addShutdownHook(shutdownHook);

      waitUntilAvailable();
    }
  }

  private static String buildPathWithDriverDir(File driverDir) {
    String currentPath = System.getenv("PATH");
    if (currentPath == null || currentPath.isEmpty()) {
      return driverDir.getAbsolutePath();
    }
    return driverDir.getAbsolutePath() + File.pathSeparator + currentPath;
  }

  private ExternalProcess startDriverProcess(String exe, File driverDir, String newPath) {
    ExternalProcess.Builder processBuilder = ExternalProcess.builder()
      .command(exe, getArgs())
      .directory(driverDir)
      .copyOutputTo(getOutputStream());

    processBuilder.environment("PATH", newPath);
    if (!WebKitManager.isWindows()) {
      configureLinuxDisplay(processBuilder);
    }
    getEnvironment().forEach(processBuilder::environment);
    return processBuilder.start();
  }

  private static void configureLinuxDisplay(ExternalProcess.Builder processBuilder) {
    String display = System.getenv("DISPLAY");
    if ((display == null || display.isBlank()) && new File("/tmp/.X11-unix/X0").exists()) {
      processBuilder.environment("DISPLAY", ":0");
    }
  }

  private void startManagedMiniBrowser(File driverDir, String pathEnv, int inspectorPort) {
    File browserExe = new File(driverDir, WebKitManager.BROWSER_EXE_NAME);
    if (!browserExe.exists()) {
      throw new WebDriverException("MiniBrowser executable not found at: " + browserExe.getAbsolutePath());
    }

    String inspectorServer = "127.0.0.1:" + inspectorPort;
    log.info("Launching {} with WEBKIT_INSPECTOR_SERVER={}", browserExe.getAbsolutePath(), inspectorServer);

    ProcessBuilder launcher = createMiniBrowserLauncher(browserExe, driverDir, pathEnv, inspectorServer);
    browserHandle = launchMiniBrowserProcess(launcher);
    waitUntilInspectorListening(inspectorPort, getTimeout());
  }

  private ProcessBuilder createMiniBrowserLauncher(File browserExe, File driverDir,
                                                   String pathEnv, String inspectorServer) {
    String escapedExe = browserExe.getAbsolutePath().replace("'", "''");
    String escapedDir = driverDir.getAbsolutePath().replace("'", "''");
    String psCommand = "(Start-Process -FilePath '" + escapedExe + "'"
      + " -ArgumentList 'about://blank'"
      + " -WorkingDirectory '" + escapedDir + "'"
      + " -WindowStyle Hidden -PassThru).Id";

    ProcessBuilder launcher = new ProcessBuilder(
      "powershell.exe",
      "-NoProfile",
      "-NonInteractive",
      "-Command",
      psCommand
    );
    launcher.directory(driverDir);
    Map<String, String> env = launcher.environment();
    env.put("PATH", pathEnv);
    env.put("WEBKIT_INSPECTOR_SERVER", inspectorServer);
    env.putAll(getEnvironment());
    return launcher;
  }

  private static ProcessHandle launchMiniBrowserProcess(ProcessBuilder launcher) {
    try {
      Process startProc = launcher.start();
      String stdout = new String(startProc.getInputStream().readAllBytes(), UTF_8).trim();
      int exitCode = startProc.waitFor();
      if (exitCode != 0 || stdout.isEmpty()) {
        String stderr = new String(startProc.getErrorStream().readAllBytes(), UTF_8).trim();
        throw new WebDriverException("Failed to launch MiniBrowser.exe (exit=" + exitCode + "): " + stderr);
      }
      long pid = Long.parseLong(stdout.lines().reduce((first, second) -> second).orElse(stdout).trim());
      return ProcessHandle.of(pid).orElseThrow(
        () -> new WebDriverException("MiniBrowser.exe process (PID " + pid + ") exited immediately")
      );
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new WebDriverException("Interrupted while launching MiniBrowser.exe", e);
    }
    catch (IOException | NumberFormatException e) {
      throw new WebDriverException("Failed to launch MiniBrowser.exe", e);
    }
  }

  private void waitUntilInspectorListening(int inspectorPort, Duration timeout) {
    long deadline = System.currentTimeMillis() + Math.max(timeout.toMillis(), 30_000L);
    sleepOrThrow(300, "Interrupted while waiting for MiniBrowser to start");

    while (System.currentTimeMillis() < deadline) {
      if (browserHandle != null && !browserHandle.isAlive()) {
        throw new WebDriverException("MiniBrowser.exe exited prematurely before opening inspector port " + inspectorPort);
      }
      if (isLocalPortInUse(inspectorPort)) {
        log.info("MiniBrowser inspector server is ready on 127.0.0.1:{}", inspectorPort);
        return;
      }
      sleepOrThrow(200, "Interrupted while waiting for MiniBrowser inspector port");
    }
    throw new WebDriverException("Timed out waiting for MiniBrowser inspector server on 127.0.0.1:" + inspectorPort);
  }

  private static void sleepOrThrow(long millis, String interruptMessage) {
    try {
      Thread.sleep(millis);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new WebDriverException(interruptMessage, e);
    }
  }

  static boolean isLocalPortInUse(int port) {
    try (ServerSocket ss = new ServerSocket()) {
      ss.setReuseAddress(false);
      ss.bind(new InetSocketAddress("127.0.0.1", port));
      return false;
    }
    catch (IOException e) {
      return true;
    }
  }

  @Override
  public void stop() {
    Thread hook = this.shutdownHook;
    this.shutdownHook = null;
    if (hook != null) {
      try {
        Runtime.getRuntime().removeShutdownHook(hook);
      }
      catch (IllegalStateException ignored) {
        // JVM is already shutting down
      }
    }
    stopProcessesQuietly();
  }

  private void stopProcessesQuietly() {
    try {
      stopMiniBrowserProcess();
      stopDriverProcess();
      super.stop();
    }
    catch (Exception e) {
      log.debug("Failed to cleanly stop WebKitDriverService process: {}", e.getMessage());
    }
    finally {
      releaseAllocatedPorts();
    }
  }

  private void stopMiniBrowserProcess() {
    ProcessHandle handle = this.browserHandle;
    if (handle != null) {
      try {
        handle.descendants().forEach(ProcessHandle::destroyForcibly);
        handle.destroyForcibly();
      }
      catch (Exception e) {
        log.debug("Failed to cleanly stop MiniBrowser process: {}", e.getMessage());
      }
      finally {
        this.browserHandle = null;
      }
    }
  }

  private void stopDriverProcess() {
    ExternalProcess proc = this.process;
    if (proc != null) {
      try {
        if (proc.isAlive()) {
          proc.shutdown(Duration.ofMillis(500));
        }
      }
      catch (Exception e) {
        log.debug("Failed to cleanly stop WebDriver.exe process: {}", e.getMessage());
      }
      finally {
        this.process = null;
      }
    }
  }

  private void releaseAllocatedPorts() {
    if (managedInspectorPort > 0) {
      ALLOCATED_PORTS.remove(managedInspectorPort);
    }
    if (getUrl() != null && getUrl().getPort() > 0) {
      ALLOCATED_PORTS.remove(getUrl().getPort());
    }
  }

  public static class Builder extends DriverService.Builder<WebKitDriverService, Builder> {
    @Nullable
    private String host;
    private boolean replaceOnNewSession = true;
    @Nullable
    private String target;
    private boolean headless = Boolean.parseBoolean(
      System.getProperty("selenide.headless", System.getProperty("headless", "false"))
    );
    private int allocatedInspectorPort;

    @CanIgnoreReturnValue
    public Builder withHost(@Nullable String host) {
      this.host = host;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder withReplaceOnNewSession(boolean replace) {
      this.replaceOnNewSession = replace;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder withTarget(@Nullable String target) {
      this.target = target;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder withHeadless(boolean headless) {
      this.headless = headless;
      return this;
    }

    @Override
    public int score(Capabilities capabilities) {
      String browserName = capabilities.getBrowserName();
      if (browserName == null) {
        return 0;
      }
      String lower = browserName.toLowerCase(ROOT);
      return "minibrowser".equals(lower) || "webkit".equals(lower) ? 10 : 0;
    }

    @Override
    protected void loadSystemProperties() {
      if (this.exe == null) {
        this.exe = WebKitManager.getOrInstallDriver();
      }
      if (getPort() == 0) {
        usingPort(allocateUniqueFreePort());
      }
    }

    @Override
    protected List<String> createArgs() {
      List<String> args = new ArrayList<>();

      int chosenPort = getPort() == 0 ? allocateUniqueFreePort() : getPort();
      args.add("--port=" + chosenPort);

      if (host != null && !host.isBlank()) {
        args.add("--host=" + host);
      }

      if (replaceOnNewSession) {
        args.add("--replace-on-new-session");
      }

      configureTargetArg(args);
      return Collections.unmodifiableList(args);
    }

    private void configureTargetArg(List<String> args) {
      if (target != null && !target.isBlank()) {
        args.add("--target=" + target);
        allocatedInspectorPort = 0;
      }
      else if (WebKitManager.isWindows() && !headless) {
        allocatedInspectorPort = allocateUniqueFreePort();
        args.add("--target=127.0.0.1:" + allocatedInspectorPort);
      }
      else {
        allocatedInspectorPort = 0;
      }
    }

    @Override
    protected WebKitDriverService createDriverService(
      File exe,
      int port,
      Duration timeout,
      List<String> args,
      Map<String, String> environment
    ) {
      try {
        return new WebKitDriverService(exe, port, timeout, args, environment, allocatedInspectorPort);
      }
      catch (IOException e) {
        throw new WebDriverException("Failed to start WebKitDriverService", e);
      }
    }
  }
}
