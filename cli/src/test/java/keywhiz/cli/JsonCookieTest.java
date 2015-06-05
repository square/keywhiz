/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
