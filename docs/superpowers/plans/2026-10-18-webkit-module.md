# Selenide WebKit Module (`selenide-webkit`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `:modules:webkit` (`com.codeborne:selenide-webkit`) module for automating native WebKit (`MiniBrowser.exe` + `WebDriver.exe` on Windows via WebKit Buildbot, and `WebKitWebDriver` + `MiniBrowser` on Linux/WSL), with first-class support for `Configuration.browser = "webkit"`.

**Architecture:**
- `:modules:core` defines `Browsers.WEBKIT = "webkit"`, `Browser.isWebkit()`, and resolves `"webkit"` in `DefaultDriverFactory` to `org.selenide.webkit.WebKitDriverFactory` via reflection (throwing a clear `IllegalStateException` if `com.codeborne:selenide-webkit` is missing from the classpath).
- `:modules:webkit` provides `WebKitManager`, `WebKitDriverService`, `WebKitDriver`, and `WebKitDriverFactory` in package `org.selenide.webkit` (`@NullMarked`, `@CheckReturnValue`).
- `gradle/tests.gradle` adds the `webkit` browser test task and forwards `webdriver.*` system properties.

**Tech Stack:** Java 17, Selenide (`:statics` + `:modules:core`), Selenium 4.51.0, JUnit Jupiter 6.1.3, AssertJ 3.27.7, Mockito 5.24.0. Zero extra runtime dependencies.

**Spec:** `docs/superpowers/specs/2026-10-18-webkit-module-design.md`

---

## File Structure

### Core & Build Configuration

| File | Action | Purpose |
|------|--------|---------|
| `settings.gradle` | Modify | Add `include ':modules:webkit'` |
| `gradle/tests.gradle` | Modify | Register `webkit` test task and forward `webdriver.*` system properties |
| `src/main/java/com/codeborne/selenide/Browsers.java` | Modify | Add `String WEBKIT = "webkit"` |
| `src/main/java/com/codeborne/selenide/Browser.java` | Modify | Add `isWebkit()` |
| `src/main/java/com/codeborne/selenide/webdriver/DefaultDriverFactory.java` | Modify | Resolve `"webkit"` to `org.selenide.webkit.WebKitDriverFactory` with helpful error if dependency is missing |
| `src/test/java/com/codeborne/selenide/BrowserTest.java` | Modify | Unit test for `Browser.isWebkit()` |
| `src/test/java/com/codeborne/selenide/webdriver/DefaultDriverFactoryTest.java` | Create | Unit test verifying missing `selenide-webkit` error message |

### WebKit Module (`modules/webkit/`)

| File | Action | Purpose |
|------|--------|---------|
| `modules/webkit/build.gradle` | Create | Gradle module config and test dependencies |
| `modules/webkit/README.md` | Create | Module documentation (Windows & Linux setup, usage, properties) |
| `modules/webkit/src/main/java/org/selenide/webkit/package-info.java` | Create | `@CheckReturnValue` and `@NullMarked` package annotations |
| `modules/webkit/src/main/java/org/selenide/webkit/WebKitManager.java` | Create | Auto-discovery, Buildbot download, `.pdb`-free zip extraction, file-locked caching |
| `modules/webkit/src/main/java/org/selenide/webkit/WebKitDriverService.java` | Create | `DriverService` managing `WebDriver.exe` / `WebKitWebDriver` and `MiniBrowser.exe` (`SW_HIDE`) |
| `modules/webkit/src/main/java/org/selenide/webkit/WebKitDriver.java` | Create | `RemoteWebDriver` subclass for WebKit with cold-start retry and service cleanup |
| `modules/webkit/src/main/java/org/selenide/webkit/WebKitDriverFactory.java` | Create | Selenide `AbstractDriverFactory` implementation for WebKit |
| `modules/webkit/src/main/resources/META-INF/services/org.openqa.selenium.remote.service.DriverService$Builder` | Create | SPI registration for `WebKitDriverService.Builder` |

### Unit & Integration Tests in `modules/webkit/`

