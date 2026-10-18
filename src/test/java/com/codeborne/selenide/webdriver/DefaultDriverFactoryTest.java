package com.codeborne.selenide.webdriver;

import com.codeborne.selenide.Browser;
import com.codeborne.selenide.SelenideConfig;
import org.junit.jupiter.api.Test;

import static com.codeborne.selenide.Browsers.WEBKIT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class DefaultDriverFactoryTest {
  private final DefaultDriverFactory factory = new DefaultDriverFactory();

  @Test
  void reportsMissingWebKitDependency() {
    SelenideConfig config = new SelenideConfig().browser(WEBKIT);
    Browser browser = new Browser(config.browser(), config.headless());

    assertThatThrownBy(() -> factory.createCapabilities(config, browser, null, null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("WebKit driver not found. Add dependency \"com.codeborne:selenide-webkit\" to your project.")
      .hasCauseInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void reportsUnknownBrowserClass() {
    SelenideConfig config = new SelenideConfig().browser("org.example.NonExistentDriver");
    Browser browser = new Browser(config.browser(), config.headless());

    assertThatThrownBy(() -> factory.createCapabilities(config, browser, null, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Class not found: org.example.NonExistentDriver")
      .hasCauseInstanceOf(ClassNotFoundException.class);
  }
}
