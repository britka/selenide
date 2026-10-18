package integration;

import com.codeborne.selenide.Selenide;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.openqa.selenium.By;
import org.selenide.webkit.WebKitManager;

import java.io.File;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.selected;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.title;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("update")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
final class WebKitForceUpdateTest extends BaseWebKitTest {
  @Test
  void shouldForceUpdateWebKitDriverAndExecuteFullWebFormFlow() {
    Selenide.closeWebDriver();

    long beforeUpdateTimestamp = System.currentTimeMillis();

    System.setProperty(WebKitManager.PROPERTY_UPDATE_WEBKIT, "true");
    assertThat(WebKitManager.isUpdateRequested()).isTrue();

    open("/web-form.html");

    assertThat(WebKitManager.isUpdateRequested()).isFalse();

    File targetDir = WebKitManager.resolveTargetDir();
    File driverExe = new File(targetDir, WebKitManager.DRIVER_EXE_NAME);
    File browserExe = new File(targetDir, WebKitManager.BROWSER_EXE_NAME);
    assertThat(driverExe).exists();
    assertThat(driverExe.canExecute()).isTrue();
    assertThat(browserExe).exists();
    assertThat(driverExe.lastModified()).isGreaterThanOrEqualTo(beforeUpdateTimestamp - 2000);

    assertThat(title()).isEqualTo("Web form");
    $("h1").shouldBe(visible).shouldHave(exactText("Web form"));

    String userAgent = executeJavaScript("return navigator.userAgent;");
    assertThat(userAgent).contains("AppleWebKit");

    $("#my-text-id")
      .shouldBe(visible, enabled)
      .setValue("Updated WebKit Driver Test")
      .shouldHave(value("Updated WebKit Driver Test"));

    $(By.name("my-password"))
      .shouldBe(visible)
      .setValue("FreshInstallPass!")
      .shouldHave(value("FreshInstallPass!"));

    $(By.name("my-textarea"))
      .shouldBe(visible)
      .setValue("Verified full E2E flow after forced WebKit update")
      .shouldHave(value("Verified full E2E flow after forced WebKit update"));

    var select = $(By.name("my-select")).shouldBe(visible);
    select.selectOption("Two");
    select.getSelectedOption().shouldHave(exactText("Two")).shouldHave(value("2"));

    $("#my-check-2").click();
    $("#my-check-2").shouldBe(selected);
    $("#my-radio-2").click();
    $("#my-radio-2").shouldBe(selected);

    $("button[type='submit']").shouldBe(visible, enabled).click();

    $("h1").shouldBe(visible).shouldHave(exactText("Form submitted"));
    $("#message").shouldBe(visible).shouldHave(exactText("Received!"));
    assertThat(title()).isEqualTo("Web form - target page");
  }
}
