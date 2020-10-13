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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Date;
import javax.crypto.AEADBadTagException;
import javax.inject.Inject;
import javax.ws.rs.core.NewCookie;
import keywhiz.auth.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Produces tokens and cookies based on encrypted {@link UserCookieData} records. */
public class AuthenticatedEncryptedCookieFactory {
  private static final Logger logger = LoggerFactory.getLogger(AuthenticatedEncryptedCookieFactory.class);

  private final Clock clock;
  private final ObjectMapper mapper;
  private final GCMEncryptor encryptor;
  private final CookieConfig config;

  /**
   * @param clock to use for resolving current time
   * @param mapper json serializer
   * @param encryptor performs authenticated-encryption using a non-colliding counter specific to a host.
   * @param config parameters for cookie generation
   */
  @Inject
  public AuthenticatedEncryptedCookieFactory(
      Clock clock,
      ObjectMapper mapper,
      GCMEncryptor encryptor,
      @SessionCookie CookieConfig config) {
    this.clock = clock;
    this.mapper = mapper;
    this.encryptor = encryptor;
    this.config = config;
  }

  /**
   * Produces an authenticating token.
   *
   * @param user identity the token will authenticate.
   * @param expiration timestamp when token should expire.
   * @return token which can be used to authenticate as user until expiration.
   */
  public String getSession(User user, ZonedDateTime expiration) {
    try {
      String cookieJson = mapper.writeValueAsString(new UserCookieData(user, expiration));
      byte[] cookieBody = encryptor.encrypt(cookieJson.getBytes(UTF_8));
      return Base64.getEncoder().encodeToString(cookieBody);
    } catch (AEADBadTagException e) {
      logger.error("Could not encrypt cookie", e);
      throw Throwables.propagate(e);
    } catch (JsonProcessingException e) {
      throw Throwables.propagate(e);
    }
  }

  /**
   * Produces a cookie string for a given value and expiration.
   *
   * @param value value of new cookie.
   * @param expiration expiration time of cookie.
   * @return serialized cookie with given value and expiration.
   */
  public NewCookie cookieFor(String value, ZonedDateTime expiration) {
    long maxAge = Duration.between(ZonedDateTime.now(clock), expiration).getSeconds();
    Date expirationDate = Date.from(expiration.toInstant());

    // If the cookie is already expired, set it to have no lifespan (maxAge = 0) and
    // an expiration in the distant past. This is particularly important because a maxAge
    // value of -1 would mark that the cookie expired at the end of the current session.
    if (maxAge <= 0) {
      maxAge = 0;
      expirationDate = new Date(0);
    }

    return new NewCookie(config.getName(), value, config.getPath(), config.getDomain(), 1, "",
        (int) maxAge, expirationDate, config.isSecure(), config.isHttpOnly());
  }

  /**
   * Shortcut method to produce an authenticated cookie string.
   *
   * @param user identity the token will authenticate.
   * @param expiration timestamp when cookie should expire.
   * @return serialized cookie which can be used to authenticate as user until expiration.
   */
  public NewCookie getSessionCookie(User user, ZonedDateTime expiration) {
    return cookieFor(getSession(user, expiration), expiration);
  }

  /**
   * Produces an expired cookie string, used to update/overwrite an existing cookie.
   *
   * @return serialized expired cookie with matching parameters to authenticating cookie.
   */
  public NewCookie getExpiredSessionCookie() {
    return new NewCookie(config.getName(), "expired", config.getPath(), config.getDomain(), 1, "",
        0, new Date(0), config.isSecure(), config.isHttpOnly());
  }
}
