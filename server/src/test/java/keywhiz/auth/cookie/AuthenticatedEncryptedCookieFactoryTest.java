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
package keywhiz.auth.cookie;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.ws.rs.core.NewCookie;
import keywhiz.FakeRandom;
import keywhiz.KeywhizService;
import keywhiz.auth.User;
import org.junit.Before;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticatedEncryptedCookieFactoryTest {
  private static final ObjectMapper MAPPER =
      KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
  private static final GCMEncryptor ENCRYPTOR =
      new GCMEncryptor("TESTKEYOF16BYTES".getBytes(UTF_8), FakeRandom.create());
  private static final User USER = User.named("User");

  Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));
  AuthenticatedEncryptedCookieFactory cookieFactory;

  @Before public void setUp() {
    CookieConfig config = new CookieConfig();
    config.setName("session");
    config.setPath("/admin");

    cookieFactory = new AuthenticatedEncryptedCookieFactory(clock, MAPPER, ENCRYPTOR, config);
  }

  @Test public void validCookieMatchesExpected() {
    ZonedDateTime expiration = ZonedDateTime.now(clock).plusHours(1);
    NewCookie validCookie = cookieFactory.getSessionCookie(USER, expiration);

    assertThat(validCookie.getName()).isEqualTo("session");
    assertThat(validCookie.getValue()).isNotEmpty();
    assertThat(validCookie.getVersion()).isEqualTo(1);
    assertThat(validCookie.getPath()).isEqualTo("/admin");
    assertThat(validCookie.isSecure()).isTrue();
    assertThat(validCookie.isHttpOnly()).isTrue();
    assertThat(validCookie.getMaxAge()).isEqualTo(3600);
    assertThat(validCookie.getExpiry()).isEqualTo(Date.from(expiration.toInstant()));
  }

  @Test public void validExpiredCookieExpiresInPast() {
    ZonedDateTime expiration = ZonedDateTime.now(clock).minusSeconds(1);
    NewCookie validCookie = cookieFactory.getSessionCookie(USER, expiration);

    assertThat(validCookie.getName()).isEqualTo("session");
    assertThat(validCookie.getValue()).isNotEmpty();
    assertThat(validCookie.getVersion()).isEqualTo(1);
    assertThat(validCookie.getPath()).isEqualTo("/admin");
    assertThat(validCookie.isSecure()).isTrue();
    assertThat(validCookie.isHttpOnly()).isTrue();

    // When an already-expired or expiring cookie is created, its maxage and expiry should
    // be set to the past
    assertThat(validCookie.getMaxAge()).isEqualTo(0);
    assertThat(validCookie.getExpiry()).isEqualTo(new Date(0));
  }

  @Test public void expiredCookieMatchesExpected() {
    NewCookie expiredCookie = cookieFactory.getExpiredSessionCookie();

    assertThat(expiredCookie.getName()).isEqualTo("session");
    assertThat(expiredCookie.getValue()).isEqualTo("expired");
    assertThat(expiredCookie.getVersion()).isEqualTo(1);
    assertThat(expiredCookie.getPath()).isEqualTo("/admin");
    assertThat(expiredCookie.isSecure()).isTrue();
    assertThat(expiredCookie.isHttpOnly()).isTrue();
    assertThat(expiredCookie.getMaxAge()).isEqualTo(0);
    assertThat(expiredCookie.getExpiry()).isEqualTo(new Date(0));
  }

  @Test public void expiredCookieMatchesValidExpiringNowCookieExceptContent() {
    // Create a cookie expiring immediately and an expired cookie
    String validCookie = cookieFactory.getSessionCookie(USER, ZonedDateTime.now(clock)).toString();
    String expiredCookie = cookieFactory.getExpiredSessionCookie().toString();

    Matcher matcher = Pattern.compile("(.*session=)[^;]+(;.*)").matcher(validCookie);
    assertThat(matcher.find()).isTrue();

    String withoutSession = matcher.replaceFirst("$1expired$2");
    assertThat(withoutSession).isEqualTo(expiredCookie);
  }
}