| File | Action | Purpose |
|------|--------|---------|
| `modules/webkit/src/test/java/org/selenide/webkit/WebKitManagerTest.java` | Create | Unit tests for path resolution, zip extraction, and Buildbot JSON parsing |
| `modules/webkit/src/test/java/org/selenide/webkit/WebKitDriverServiceTest.java` | Create | Unit tests for service builder scoring, CLI args, and port allocation |
| `modules/webkit/src/test/java/org/selenide/webkit/WebKitDriverFactoryTest.java` | Create | Unit tests for capabilities creation and driver factory wiring |
| `modules/webkit/src/test/java/integration/webkit/BaseWebKitTest.java` | Create | Local `SimpleFileServer` fixture setup for WebKit sandbox tests |
| `modules/webkit/src/test/java/integration/webkit/WebKitWebFormTest.java` | Create | End-to-end form interaction tests on WebKit (using AssertJ) |
| `modules/webkit/src/test/java/integration/webkit/WebKitCapabilitiesTest.java` | Create | Async content, Shadow DOM, iframe, mouse/keyboard, storage tests (using AssertJ) |
| `modules/webkit/src/test/java/integration/webkit/WebKitForceUpdateTest.java` | Create | `@Tag("update")` forced Buildbot download & E2E verification test |
| `modules/webkit/src/test/resources/sandbox/*.html` | Create | HTML test pages (`index.html`, `web-form.html`, `submitted-form.html`, `capabilities.html`, `iframe-content.html`) |

---

## Task 1: Add `Browsers.WEBKIT`, `Browser.isWebkit()`, and `DefaultDriverFactory` Support in `:modules:core`

**Files:**
- Modify: `src/main/java/com/codeborne/selenide/Browsers.java`
- Modify: `src/main/java/com/codeborne/selenide/Browser.java`
- Modify: `src/main/java/com/codeborne/selenide/webdriver/DefaultDriverFactory.java`
- Modify: `src/test/java/com/codeborne/selenide/BrowserTest.java`
- Create: `src/test/java/com/codeborne/selenide/webdriver/DefaultDriverFactoryTest.java`

- [x] **Step 1: Add `WEBKIT` constant to `Browsers.java`**

Update `src/main/java/com/codeborne/selenide/Browsers.java`:

```java
package com.codeborne.selenide;

/**
 * Constants for all browsers supported by Selenide out of the box
 */
public interface Browsers {
  String CHROME = "chrome";
  String IE = "ie";
  String INTERNET_EXPLORER = "internet explorer";
  String EDGE = "edge";
  String FIREFOX = "firefox";
  String SAFARI = "safari";
  String HTMLUNIT = "htmlunit";
  String WEBKIT = "webkit";
}
```

- [x] **Step 2: Add `isWebkit()` to `Browser.java`**

In `src/main/java/com/codeborne/selenide/Browser.java`, import `com.codeborne.selenide.Browsers.WEBKIT` and add:

```java
  public boolean isWebkit() {
    return WEBKIT.equalsIgnoreCase(name);
  }
```

- [x] **Step 3: Resolve `"webkit"` in `DefaultDriverFactory.java`**

In `src/main/java/com/codeborne/selenide/webdriver/DefaultDriverFactory.java`, map `WEBKIT` to `"org.selenide.webkit.WebKitDriverFactory"` and throw a clear `IllegalStateException` if the class is not found:

```java
  private static final String WEBKIT_DRIVER_FACTORY = "org.selenide.webkit.WebKitDriverFactory";

  private Class<?> classOf(String className) {
    if (WEBKIT.equalsIgnoreCase(className)) {
      try {
        return Class.forName(WEBKIT_DRIVER_FACTORY);
      }
      catch (ClassNotFoundException e) {
        throw new IllegalStateException(
          "WebKit driver not found. Add dependency \"com.codeborne:selenide-webkit\" to your project.", e);
      }
    }
    try {
      return Class.forName(className);
    }
    catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("Class not found: " + className, e);
    }
  }
```

- [x] **Step 4: Add unit tests in `:modules:core`**

Add `isWebkit()` assertion in `src/test/java/com/codeborne/selenide/BrowserTest.java`, and create `src/test/java/com/codeborne/selenide/webdriver/DefaultDriverFactoryTest.java` verifying that `new DefaultDriverFactory().createCapabilities(new SelenideConfig().browser("webkit"), new Browser("webkit", false), null, null)` throws `IllegalStateException` with message `"WebKit driver not found. Add dependency \"com.codeborne:selenide-webkit\" to your project."`.

- [x] **Step 5: Run `:modules:core:check`**

Run: `./gradlew :modules:core:check`
Expected: PASS with 0 Checkstyle/SpotBugs warnings.

---

## Task 2: Create `:modules:webkit` Gradle Module and `gradle/tests.gradle` Task

**Files:**
- Modify: `settings.gradle`
- Modify: `gradle/tests.gradle`
- Create: `modules/webkit/build.gradle`
- Create: `modules/webkit/src/main/java/org/selenide/webkit/package-info.java`
- Create: `modules/webkit/src/main/resources/META-INF/services/org.openqa.selenium.remote.service.DriverService$Builder`

