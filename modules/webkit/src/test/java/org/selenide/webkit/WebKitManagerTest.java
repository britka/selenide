package org.selenide.webkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.WebDriverException;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SystemStubsExtension.class)
final class WebKitManagerTest {
  @SystemStub
  private SystemProperties systemProperties;

  @Test
  void resolvesDefaultCacheDir() {
    File cacheDir = WebKitManager.getCacheDir();
    assertThat(cacheDir.getName()).isEqualTo("WebKit");
  }

  @Test
  void resolvesCustomTargetDirFromDirectoryProperty(@TempDir Path tempDir) {
    systemProperties.set(WebKitManager.PROPERTY_DRIVER_PATH, tempDir.toString());
    assertThat(WebKitManager.resolveTargetDir()).isEqualTo(tempDir.toFile().getAbsoluteFile());
  }

  @Test
  void resolvesParentTargetDirFromExecutableProperty(@TempDir Path tempDir) {
    File exe = tempDir.resolve(WebKitManager.DRIVER_EXE_NAME).toFile();
    systemProperties.set(WebKitManager.PROPERTY_DRIVER_PATH, exe.getAbsolutePath());
    assertThat(WebKitManager.resolveTargetDir()).isEqualTo(tempDir.toFile().getAbsoluteFile());
  }

  @Test
  void checksUpdateRequestedProperty() {
    systemProperties.set(WebKitManager.PROPERTY_UPDATE_WEBKIT, "true");
    assertThat(WebKitManager.isUpdateRequested()).isTrue();

    systemProperties.set(WebKitManager.PROPERTY_UPDATE_WEBKIT, "false");
    assertThat(WebKitManager.isUpdateRequested()).isFalse();
  }

  @Test
  void resolvesArchiveUrlFromSystemProperty() {
    systemProperties.set(WebKitManager.PROPERTY_ARCHIVE_URL, "https://example.com/webkit.zip");
    assertThat(WebKitManager.resolveArchiveUrl()).isEqualTo("https://example.com/webkit.zip");
  }

  @Test
  void parsesLatestGreenBuildNumberFromBuildbotJson() {
    String json = """
      {
        "builds": [
          {"number": 2050, "complete": false, "results": null},
          {"number": 2049, "complete": true, "results": 2},
          {"number": 2048, "complete": true, "results": 0}
        ]
      }
      """;
    assertThat(WebKitManager.findLatestGreenBuildNumber(json)).isEqualTo(2048L);
  }

  @Test
  void throwsWhenNoGreenBuildFound() {
    String json = """
      {
        "builds": [
          {"number": 2050, "complete": false, "results": null},
          {"number": 2049, "complete": true, "results": 2}
        ]
      }
      """;
    assertThatThrownBy(() -> WebKitManager.findLatestGreenBuildNumber(json))
      .isInstanceOf(WebDriverException.class)
      .hasMessageContaining("No successful Windows build found");
  }

  @Test
  void parsesArchiveUrlFromBuildStepsJson() {
    String json = """
      {
        "steps": [
          {"name": "git", "urls": []},
          {
            "name": "compile-webkit",
            "urls": [
              {"name": "Log", "url": "https://build.webkit.org/log"},
              {"name": "Archive", "url": "https://s3.amazonaws.com/archives.webkit.org/win-x86_64-release/12345.zip"}
            ]
          }
        ]
      }
      """;
    assertThat(WebKitManager.findArchiveUrlInSteps(json, 2048L))
      .isEqualTo("https://s3.amazonaws.com/archives.webkit.org/win-x86_64-release/12345.zip");
  }

  @Test
  void extractsArchiveSkippingPdbAndStrippingTopLevelDir(@TempDir Path tempDir) throws IOException {
    File zipFile = tempDir.resolve("webkit.zip").toFile();
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
      writeEntry(zos, "bin64/WebDriver.exe", "driver-binary");
      writeEntry(zos, "bin64/MiniBrowser.exe", "browser-binary");
      writeEntry(zos, "bin64/WebKit.pdb", "debug-symbols");
      writeEntry(zos, "bin64/sub/helper.dll", "helper-dll");
    }

    File targetDir = tempDir.resolve("extracted").toFile();
    WebKitManager.extractArchive(zipFile, targetDir);

    assertThat(new File(targetDir, "WebDriver.exe")).exists().hasContent("driver-binary");
    assertThat(new File(targetDir, "MiniBrowser.exe")).exists().hasContent("browser-binary");
    assertThat(new File(targetDir, "sub/helper.dll")).exists().hasContent("helper-dll");
    assertThat(new File(targetDir, "WebKit.pdb")).doesNotExist();
  }

  @Test
  void rejectsZipSlipEntries(@TempDir Path tempDir) throws IOException {
    File zipFile = tempDir.resolve("malicious.zip").toFile();
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
      writeEntry(zos, "bin64/../../evil.exe", "evil");
    }

    File targetDir = tempDir.resolve("extracted").toFile();
    assertThatThrownBy(() -> WebKitManager.extractArchive(zipFile, targetDir))
      .isInstanceOf(WebDriverException.class)
      .hasMessageContaining("Zip entry is outside of the target dir");
  }

  private static void writeEntry(ZipOutputStream zos, String name, String content) throws IOException {
    zos.putNextEntry(new ZipEntry(name));
    zos.write(content.getBytes(UTF_8));
    zos.closeEntry();
  }
}
