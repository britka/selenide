# Selenide WebKit Module (`selenide-webkit`) — Design Spec

## Goal

Add native WebKit browser automation support to Selenide as a new module `:modules:webkit` (published artifact `com.codeborne:selenide-webkit`), migrating and adapting the solution from `webkit-automation-project-for-selenium`.

Users will be able to run Selenide tests against native WebKit:
- **Windows**: `MiniBrowser.exe` + `WebDriver.exe` (automatically discovered, downloaded from the official WebKit Buildbot API, unpacked without `.pdb` files, and cached in `%LOCALAPPDATA%\WebKit`).
- **Linux / WSL2**: `MiniBrowser` + `WebKitWebDriver` (`webkit2gtk-driver`, located on `PATH` or standard system paths).

Success criteria:
1. Users can configure WebKit using either `Configuration.browser = "webkit"` (`Browsers.WEBKIT`) or `Configuration.browser = WebKitDriverFactory.BROWSER`.
2. Missing `com.codeborne:selenide-webkit` dependency when `Configuration.browser = "webkit"` produces a clear `IllegalStateException` instructing the user to add `com.codeborne:selenide-webkit`.
3. All classes in `:modules:webkit` conform to Selenide's Java 17 toolchain, Checkstyle (`maxWarnings = 0`, 2-space indent, max line length 136, max cyclomatic complexity 11), SpotBugs (`Effort.MAX`), JSpecify `@NullMarked`/`@Nullable`, and ArchUnit (`assertThat` from AssertJ, no JUnit assertions).
4. Fast unit tests run as part of `./gradlew check` without launching a browser or hitting the network.
5. Browser integration tests (migrated WebKit sandbox tests + Selenide's `integration.*` suite) can be executed via `./gradlew webkit`.

---

## Scope & Architecture

### 1. Core Browser Registration (`:modules:core`)

To support the short name `Configuration.browser = "webkit"` without introducing a circular dependency from `:modules:core` to `:modules:webkit`:

- **`com.codeborne.selenide.Browsers`**:
  - Add constant `String WEBKIT = "webkit";`
- **`com.codeborne.selenide.Browser`**:
  - Add method `public boolean isWebkit() { return WEBKIT.equalsIgnoreCase(name); }`
- **`com.codeborne.selenide.webdriver.DefaultDriverFactory`**:
  - Map `Browsers.WEBKIT` (`"webkit"`) to class name `"org.selenide.webkit.WebKitDriverFactory"`.
  - When `WEBKIT.equalsIgnoreCase(className)` and `Class.forName("org.selenide.webkit.WebKitDriverFactory")` throws `ClassNotFoundException`, throw:
    ```java
    new IllegalStateException(
      "WebKit driver not found. Add dependency \"com.codeborne:selenide-webkit\" to your project.", e)
    ```

### 2. New Gradle Module (`:modules:webkit`)

- **Gradle coordinates**: `com.codeborne:selenide-webkit` (`artifactId = 'selenide-webkit'` in `modules/webkit/build.gradle`, included in `settings.gradle` as `include ':modules:webkit'`).
- **Dependencies**:
  - `api project(':statics')` (provides `:modules:core`, Selenium, SLF4J, JSpecify, ErrorProne annotations).
  - Zero extra runtime dependencies (uses `java.net.http.HttpClient` and Selenium's `org.openqa.selenium.json.Json`).
- **Package**: `org.selenide.webkit` annotated with `@CheckReturnValue` and `@NullMarked` in `package-info.java`.

### 3. Classes in `org.selenide.webkit`

1. **`WebKitManager`**:
   - Resolves WebKit installation directory (`%LOCALAPPDATA%\WebKit` on Windows, `~/.webkit/WebKit` elsewhere, or custom path via `-Dwebdriver.webkit.driver` / `WEBKIT_DRIVER_EXE`).
   - On Linux/WSL: resolves `WebKitWebDriver` from custom path, `PATH`, or `/usr/bin`, `/usr/local/bin`, `/bin`.
   - On Windows: synchronizes installation across threads and processes using a `.webkit-install.lock` file lock, queries WebKit Buildbot REST API (`builder 1192`) for the latest green release build (or uses `-Dwebdriver.webkit.url` / `WEBKIT_ARCHIVE_URL`), downloads the `.zip` archive, extracts files while skipping `.pdb` symbols and preventing Zip Slip, and supports forced updates via `-Dwebdriver.webkit.update=true` / `WEBKIT_UPDATE=true`.
   - Refactored into small, focused methods so every method has cyclomatic complexity $\le 11$.

2. **`WebKitDriverService`** (extends `org.openqa.selenium.remote.service.DriverService`):
   - Registered via `META-INF/services/org.openqa.selenium.remote.service.DriverService$Builder`.
   - Allocates collision-free local ports (`PortProber.findFreePort()`).
   - On Windows (when not headless and no custom `--target` is set): pre-allocates a unique inspector port, launches `MiniBrowser.exe` with `about://blank` and `-WindowStyle Hidden` (`SW_HIDE`) so the unused bootstrap window stays hidden while the WebDriver-controlled window opens visibly (`SW_SHOW`), waits non-intrusively (`ServerSocket` bind check) until `127.0.0.1:<inspectorPort>` is listening, and passes `--target=127.0.0.1:<inspectorPort>` to `WebDriver.exe`.
   - Prepends the driver directory to `PATH` so child processes locate bundled DLLs; sets `DISPLAY=:0` on Linux/WSLg when `/tmp/.X11-unix/X0` is present and `DISPLAY` is unset.
   - Registers a JVM shutdown hook and forcibly cleans up `MiniBrowser.exe` and its descendant processes on `stop()`.

3. **`WebKitDriver`** (extends `org.openqa.selenium.remote.RemoteWebDriver`):
   - Default capability `browserName = "MiniBrowser"`.
   - Retries `startSession` up to 3 times if the inspector port reports `"is not reachable"` during cold start.
   - Stops `WebKitDriverService` in `quit()`.

4. **`WebKitDriverFactory`** (extends `com.codeborne.selenide.webdriver.AbstractDriverFactory`):
   - Public constant `public static final String BROWSER = WebKitDriverFactory.class.getName();`.
   - Implements `createCapabilities(Config, Browser, Proxy, File)` — merges Selenide common capabilities, preserves `browserName = "MiniBrowser"`, and clears `webSocketUrl` (BiDi is not supported by Windows `WebDriver.exe`).
   - Implements `create(Config, Browser, Proxy, File)` — builds `WebKitDriverService` (with Selenide webdriver log integration via `withLog(config, ...)`) and returns `new WebKitDriver(driverService, capabilities)`.
   - Note: `WebKitDriverProvider` from the prototype project is intentionally omitted in favor of `WebKitDriverFactory`.

---

## Testing Strategy

### 1. Fast Unit Tests (`./gradlew check`)
Located in `modules/webkit/src/test/java/org/selenide/webkit/`:
- `WebKitManagerTest` — tests OS detection helpers, cache/target directory resolution with system properties, update flag resolution, archive extraction (including skipping `.pdb` files and rejecting Zip Slip paths), and Buildbot JSON response parsing via a local mock HTTP server or extracted parser methods.
- `WebKitDriverServiceTest` — tests `Builder.score(Capabilities)`, `Builder.createArgs()` on Windows/Linux/headless/custom target, and unique port allocation.
- `WebKitDriverFactoryTest` — tests `createCapabilities()` (browserName, proxy, disabled `webSocketUrl`, custom capabilities merge).
- In `:modules:core`:
  - `BrowserTest` / `DefaultDriverFactoryTest` — verifies `new Browser("webkit", false).isWebkit()` and verifies that `DefaultDriverFactory` throws the expected `IllegalStateException` when `selenide-webkit` is not on `:modules:core`'s runtime classpath.

### 2. Browser Integration Tests (`./gradlew webkit`)
- In `gradle/tests.gradle`:
  - Register the `webkit` `Test` task (using `browserTestLock`, `selenide.browser = 'webkit'`, including `'integration/**/*'`, excluding `'com/codeborne/selenide/**/*'` and `'org/selenide/**/*'`, excluding JUnit tag `'update'` unless `-Dwebdriver.webkit.update=true`).
  - Forward `-Dwebdriver.*` system properties to test JVMs alongside `-Dselenide.*`.
- In `modules/webkit/src/test/java/integration/webkit/`:
  - Migrate `BaseWebKitTest`, `WebKitWebFormTest`, `WebKitCapabilitiesTest`, and `WebKitForceUpdateTest` (`@Tag("update")`), converting all JUnit `Assertions` to AssertJ `assertThat(...)` and formatting to Selenide checkstyle rules.
  - Copy sandbox HTML fixtures to `modules/webkit/src/test/resources/sandbox/`.
- Configure `modules/webkit/build.gradle` with `testImplementation` on `:statics` and `:modules:core` test outputs so `./gradlew :modules:webkit:webkit` can also run standard Selenide `integration.*` tests.