- [x] **Step 1: Register `:modules:webkit` in `settings.gradle`**

Add `include ':modules:webkit'` to `settings.gradle`.

- [x] **Step 2: Create `modules/webkit/build.gradle`**

```groovy
ext {
  artifactId = 'selenide-webkit'
}

dependencies {
  api project(':statics')

  testImplementation rootProject.project(':statics').sourceSets.test.output
  testImplementation rootProject.project(':statics').sourceSets.test.compileClasspath
  testImplementation rootProject.project(':modules:core').sourceSets.test.output

  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testImplementation("org.assertj:assertj-core:$assertjVersion") {transitive = false}
  testImplementation("org.mockito:mockito-core:$mockitoVersion")
  testImplementation("uk.org.webcompere:system-stubs-jupiter:$systemStubsVersion")
}

apply from: rootProject.file('gradle/publish-module.gradle')
```

- [x] **Step 3: Add `webkit` task and `webdriver.*` property forwarding in `gradle/tests.gradle`**

In `gradle/tests.gradle`, add:

```groovy
  tasks.register("webkit", Test) {
    usesService(browserTestLock)
    systemProperties['selenide.browser'] = 'webkit'
    include 'integration/**/*'
    exclude 'com/codeborne/selenide/**/*'
    exclude 'org/selenide/**/*'
    useJUnitPlatform {
      if (System.getProperty('webdriver.webkit.update') != 'true') {
        excludeTags 'update'
      }
    }
    outputs.upToDateWhen {false}
  }
```

And update property forwarding in `tasks.withType(Test).configureEach`:

```groovy
    System.properties.stringPropertyNames()
      .findAll { it.startsWith("selenide.") || it.startsWith("webdriver.") }
      .forEach {
        println " set ${it} to ${System.getProperty(it)}"
        systemProperties[it] = System.getProperty(it)
      }
```

- [x] **Step 4: Create `package-info.java` and SPI service file**

Create `modules/webkit/src/main/java/org/selenide/webkit/package-info.java`:

```java
@CheckReturnValue
@NullMarked
package org.selenide.webkit;

import com.google.errorprone.annotations.CheckReturnValue;
import org.jspecify.annotations.NullMarked;
```

Create `modules/webkit/src/main/resources/META-INF/services/org.openqa.selenium.remote.service.DriverService$Builder`:

```text
org.selenide.webkit.WebKitDriverService$Builder
```

---

## Task 3: Implement `WebKitManager`, `WebKitDriverService`, `WebKitDriver`, and `WebKitDriverFactory`

**Files:**
- Create: `modules/webkit/src/main/java/org/selenide/webkit/WebKitManager.java`
- Create: `modules/webkit/src/main/java/org/selenide/webkit/WebKitDriverService.java`
- Create: `modules/webkit/src/main/java/org/selenide/webkit/WebKitDriver.java`
- Create: `modules/webkit/src/main/java/org/selenide/webkit/WebKitDriverFactory.java`

- [x] **Step 1: Migrate and refactor `WebKitManager.java`**

Adapt `WebKitManager.java` from `webkit-automation-project-for-selenium` to Selenide code style:
- 2-space indentation, max line length 136, static imports (`UTF_8`, `ROOT`, `SECONDS`).
- Decompose complex methods (`queryLatestBuildbotArchiveUrl`, `extractArchive`, `resolveLinuxDriver`, `downloadFile`) into smaller helper methods (`fetchJsonMap`, `findLatestGreenBuildNumber`, `findArchiveUrlInSteps`, `extractZipEntry`, `resolveRelativeEntryPath`, `logDownloadProgress`) so every method has CyclomaticComplexity $\le 11$ and low NPathComplexity.
- Add `@CanIgnoreReturnValue` on methods whose return value may be ignored (`getOrInstallDriver`, `updateDriver`, `install`).

- [x] **Step 2: Migrate and refactor `WebKitDriverService.java`**

Adapt `WebKitDriverService.java` to Selenide code style:
- 2-space indentation, `@Nullable` annotations on nullable fields/parameters (`host`, `target`, `browserHandle`, `shutdownHook`).
- Extract helpers in `start()`, `startManagedMiniBrowser()`, and `stopProcessesQuietly()` to keep cyclomatic complexity $\le 11$.
- Ensure `UTF_8` is explicitly passed when reading process `InputStream` bytes (`new String(..., UTF_8)`) to satisfy SpotBugs `DM_DEFAULT_ENCODING`.

- [x] **Step 3: Migrate `WebKitDriver.java` and `WebKitDriverFactory.java`**

