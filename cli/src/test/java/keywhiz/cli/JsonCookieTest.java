package keywhiz.cli;

import java.net.HttpCookie;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonCookieTest {
  @Test public void cookiesAlwaysVersion1() {
    HttpCookie cookieVersionZero = new HttpCookie("session", "session-contents");
    cookieVersionZero.setPath("/");
    cookieVersionZero.setDomain("localhost");
    cookieVersionZero.setVersion(0);

    HttpCookie convertedCookie = JsonCookie.toHttpCookie(
        JsonCookie.fromHttpCookie(cookieVersionZero));
    assertThat(convertedCookie.getVersion()).isEqualTo(1);
  }
}
