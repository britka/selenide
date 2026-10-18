package org.selenide.webkit;

import com.codeborne.selenide.Browser;
import com.codeborne.selenide.Config;
import com.codeborne.selenide.webdriver.AbstractDriverFactory;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.File;

import static org.openqa.selenium.remote.CapabilityType.BROWSER_NAME;

/**
 * Native Selenide {@link com.codeborne.selenide.webdriver.DriverFactory} for WebKit ({@code MiniBrowser}).
 *
 * <p>Usage:
 * <pre>{@code
 * Configuration.browser = "webkit";
 * // or
 * Configuration.browser = WebKitDriverFactory.BROWSER;
 * }</pre>
 */
public class WebKitDriverFactory extends AbstractDriverFactory {
  public static final String BROWSER = WebKitDriverFactory.class.getName();

  @Override
  public MutableCapabilities createCapabilities(
    Config config,
    Browser browser,
    @Nullable Proxy proxy,
    @Nullable File browserDownloadsFolder
  ) {
    DesiredCapabilities capabilities = WebKitDriver.defaultCapabilities();
    MutableCapabilities commonCapabilities = createCommonCapabilities(capabilities, config, browser, proxy);
    commonCapabilities.setCapability(BROWSER_NAME, WebKitDriver.MINI_BROWSER);
    commonCapabilities.setCapability("webSocketUrl", (Object) null);
    return commonCapabilities;
  }

  @Override
  public WebDriver create(
    Config config,
    Browser browser,
    @Nullable Proxy proxy,
    @Nullable File browserDownloadsFolder
  ) {
    WebKitDriverService driverService = createDriverService(config);
    MutableCapabilities capabilities = createCapabilities(config, browser, proxy, browserDownloadsFolder);
    return new WebKitDriver(driverService, capabilities);
  }

  protected WebKitDriverService createDriverService(Config config) {
    return withLog(config, new WebKitDriverService.Builder()
      .usingAnyFreePort()
      .withHeadless(config.headless()));
  }
}