Adapt `WebKitDriver.java` and `WebKitDriverFactory.java` to Selenide code style:
- Use `@Nullable` on `Proxy proxy`, `File browserDownloadsFolder`, and `Capabilities capabilities`.
- Ensure `WebKitDriverFactory.BROWSER` is `WebKitDriverFactory.class.getName()`.

---

## Task 4: Add Unit Tests for `:modules:webkit`

**Files:**
- Create: `modules/webkit/src/test/java/org/selenide/webkit/WebKitManagerTest.java`
- Create: `modules/webkit/src/test/java/org/selenide/webkit/WebKitDriverServiceTest.java`
- Create: `modules/webkit/src/test/java/org/selenide/webkit/WebKitDriverFactoryTest.java`

- [x] **Step 1: Create `WebKitManagerTest.java`**

Test without network or real browser:
- `resolveTargetDir()` with `-Dwebdriver.webkit.driver` pointing to a directory or `WebDriver.exe` file.
- `isUpdateRequested()` with `-Dwebdriver.webkit.update`.
- `extractArchive()` with a temporary `.zip` containing `bin64/WebDriver.exe`, `bin64/MiniBrowser.exe`, and `bin64/debug.pdb` (verifies `.pdb` is skipped and top-level folder is stripped).
- Zip Slip protection in `extractArchive()` when a zip entry contains `../evil.exe`.
- Buildbot JSON parsing (`findLatestGreenBuildNumber` and `findArchiveUrlInSteps`).

- [x] **Step 2: Create `WebKitDriverServiceTest.java` and `WebKitDriverFactoryTest.java`**

- In `WebKitDriverServiceTest`: verify `Builder.score()` returns `10` for `"MiniBrowser"` and `"webkit"` and `0` for `"chrome"`, verify `allocateUniqueFreePort()` returns valid ports, and verify `createArgs()` includes `--port`, `--host`, `--replace-on-new-session`, and `--target`.
- In `WebKitDriverFactoryTest`: verify `createCapabilities()` sets `browserName` to `"MiniBrowser"`, clears `webSocketUrl`, sets `acceptInsecureCerts` to `true`, and merges custom capabilities and proxy. Also verify `new WebDriverFactory()` resolves `config.browser("webkit")` to `WebKitDriverFactory`.

- [x] **Step 3: Run `./gradlew :modules:webkit:check` and `:statics:check`**

Run: `./gradlew :modules:webkit:check :statics:check`
Expected: All unit tests, Checkstyle, SpotBugs, and ArchUnit checks pass.

---

## Task 5: Migrate Sandbox Integration Tests & Documentation

**Files:**
- Create: `modules/webkit/src/test/resources/sandbox/*.html`
- Create: `modules/webkit/src/test/java/integration/webkit/BaseWebKitTest.java`
- Create: `modules/webkit/src/test/java/integration/webkit/WebKitWebFormTest.java`
- Create: `modules/webkit/src/test/java/integration/webkit/WebKitCapabilitiesTest.java`
- Create: `modules/webkit/src/test/java/integration/webkit/WebKitForceUpdateTest.java`
- Create: `modules/webkit/README.md`
- Modify: `README.md`

- [x] **Step 1: Copy sandbox HTML fixtures and migrate integration tests**

- Copy the 5 HTML files (`capabilities.html`, `iframe-content.html`, `index.html`, `submitted-form.html`, `web-form.html`) from `webkit-automation-project-for-selenium/src/test/resources/sandbox/` to `modules/webkit/src/test/resources/sandbox/`.
- Migrate `BaseWebKitTest`, `WebKitWebFormTest`, `WebKitCapabilitiesTest`, and `WebKitForceUpdateTest` into package `integration.webkit`.
- Resolve sandbox directory from classpath (`BaseWebKitTest.class.getResource("/sandbox")`) so it works regardless of working directory.
- Replace all `org.junit.jupiter.api.Assertions.*` calls with `org.assertj.core.api.Assertions.assertThat` to pass `ArchitecturalChecks.noJunitAsserts()`.
- Configure `Configuration.browser = WEBKIT` in `BaseWebKitTest`.

- [x] **Step 2: Add `modules/webkit/README.md` and update root `README.md`**

Document `com.codeborne:selenide-webkit` installation, `Configuration.browser = "webkit"`, Windows & Linux setup, and system properties.

- [x] **Step 3: Verify full build, Javadoc, and WebKit integration tests**

Run:
1. `./gradlew check`
2. `./gradlew javadocForSite`
3. `./gradlew :modules:webkit:webkit`
Expected: All checks, Javadoc generation, and WebKit browser tests pass with 0 warnings.
