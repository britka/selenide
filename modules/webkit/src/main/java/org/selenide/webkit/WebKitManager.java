package org.selenide.webkit;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Manages the automatic discovery, downloading, unpacking, updating, and caching of
 * native WebKit MiniBrowser and WebDriver.exe on Windows, or locating WebKitWebDriver on Linux.
 */
public final class WebKitManager {
  private static final Logger log = LoggerFactory.getLogger(WebKitManager.class);
  private static final Object LOCK = new Object();
  private static final Json JSON = new Json();
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(30))
    .followRedirects(HttpClient.Redirect.ALWAYS)
    .build();

  public static final String BUILDBOT_BUILDS_API =
    "https://build.webkit.org/api/v2/builders/1192/builds?order=-number&limit=10";
  public static final String BUILDBOT_STEPS_API_TEMPLATE =
    "https://build.webkit.org/api/v2/builders/1192/builds/%s/steps";

  public static final String PROPERTY_DRIVER_PATH = "webdriver.webkit.driver";
  public static final String PROPERTY_ARCHIVE_URL = "webdriver.webkit.url";
  public static final String PROPERTY_UPDATE_WEBKIT = "webdriver.webkit.update";

  public static final String ENV_DRIVER_PATH = "WEBKIT_DRIVER_EXE";
  public static final String ENV_ARCHIVE_URL = "WEBKIT_ARCHIVE_URL";
  public static final String ENV_UPDATE_WEBKIT = "WEBKIT_UPDATE";

  public static final String DRIVER_EXE_NAME = "WebDriver.exe";
  public static final String LINUX_DRIVER_EXE_NAME = "WebKitWebDriver";
  public static final String BROWSER_EXE_NAME = "MiniBrowser.exe";

  private WebKitManager() {
  }

  public static boolean isWindows() {
    return System.getProperty("os.name", "")
      .toLowerCase(ROOT)
      .contains("win");
  }

  public static File getCacheDir() {
    File baseDir = resolveBaseCacheDir();
    return new File(baseDir, "WebKit");
  }

  private static File resolveBaseCacheDir() {
    if (isWindows()) {
      String localAppData = System.getenv("LOCALAPPDATA");
      if (localAppData != null && !localAppData.isBlank()) {
        return new File(localAppData);
      }
    }
    return new File(System.getProperty("user.home"), ".webkit");
  }

  public static boolean isUpdateRequested() {
    String prop = System.getProperty(PROPERTY_UPDATE_WEBKIT);
    if (prop != null && !prop.isBlank()) {
      return Boolean.parseBoolean(prop);
    }
    String env = System.getenv(ENV_UPDATE_WEBKIT);
    return env != null && !env.isBlank() && Boolean.parseBoolean(env);
  }

  public static File resolveTargetDir() {
    String customPath = getConfiguredDriverPath();
    if (customPath == null || customPath.isBlank()) {
      return getCacheDir();
    }

    File customFile = new File(customPath).getAbsoluteFile();
    if (isDriverExecutableFile(customFile)) {
      File parent = customFile.getParentFile();
      return parent != null ? parent : getCacheDir();
    }
    return customFile;
  }

  @Nullable
  private static String getConfiguredDriverPath() {
    String customPath = System.getProperty(PROPERTY_DRIVER_PATH);
    if (customPath != null && !customPath.isBlank()) {
      return customPath;
    }
    return System.getenv(ENV_DRIVER_PATH);
  }

  private static boolean isDriverExecutableFile(File file) {
    return file.getName().equalsIgnoreCase(DRIVER_EXE_NAME)
      || file.getName().equalsIgnoreCase(LINUX_DRIVER_EXE_NAME)
      || file.isFile();
  }

  @CanIgnoreReturnValue
  public static File getOrInstallDriver() {
    synchronized (LOCK) {
      return getOrInstallDriver(isUpdateRequested());
    }
  }

  @CanIgnoreReturnValue
  public static File getOrInstallDriver(boolean forceUpdate) {
    synchronized (LOCK) {
      if (!isWindows()) {
        return resolveLinuxDriver();
      }

      File targetDir = resolveTargetDir();
      File driverFile = new File(targetDir, DRIVER_EXE_NAME);

      if (!forceUpdate && isInstalled(targetDir)) {
        log.info("Found WebKit WebDriver in target directory: {}", driverFile.getAbsolutePath());
        updateSystemProperties(driverFile);
        return driverFile.getAbsoluteFile();
      }

      if (forceUpdate) {
        log.info("WebKit update requested. Reinstalling WebKit in: {}", targetDir.getAbsolutePath());
      }
      else {
        log.info("WebKit not found in {}. Triggering download & installation...", targetDir.getAbsolutePath());
      }

      return install(targetDir, forceUpdate);
    }
  }

  static File resolveLinuxDriver() {
    File fromConfiguredPath = findExecutableFromConfiguredPath();
    if (fromConfiguredPath != null) {
      return fromConfiguredPath;
    }

    File fromPathEnv = findExecutableInDirs(
      System.getenv("PATH") != null ? List.of(System.getenv("PATH").split(File.pathSeparator)) : List.of()
    );
    if (fromPathEnv != null) {
      return fromPathEnv;
    }

    File fromStandardDirs = findExecutableInDirs(List.of("/usr/bin", "/usr/local/bin", "/bin"));
    if (fromStandardDirs != null) {
      return fromStandardDirs;
    }

    throw new WebDriverException("Could not find '" + LINUX_DRIVER_EXE_NAME + "' on Linux. "
      + "Install it via 'sudo apt install webkit2gtk-driver' or set -D" + PROPERTY_DRIVER_PATH + "=/path/to/WebKitWebDriver");
  }

  @Nullable
  private static File findExecutableFromConfiguredPath() {
    String customPath = getConfiguredDriverPath();
    if (customPath == null || customPath.isBlank()) {
      return null;
    }
    File customFile = new File(customPath).getAbsoluteFile();
    File candidate = customFile.isDirectory() ? new File(customFile, LINUX_DRIVER_EXE_NAME) : customFile;
    if (candidate.exists() && candidate.canExecute()) {
      log.info("Found Linux WebKitWebDriver at configured path: {}", candidate.getAbsolutePath());
      updateSystemProperties(candidate);
      return candidate;
    }
    return null;
  }

  @Nullable
  private static File findExecutableInDirs(List<String> dirs) {
    for (String dir : dirs) {
      if (!dir.isBlank()) {
        File candidate = new File(dir, LINUX_DRIVER_EXE_NAME);
        if (candidate.isFile() && candidate.canExecute()) {
          log.info("Found Linux WebKitWebDriver at: {}", candidate.getAbsolutePath());
          updateSystemProperties(candidate);
          return candidate.getAbsoluteFile();
        }
      }
    }
    return null;
  }

  @CanIgnoreReturnValue
  public static File updateDriver() {
    synchronized (LOCK) {
      return updateDriver(resolveTargetDir());
    }
  }

  @CanIgnoreReturnValue
  public static File updateDriver(File targetDir) {
    synchronized (LOCK) {
      return install(targetDir, true);
    }
  }

  @CanIgnoreReturnValue
  public static File install(File targetDir, boolean forceUpdate) {
    File absTarget = targetDir.getAbsoluteFile();
    File parentDir = absTarget.getParentFile() != null ? absTarget.getParentFile() : absTarget;
    mkdirsQuietly(parentDir);
    File lockFile = new File(parentDir, ".webkit-install.lock");

    try (FileOutputStream lockStream = new FileOutputStream(lockFile);
         FileLock ignored = lockStream.getChannel().lock()) {
      return installLocked(targetDir, parentDir, forceUpdate);
    }
    catch (IOException e) {
      throw new WebDriverException("Failed to install WebKit MiniBrowser in " + targetDir.getAbsolutePath(), e);
    }
  }

  private static File installLocked(File targetDir, File parentDir, boolean forceUpdate) throws IOException {
    File driverFile = new File(targetDir, DRIVER_EXE_NAME);

    if (forceUpdate) {
      clearCache(targetDir);
      System.setProperty(PROPERTY_UPDATE_WEBKIT, "false");
    }
    else if (isInstalled(targetDir)) {
      log.info("WebKit WebDriver was already installed by a concurrent process: {}", driverFile.getAbsolutePath());
      updateSystemProperties(driverFile);
      return driverFile.getAbsoluteFile();
    }

    mkdirsQuietly(targetDir);
    String archiveUrl = resolveArchiveUrl();
    log.info("Downloading WebKit MiniBrowser package from: {}", archiveUrl);

    File tempZip = new File(parentDir, "_download_WebKitBuild.zip");
    try {
      downloadFile(archiveUrl, tempZip);
      log.info("Download complete. Extracting WebKit binaries (excluding .pdb) into: {}", targetDir.getAbsolutePath());

      extractArchive(tempZip, targetDir);
      createLauncherScript(targetDir);

      if (!isInstalled(targetDir)) {
        throw new WebDriverException(
          "Extraction finished but '" + DRIVER_EXE_NAME + "' was not found in: " + targetDir.getAbsolutePath());
      }

      updateSystemProperties(driverFile);
      log.info("WebKit MiniBrowser installed successfully at: {}", driverFile.getAbsolutePath());
      return driverFile.getAbsoluteFile();
    }
    finally {
      deleteQuietly(tempZip);
    }
  }

  public static void clearCache(File targetDir) {
    if (!targetDir.exists()) {
      return;
    }
    log.info("Clearing WebKit cache directory: {}", targetDir.getAbsolutePath());
    terminateProcessesInDir(targetDir);
    try (Stream<Path> walk = Files.walk(targetDir.toPath())) {
      walk.sorted(Comparator.reverseOrder())
        .map(Path::toFile)
        .forEach(file -> {
          if (!file.delete()) {
            file.deleteOnExit();
          }
        });
    }
    catch (IOException e) {
      log.warn("Could not completely delete some files in {}, attempting to overwrite during extraction.",
        targetDir.getAbsolutePath(), e);
    }
  }

  private static void terminateProcessesInDir(File targetDir) {
    String targetPrefix = targetDir.getAbsolutePath().toLowerCase(ROOT);
    ProcessHandle.allProcesses().forEach(ph -> terminateIfInDir(ph, targetPrefix));
  }

  private static void terminateIfInDir(ProcessHandle ph, String targetPrefix) {
    try {
      ph.info().command().ifPresent(cmd -> {
        if (cmd.toLowerCase(ROOT).startsWith(targetPrefix)) {
          log.info("Terminating lingering WebKit process (PID {}): {}", ph.pid(), cmd);
          ph.destroyForcibly();
          try {
            ph.onExit().get(5, SECONDS);
          }
          catch (Exception ignored) {
          }
        }
      });
    }
    catch (Exception ignored) {
    }
  }

  static boolean isInstalled(File targetDir) {
    File driverFile = new File(targetDir, DRIVER_EXE_NAME);
    File browserFile = new File(targetDir, BROWSER_EXE_NAME);
    return driverFile.exists() && driverFile.canExecute() && browserFile.exists();
  }

  private static void updateSystemProperties(File driverFile) {
    String absPath = driverFile.getAbsolutePath();
    if (!absPath.equals(System.getProperty(PROPERTY_DRIVER_PATH))) {
      System.setProperty(PROPERTY_DRIVER_PATH, absPath);
      log.debug("Updated system property '{}' = {}", PROPERTY_DRIVER_PATH, absPath);
    }
  }

  public static String resolveArchiveUrl() {
    String prop = System.getProperty(PROPERTY_ARCHIVE_URL);
    if (prop != null && !prop.isBlank()) {
      return prop;
    }
    String env = System.getenv(ENV_ARCHIVE_URL);
    if (env != null && !env.isBlank()) {
      return env;
    }
    return queryLatestBuildbotArchiveUrl();
  }

  public static String queryLatestBuildbotArchiveUrl() {
    String buildsBody = fetchUrlText(BUILDBOT_BUILDS_API, "Failed to query WebKit builds API");
    long buildNumber = findLatestGreenBuildNumber(buildsBody);
    log.info("Found latest successful WebKit build on Buildbot: #{}", buildNumber);

    String stepsUrl = String.format(BUILDBOT_STEPS_API_TEMPLATE, buildNumber);
    String stepsBody = fetchUrlText(stepsUrl, "Failed to query WebKit build steps for build #" + buildNumber);
    return findArchiveUrlInSteps(stepsBody, buildNumber);
  }

  private static String fetchUrlText(String url, String errorPrefix) {
    try {
      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .GET()
        .header("Accept", "application/json")
        .timeout(Duration.ofSeconds(20))
        .build();

      HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new WebDriverException(errorPrefix + ": HTTP " + response.statusCode());
      }
      return response.body();
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new WebDriverException("Interrupted while querying WebKit Buildbot API", e);
    }
    catch (IOException e) {
      throw new WebDriverException("I/O error while querying WebKit Buildbot API", e);
    }
  }

  @SuppressWarnings("unchecked")
  static long findLatestGreenBuildNumber(String buildsJson) {
    Map<String, Object> buildsRoot = JSON.toType(buildsJson, Json.MAP_TYPE);
    List<Map<String, Object>> builds = (List<Map<String, Object>>) buildsRoot.get("builds");
    if (builds != null) {
      for (Map<String, Object> build : builds) {
        Long number = extractGreenBuildNumber(build);
        if (number != null) {
          return number;
        }
      }
    }
    throw new WebDriverException("No successful Windows build found in recent WebKit Buildbot builds.");
  }

  @Nullable
  private static Long extractGreenBuildNumber(Map<String, Object> build) {
    boolean isComplete = Boolean.TRUE.equals(build.get("complete"));
    Number results = (Number) build.get("results");
    Number number = (Number) build.get("number");
    if (isComplete && results != null && results.intValue() == 0 && number != null) {
      return number.longValue();
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  static String findArchiveUrlInSteps(String stepsJson, long buildNumber) {
    Map<String, Object> stepsRoot = JSON.toType(stepsJson, Json.MAP_TYPE);
    List<Map<String, Object>> steps = (List<Map<String, Object>>) stepsRoot.get("steps");
    if (steps != null) {
      for (Map<String, Object> step : steps) {
        String archiveUrl = extractArchiveUrlFromStep(step);
        if (archiveUrl != null) {
          return archiveUrl;
        }
      }
    }
    throw new WebDriverException("Could not find archive URL in compile-webkit step of build #" + buildNumber);
  }

  @Nullable
  @SuppressWarnings("unchecked")
  private static String extractArchiveUrlFromStep(Map<String, Object> step) {
    if (!"compile-webkit".equals(step.get("name"))) {
      return null;
    }
    List<Map<String, Object>> urls = (List<Map<String, Object>>) step.get("urls");
    if (urls == null) {
      return null;
    }
    for (Map<String, Object> urlEntry : urls) {
      if ("Archive".equalsIgnoreCase(String.valueOf(urlEntry.get("name")))) {
        String archiveUrl = (String) urlEntry.get("url");
        if (archiveUrl != null && !archiveUrl.isBlank()) {
          return archiveUrl;
        }
      }
    }
    return null;
  }

  private static void downloadFile(String url, File destination) throws IOException {
    try {
      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .GET()
        .timeout(Duration.ofMinutes(10))
        .build();

      HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new WebDriverException("Failed to download WebKit archive from " + url + ": HTTP " + response.statusCode());
      }

      long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
      try (InputStream input = response.body();
           FileOutputStream output = new FileOutputStream(destination)) {
        copyWithProgress(input, output, contentLength);
      }
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new WebDriverException("Interrupted while downloading WebKit archive", e);
    }
  }

  private static void copyWithProgress(InputStream input, FileOutputStream output, long contentLength) throws IOException {
    byte[] buffer = new byte[64 * 1024];
    int bytesRead;
    long totalBytesRead = 0L;
    long lastLogTime = System.currentTimeMillis();

    while ((bytesRead = input.read(buffer)) != -1) {
      output.write(buffer, 0, bytesRead);
      totalBytesRead += bytesRead;

      long now = System.currentTimeMillis();
      if (now - lastLogTime >= 5000) {
        logDownloadProgress(totalBytesRead, contentLength);
        lastLogTime = now;
      }
    }
  }

  private static void logDownloadProgress(long totalBytesRead, long contentLength) {
    long readMb = totalBytesRead / (1024 * 1024);
    long totalMb = contentLength > 0 ? contentLength / (1024 * 1024) : -1L;
    if (totalMb > 0) {
      long percent = (totalBytesRead * 100) / contentLength;
      log.info("Downloading WebKit: {} MB / {} MB ({}%)", readMb, totalMb, percent);
    }
    else {
      log.info("Downloading WebKit: {} MB downloaded...", readMb);
    }
  }

  static void extractArchive(File zipFile, File targetDir) throws IOException {
    String canonicalTargetDirPath = targetDir.getCanonicalPath();
    int extractedCount = 0;
    int skippedPdbCount = 0;

    try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile), 64 * 1024))) {
      byte[] buffer = new byte[64 * 1024];
      ZipEntry entry;

      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          String relativePath = resolveRelativeEntryPath(entry.getName());
          if (!relativePath.isBlank()) {
            if (relativePath.toLowerCase(ROOT).endsWith(".pdb")) {
              skippedPdbCount++;
            }
            else {
              writeZipEntry(zis, entry.getName(), relativePath, targetDir, canonicalTargetDirPath, buffer);
              extractedCount++;
            }
          }
        }
        zis.closeEntry();
      }
    }
    log.info("Extracted {} files into {} (skipped {} .pdb files)",
      extractedCount, targetDir.getAbsolutePath(), skippedPdbCount);
  }

  static String resolveRelativeEntryPath(String entryName) {
    String normalizedName = entryName.replace('\\', '/');
    while (normalizedName.startsWith("/")) {
      normalizedName = normalizedName.substring(1);
    }
    int slashIndex = normalizedName.indexOf('/');
    return slashIndex >= 0 ? normalizedName.substring(slashIndex + 1) : normalizedName;
  }

  private static void writeZipEntry(ZipInputStream zis, String entryName, String relativePath,
                                    File targetDir, String canonicalTargetDirPath, byte[] buffer) throws IOException {
    File outFile = new File(targetDir, relativePath);
    if (!outFile.getCanonicalPath().startsWith(canonicalTargetDirPath + File.separator)
        && !outFile.getCanonicalPath().equals(canonicalTargetDirPath)) {
      throw new WebDriverException("Zip entry is outside of the target dir: " + entryName);
    }
    File parent = outFile.getParentFile();
    if (parent != null) {
      mkdirsQuietly(parent);
    }
    try (FileOutputStream fos = new FileOutputStream(outFile)) {
      int len;
      while ((len = zis.read(buffer)) != -1) {
        fos.write(buffer, 0, len);
      }
    }
  }

  private static void createLauncherScript(File targetDir) {
    File launcherBat = new File(targetDir, "run-minibrowser.bat");
    String content = "@echo off\r\n"
      + "setlocal\r\n"
      + "cd /d \"%~dp0\"\r\n"
      + "set \"PATH=%~dp0;%PATH%\"\r\n"
      + "start \"\" \"%~dp0" + BROWSER_EXE_NAME + "\" %*\r\n"
      + "endlocal\r\n";
    try {
      Files.writeString(launcherBat.toPath(), content, UTF_8);
    }
    catch (Exception e) {
      log.warn("Could not create run-minibrowser.bat: {}", e.getMessage());
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private static void mkdirsQuietly(File dir) {
    dir.mkdirs();
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private static void deleteQuietly(File file) {
    if (file.exists()) {
      file.delete();
    }
  }
}
