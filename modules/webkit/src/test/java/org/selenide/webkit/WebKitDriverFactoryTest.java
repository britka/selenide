package org.selenide.webkit;

import com.codeborne.selenide.Browser;
import com.codeborne.selenide.SelenideConfig;
import com.codeborne.selenide.webdriver.DefaultDriverFactory;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Proxy;

import java.io.File;
import java.util.Map;

import static com.codeborne.selenide.Browsers.WEBKIT;
import static java.lang.Boolean.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openqa.selenium.remote.CapabilityType.ACCEPT_INSECURE_CERTS;
import static org.openqa.selenium.remote.CapabilityType.PROXY;

final class WebKitDriverFactoryTest {
  private final SelenideConfig config = new SelenideConfig().browser(WEBKIT);
  private final Browser browser = new Browser(config.browser(), config.headless());
  private final WebKitDriverFactory factory = new WebKitDriverFactory();
  private final File downloadsFolder = new File("build/downloads");

  @Test
  void setsBrowserNameToMiniBrowserAndClearsWebSocketUrl() {
    MutableCapabilities capabilities = factory.createCapabilities(config, browser, null, downloadsFolder);

    assertThat(capabilities.getBrowserName()).isEqualTo("MiniBrowser");
    assertThat(capabilities.getCapability("webSocketUrl")).isNull();
    assertThat(capabilities.getCapability(ACCEPT_INSECURE_CERTS)).isEqualTo(TRUE);
  }

  @Test
  void setsProxyAndMergesCustomCapabilities() {
    Proxy proxy = new Proxy().setHttpProxy("127.0.0.1:8888");
    config.browserCapabilities(new MutableCapabilities(Map.of("custom:cap", "val")));

    MutableCapabilities capabilities = factory.createCapabilities(config, browser, proxy, downloadsFolder);

    assertThat(capabilities.getCapability(PROXY)).isEqualTo(proxy);
    assertThat(capabilities.getCapability("custom:cap")).isEqualTo("val");
    assertThat(capabilities.getBrowserName()).isEqualTo("MiniBrowser");
  }

  @Test
  void defaultDriverFactoryResolvesWebKitShortName() {
    DefaultDriverFactory defaultDriverFactory = new DefaultDriverFactory();

    MutableCapabilities capabilities = defaultDriverFactory.createCapabilities(config, browser, null, downloadsFolder);
    assertThat(capabilities.getBrowserName()).isEqualTo("MiniBrowser");
  }

  @Test
  void mergesCapabilitiesWithDefaultInWebKitDriver() {
    MutableCapabilities custom = new MutableCapabilities(Map.of("foo", "bar"));
    var merged = WebKitDriver.mergeWithDefaultCapabilities(custom);

    assertThat(merged.getBrowserName()).isEqualTo("MiniBrowser");
    assertThat(merged.getCapability("foo")).isEqualTo("bar");
  }
}
