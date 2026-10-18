package org.selenide.webkit;

import org.jspecify.annotations.Nullable;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.service.DriverCommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * A WebDriver implementation that controls native WebKit ({@code MiniBrowser})
 * via {@link WebKitDriverService}.
 */
public class WebKitDriver extends RemoteWebDriver {
  private static final Logger log = LoggerFactory.getLogger(WebKitDriver.class);
  private static final int MAX_SESSION_ATTEMPTS = 3;
  public static final String MINI_BROWSER = "MiniBrowser";

  private final WebKitDriverService service;

  public WebKitDriver() {
    this(WebKitDriverService.createDefaultService(), defaultCapabilities());
  }

  public WebKitDriver(WebKitDriverService service) {
    this(service, defaultCapabilities());
  }

  public WebKitDriver(@Nullable Capabilities capabilities) {
    this(WebKitDriverService.createDefaultService(), mergeWithDefaultCapabilities(capabilities));
  }

  public WebKitDriver(WebKitDriverService service, @Nullable Capabilities capabilities) {
    super(new DriverCommandExecutor(service), mergeWithDefaultCapabilities(capabilities));
    this.service = service;
  }

  @Override
  protected void startSession(Capabilities capabilities) {
    SessionNotCreatedException lastException = null;
    for (int attempt = 1; attempt <= MAX_SESSION_ATTEMPTS; attempt++) {
      try {
        super.startSession(capabilities);
        return;
      }
      catch (SessionNotCreatedException e) {
        lastException = e;
        if (attempt < MAX_SESSION_ATTEMPTS && isPortNotReachable(e)) {
          log.warn("WebKit browser inspector port was not ready yet (attempt {}/{}), retrying in 1s...",
            attempt, MAX_SESSION_ATTEMPTS);
          sleepBeforeRetry(e);
        }
        else {
          throw e;
        }
      }
    }
    throw Objects.requireNonNull(lastException);
  }

  private static boolean isPortNotReachable(SessionNotCreatedException e) {
    String msg = e.getMessage();
    return msg != null && msg.contains("is not reachable");
  }

  private static void sleepBeforeRetry(SessionNotCreatedException cause) {
    try {
      Thread.sleep(1000);
    }
    catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw cause;
    }
  }

  public static DesiredCapabilities defaultCapabilities() {
    DesiredCapabilities caps = new DesiredCapabilities();
    caps.setBrowserName(MINI_BROWSER);
    return caps;
  }

  static Capabilities mergeWithDefaultCapabilities(@Nullable Capabilities capabilities) {
    DesiredCapabilities merged = defaultCapabilities();
    if (capabilities != null) {
      merged.merge(capabilities);
      if (merged.getBrowserName() == null || merged.getBrowserName().isBlank()) {
        merged.setBrowserName(MINI_BROWSER);
      }
    }
    return merged;
  }

  @Override
  public void quit() {
    try {
      super.quit();
    }
    catch (Exception e) {
      log.debug("Ignored exception while quitting WebKit session: {}", e.getMessage());
    }
    finally {
      service.stop();
    }
  }
}
