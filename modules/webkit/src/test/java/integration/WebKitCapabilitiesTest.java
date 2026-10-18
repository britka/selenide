package integration;

import com.codeborne.selenide.CollectionCondition;
import com.codeborne.selenide.WebDriverRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Keys;

import java.io.File;
import java.time.Duration;

import static com.codeborne.selenide.Condition.attribute;
import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.hidden;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.CookieStoreConditions.cookie;
import static com.codeborne.selenide.Selectors.shadowCss;
import static com.codeborne.selenide.Selenide.*;
import static org.assertj.core.api.Assertions.assertThat;

final class WebKitCapabilitiesTest extends BaseWebKitTest {
  @BeforeEach
  void openCapabilitiesPlayground() {
    open("/capabilities.html");
  }

  @Test
  void shouldWaitForDynamicAsyncContent() {
    $("#loading-spinner").shouldBe(hidden);
    $("#async-result").shouldBe(hidden);

    $("#load-data-btn").shouldBe(visible).click();

    $("#async-result")
      .shouldBe(visible)
      .shouldHave(exactText("Async payload loaded in WebKit!"));
    $("#loading-spinner").shouldBe(hidden);
  }

  @Test
  void shouldFilterAndInspectElementsCollection() {
    var items = $$(".framework-item");
    items.shouldHave(CollectionCondition.exactTexts("WebKit", "Selenide", "Selenium", "JUnit 6"));

    items.findBy(attribute("data-category", "browser"))
      .shouldHave(exactText("WebKit"));

    $("#search-filter").setValue("selen");
    items.filterBy(visible)
      .shouldHave(CollectionCondition.exactTexts("Selenide", "Selenium"));

    $("#search-filter").clear();
    executeJavaScript("arguments[0].dispatchEvent(new Event('input', {bubbles: true}));", $("#search-filter"));
    items.filterBy(visible).shouldHave(CollectionCondition.size(4));
  }

  @Test
  void shouldHandleHoverDoubleClickRightClickAndKeyboardKeys() {
    var box = $("#interactive-box").shouldBe(visible).scrollIntoView(true);
    var mouseLog = $("#mouse-event-log").shouldHave(exactText("No event"));

    box.hover();
    box.shouldHave(cssClass("hovered"));
    mouseLog.shouldHave(exactText("Hovered"));

    box.doubleClick();
    mouseLog.shouldHave(exactText("Double-Clicked"));

    box.contextClick();
    mouseLog.shouldHave(exactText("Right-Clicked"));

    var keyInput = $("#key-input").shouldBe(visible);
    var keyLog = $("#key-event-log").shouldHave(exactText("Last key: none"));

    keyInput.sendKeys(Keys.ENTER);
    keyLog.shouldHave(exactText("Last key: Enter"));

    keyInput.sendKeys(Keys.ESCAPE);
    keyLog.shouldHave(exactText("Last key: Escape"));
  }

  @Test
  void shouldSwitchToIframeAndBackToDefaultContent() {
    switchTo().frame("test-iframe");
    try {
      $("#iframe-heading").shouldBe(visible).shouldHave(exactText("Inside Iframe"));
      $("#iframe-input").shouldBe(visible).setValue("Hello from WebKit Iframe");
      $("#iframe-btn").click();
      $("#iframe-status").shouldHave(exactText("Clicked: Hello from WebKit Iframe"));
    } finally {
      switchTo().defaultContent();
    }

    $("#page-title").shouldBe(visible).shouldHave(exactText("WebKit Capabilities Playground"));
  }

  @Test
  void shouldInteractWithShadowDomElements() {
    $(shadowCss("#shadow-text", "#shadow-host"))
      .shouldBe(visible)
      .shouldHave(exactText("Shadow DOM Initial"));

    $(shadowCss("#shadow-btn", "#shadow-host"))
      .shouldBe(visible)
      .click();

    $(shadowCss("#shadow-text", "#shadow-host"))
      .shouldHave(exactText("Shadow DOM Activated!"));
  }

  @Test
  void shouldManageCookiesLocalStorageSessionStorageAndAsyncJs() {
    localStorage().setItem("webkit_key", "webkit_local_value");
    assertThat(localStorage().getItem("webkit_key")).isEqualTo("webkit_local_value");
    localStorage().removeItem("webkit_key");

    sessionStorage().setItem("session_token", "abc-123");
    assertThat(sessionStorage().getItem("session_token")).isEqualTo("abc-123");
    sessionStorage().clear();

    cookies().add(new Cookie("webkit_cookie", "cookie_val_42"));
    Cookie cookie = cookies().get("webkit_cookie");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getValue()).isEqualTo("cookie_val_42");


// TODO delete cookie is not working now
//    WebDriverRunner.getWebDriver().manage().deleteCookieNamed("webkit_cookie");//
//    cookies().object().delete("webkit_cookie");

    executeJavaScript("document.cookie = '" + cookie.getName() + "=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;'");
    cookies().shouldNotHave(cookie("webkit_cookie"));

    cookies().add(cookie);
    assertThat(cookies().size()).isEqualTo(1);

    cookies().clear();
    assertThat(cookies().size()).isEqualTo(0);

    String asyncResult = executeAsyncJavaScript(
      "const callback = arguments[arguments.length - 1];" +
        "setTimeout(() => callback('async-js-ok'), 100);"
    );
    assertThat(asyncResult).isEqualTo("async-js-ok");
  }

  @Test
  void shouldCaptureViewportAndElementScreenshots() {
    String pageShotPath = screenshot("webkit-full-page");
    assertThat(pageShotPath).isNotNull();

    File elementShot = $("#badge-card").shouldBe(visible).screenshot();
    assertThat(elementShot).isNotNull().exists();
    assertThat(elementShot.length()).isPositive();
  }

  @Test
  void shouldResizeAndQueryBrowserWindow() {
    var window = WebDriverRunner.getWebDriver().manage().window();
    window.setSize(new Dimension(1280, 800));

    Dimension size = window.getSize();
    assertThat(size.getWidth()).isGreaterThanOrEqualTo(1000);
    assertThat(size.getHeight()).isGreaterThanOrEqualTo(600);
  }
}
