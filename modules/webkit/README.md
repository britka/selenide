# Selenide WebKit (`selenide-webkit`)

Plugin for running Selenide and Selenium WebDriver automated tests against native **WebKit**:
- **Windows**: `MiniBrowser.exe` + `WebDriver.exe` (automatically downloaded and cached from WebKit Buildbot)
- **Linux / WSL2**: `MiniBrowser` + `WebKitWebDriver` (`webkit2gtk-driver`)

## Dependency

### Gradle
```groovy
testImplementation 'com.codeborne:selenide-webkit:7.19.1'
```

### Maven
```xml
<dependency>
  <groupId>com.codeborne</groupId>
  <artifactId>selenide-webkit</artifactId>
  <version>7.19.1</version>
  <scope>test</scope>
</dependency>
```

## Usage

### 1. Selenide short browser name (Recommended)

```java
import com.codeborne.selenide.Browsers;
import com.codeborne.selenide.Configuration;

import static com.codeborne.selenide.Selenide.open;

Configuration.browser = Browsers.WEBKIT; // or "webkit"
open("https://example.com");
```

### 2. Selenide via `WebKitDriverFactory`

```java
import com.codeborne.selenide.Configuration;
import org.selenide.webkit.WebKitDriverFactory;

Configuration.browser = WebKitDriverFactory.BROWSER;
```

### 3. Plain Selenium WebDriver

```java
import org.openqa.selenium.WebDriver;
import org.selenide.webkit.WebKitDriver;

WebDriver driver = new WebKitDriver();
try {
  driver.get("https://example.com");
} finally {
  driver.quit();
}
```

## Installing WebKit

### Windows
No manual installation is required. On the first test run, `WebKitManager` queries the official WebKit Buildbot API (`builder 1192`), downloads the latest green Windows 64-bit release archive, and extracts `WebDriver.exe` and `MiniBrowser.exe` (skipping `.pdb` debug symbols) into `%LOCALAPPDATA%\WebKit`.

### Linux & WSL2 (WebKitGTK)
Install the native `webkit2gtk-driver` package:

```bash
# Ubuntu / Debian / WSL2
sudo apt update
sudo apt install -y webkit2gtk-driver xvfb
```

## Configuration (System Properties & Environment Variables)

| System Property | Environment Variable | Description | Default |
| :--- | :--- | :--- | :--- |
| `webdriver.webkit.driver` | `WEBKIT_DRIVER_EXE` | Path to an existing `WebDriver.exe` / `WebKitWebDriver` executable or target directory | `%LOCALAPPDATA%\WebKit` |
| `webdriver.webkit.url` | `WEBKIT_ARCHIVE_URL` | Direct URL to a WebKit `.zip` build archive (bypasses Buildbot API lookup) | Latest green build from Buildbot API |
| `webdriver.webkit.update` | `WEBKIT_UPDATE` | Force-clear the local cache and download the latest WebKit build (`true` / `false`) | `false` |
| `webdriver.webkit.logfile` | — | Path to the log file for `WebDriver` output | — |
