package integration;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.DragAndDropOptions;
import com.selenide.videorecorder.VideoRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.codeborne.selenide.Selenide.*;

public class VideoRecorderTests {

  private static final Logger log = LoggerFactory.getLogger(VideoRecorderTests.class);

  @BeforeAll
  public static void setUp() {
    ChromeOptions chromeOptions = new ChromeOptions();
    chromeOptions.setCapability("webSocketUrl", true);
    Configuration.browserCapabilities = chromeOptions;
    Configuration.browser = "chrome";
    Configuration.headless = false;
    Configuration.timeout = 10000;
  }

  VideoRecorder videoRecorder;

  @BeforeEach
  public void beforeEach() {
    open();
    videoRecorder = new VideoRecorder(webdriver().object());
    videoRecorder.start();
  }

  @AfterEach
  public void afterEach() throws IOException {
    videoRecorder.stopRecording();
    videoRecorder.interrupt();
    closeWebDriver();
  }


  @Test
  public void videoFileShouldExistsAndNotEmpty() throws IOException {
    long now = System.currentTimeMillis();

    open("file://" + this.getClass().getClassLoader().getResource("draggable.html").getPath());
    $("#drag1").dragAndDrop(DragAndDropOptions.to("#div2"));
    sleep(3000);
    $("#drag1").dragAndDrop(DragAndDropOptions.to("#div1"));
    sleep(3000);
    $("#drag1").dragAndDrop(DragAndDropOptions.to("#div2"));
    sleep(3000);
    $("#drag1").dragAndDrop(DragAndDropOptions.to("#div1"));
    sleep(3000);
    $("#drag1").dragAndDrop(DragAndDropOptions.to("#div2"));
    sleep(3000);
    $("#drag1").dragAndDrop(DragAndDropOptions.to("#div1"));
    sleep(3000);
    log.debug("Time for test: " + (System.currentTimeMillis() - now) / 1000);
  }

  @Test
  public void videoFileShouldExistsAndNotEmpty1() {
    long now = System.currentTimeMillis();

    open("file://" + this.getClass().getClassLoader().getResource("draggable.html").getPath());
    $("#drag1").dragAndDrop(DragAndDropOptions.to("#div2"));
    sleep(3000);
    $("#drag1").dragAndDrop(DragAndDropOptions.to("#div1"));
    sleep(3000);
    $("#drag1").dragAndDrop(DragAndDropOptions.to("#div2"));
    sleep(3000);
    $("#drag1").dragAndDrop(DragAndDropOptions.to("#div1"));
    sleep(3000);
    $("#drag1").dragAndDrop(DragAndDropOptions.to("#div2"));
    sleep(3000);
    $("#drag1").dragAndDrop(DragAndDropOptions.to("#div1"));
    sleep(3000);
    log.debug("Time for test: " + (System.currentTimeMillis() - now) / 1000);
  }


}