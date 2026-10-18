package integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.codeborne.selenide.Condition.attribute;
import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.hidden;
import static com.codeborne.selenide.Condition.readonly;
import static com.codeborne.selenide.Condition.selected;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.back;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.forward;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.refresh;
import static com.codeborne.selenide.Selenide.title;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

final class WebKitWebFormTest extends BaseWebKitTest {
  @BeforeEach
  void openWebForm() {
    open("/web-form.html");
  }

  @Test
  void shouldVerifyWebKitBrowserEngineAndTitle() {
    assertThat(title()).isEqualTo("Web form");
    $("h1").shouldBe(visible).shouldHave(exactText("Web form"));

    String userAgent = executeJavaScript("return navigator.userAgent;");
    assertThat(userAgent).contains("AppleWebKit");
  }

  @Test
  void shouldFillTextInputsAndTextarea() {
    $("#my-text-id")
      .shouldBe(visible, enabled)
      .shouldHave(attribute("myprop", "myvalue"))
      .setValue("Hello WebKit");
    $("#my-text-id").shouldHave(value("Hello WebKit"));

    $(By.name("my-password"))
      .shouldBe(visible)
      .setValue("Secret123!");
    $(By.name("my-password")).shouldHave(value("Secret123!"));

    $(By.name("my-textarea"))
      .shouldBe(visible, empty)
      .setValue("Multiline text in WebKit MiniBrowser");
    $(By.name("my-textarea")).shouldHave(value("Multiline text in WebKit MiniBrowser"));
  }

  @Test
  void shouldVerifyDisabledReadonlyAndHiddenInputs() {
    $(By.name("my-disabled"))
      .shouldBe(visible, disabled)
      .shouldHave(attribute("placeholder", "Disabled input"));

    $(By.name("my-readonly"))
      .shouldBe(visible, readonly)
      .shouldHave(value("Readonly input"));

    $(By.name("my-hidden")).shouldBe(hidden);
  }

  @Test
  void shouldSelectDropdownAndDatalist() {
    var select = $(By.name("my-select")).shouldBe(visible);
    select.getSelectedOption().shouldHave(exactText("Open this select menu"));

    select.selectOption("Two");
    select.getSelectedOption().shouldHave(exactText("Two")).shouldHave(value("2"));

    select.selectOptionByValue("3");
    select.getSelectedOption().shouldHave(exactText("Three"));

    $(By.name("my-datalist"))
      .shouldBe(visible)
      .setValue("San Francisco")
      .shouldHave(value("San Francisco"));
  }

  @Test
  void shouldToggleCheckboxesAndRadioButtons() {
    $("#my-check-1").shouldBe(selected);
    $("#my-check-2").shouldNotBe(selected);

    $("#my-check-1").click();
    $("#my-check-2").click();

    $("#my-check-1").shouldNotBe(selected);
    $("#my-check-2").shouldBe(selected);

    $("#my-radio-1").shouldBe(selected);
    $("#my-radio-2").shouldNotBe(selected);

    $("#my-radio-2").click();
    $("#my-radio-1").shouldNotBe(selected);
    $("#my-radio-2").shouldBe(selected);
  }

  @Test
  void shouldInteractWithColorDateAndRangeInputs() {
    var colorPicker = $(By.name("my-colors")).shouldBe(visible).shouldHave(value("#563d7c"));
    executeJavaScript(
      "arguments[0].value = '#ff0000'; arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
      colorPicker
    );
    colorPicker.shouldHave(value("#ff0000"));

    var dateInput = $(By.name("my-date")).shouldBe(visible);
    dateInput.setValue("10/18/2026").pressEscape();
    dateInput.shouldHave(value("10/18/2026"));

    var rangeInput = $(By.name("my-range")).shouldBe(visible).shouldHave(value("5"));
    executeJavaScript(
      "arguments[0].value = '8'; arguments[0].dispatchEvent(new Event('input', {bubbles: true}));",
      rangeInput
    );
    rangeInput.shouldHave(value("8"));
  }

  @Test
  void shouldUploadFileInput(@TempDir Path tempDir) throws IOException {
    File sampleFile = tempDir.resolve("webkit-upload-sample.txt").toFile();
    Files.writeString(sampleFile.toPath(), "Sample upload content for WebKit", UTF_8);

    var fileInput = $(By.name("my-file")).shouldBe(visible);
    File uploaded = fileInput.uploadFile(sampleFile);
    assertThat(uploaded.getName()).isEqualTo("webkit-upload-sample.txt");
    assertThat(fileInput.getValue()).endsWith("webkit-upload-sample.txt");
  }

  @Test
  void shouldNavigateToIndexAndUseBrowserHistory() {
    $("#my-text-id").setValue("Before navigation");
    $("a[href='./index.html']").shouldBe(visible).click();

    $("h1").shouldBe(visible).shouldHave(exactText("Practice site"));
    assertThat(title()).isEqualTo("Hands-On Selenium WebDriver with Java");

    back();
    $("h1").shouldBe(visible).shouldHave(exactText("Web form"));

    forward();
    $("h1").shouldBe(visible).shouldHave(exactText("Practice site"));

    refresh();
    $("h1").shouldBe(visible).shouldHave(exactText("Practice site"));
  }

  @Test
  void shouldSubmitFormAndVerifyTargetPage() {
    $("#my-text-id").setValue("Selenide WebKit User");
    $(By.name("my-select")).selectOption("One");
    $("button[type='submit']").shouldBe(visible, enabled).click();

    $("h1").shouldBe(visible).shouldHave(exactText("Form submitted"));
    $("#message").shouldBe(visible).shouldHave(exactText("Received!"));
    assertThat(title()).isEqualTo("Web form - target page");
  }
}
