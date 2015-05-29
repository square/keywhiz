package keywhiz.cli;

import com.google.common.io.Resources;
import io.dropwizard.jackson.Jackson;
import java.net.HttpCookie;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class JsonCookieTest {
  @Test public void cookiesAlwaysVersion1() throws Exception {
    HttpCookie cookieVersionZero = new HttpCookie("session", "session-contents");
    cookieVersionZero.setPath("/");
    cookieVersionZero.setDomain("localhost");
    cookieVersionZero.setVersion(0);

    HttpCookie convertedCookie = JsonCookie.toHttpCookie(
        JsonCookie.fromHttpCookie(cookieVersionZero));
    assertThat(convertedCookie.getVersion()).isEqualTo(1);
  }

  @Test public void deserializesCookie() throws Exception {
    JsonCookie expected = JsonCookie.create("session", "session-contents", "localhost", "/admin", true, true);

    String json = Resources.toString(Resources.getResource("fixtures/cookie_valid.json"), UTF_8);
    JsonCookie actual = Jackson.newObjectMapper().readValue(json, JsonCookie.class);

    assertThat(actual).isEqualTo(expected);
  }

  @Test public void handlesSerializedVersionField() throws Exception {
    JsonCookie expected = JsonCookie.create("session", "session-contents", "localhost", "/admin", true, true);

    String json = Resources.toString(Resources.getResource("fixtures/cookie_with_version.json"), UTF_8);
    JsonCookie actual = Jackson.newObjectMapper().readValue(json, JsonCookie.class);

    assertThat(actual).isEqualTo(expected);
  }
}
