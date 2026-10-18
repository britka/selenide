package org.selenide.webkit;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.MutableCapabilities;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class WebKitDriverServiceTest {
  @Test
  void scoresWebKitAndMiniBrowserCapabilities() {
    WebKitDriverService.Builder builder = new WebKitDriverService.Builder();

    assertThat(builder.score(new MutableCapabilities(Map.of("browserName", "MiniBrowser")))).isEqualTo(10);
    assertThat(builder.score(new MutableCapabilities(Map.of("browserName", "webkit")))).isEqualTo(10);
    assertThat(builder.score(new MutableCapabilities(Map.of("browserName", "chrome")))).isZero();
    assertThat(builder.score(new MutableCapabilities())).isZero();
  }

  @Test
  void allocatesUniqueFreePorts() {
    int port1 = WebKitDriverService.allocateUniqueFreePort();
    int port2 = WebKitDriverService.allocateUniqueFreePort();

    assertThat(port1).isPositive();
    assertThat(port2).isPositive().isNotEqualTo(port1);
  }

  @Test
  void createsArgsWithCustomHostAndTarget() {
    TestableBuilder builder = new TestableBuilder();
    builder.usingPort(14444)
      .withHost("127.0.0.1")
      .withReplaceOnNewSession(true)
      .withTarget("127.0.0.1:19999");

    List<String> args = builder.buildArgs();
    assertThat(args).containsExactly(
      "--port=14444",
      "--host=127.0.0.1",
      "--replace-on-new-session",
      "--target=127.0.0.1:19999"
    );
  }

  @Test
  void createsArgsInHeadlessModeWithoutTarget() {
    TestableBuilder builder = new TestableBuilder();
    builder.usingPort(14445)
      .withReplaceOnNewSession(false)
      .withHeadless(true);

    List<String> args = builder.buildArgs();
    assertThat(args).containsExactly("--port=14445");
  }

  private static final class TestableBuilder extends WebKitDriverService.Builder {
    List<String> buildArgs() {
      return createArgs();
    }
  }
}
