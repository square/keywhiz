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
import io.dropwizard.auth.Authenticator;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.AEADBadTagException;
import javax.inject.Inject;
import javax.ws.rs.core.Cookie;
import keywhiz.auth.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CookieAuthenticator implements Authenticator<Cookie, User> {
  private static final Logger logger = LoggerFactory.getLogger(CookieAuthenticator.class);

  private final ObjectMapper mapper;
  private final GCMEncryptor encryptor;

  @Inject public CookieAuthenticator(ObjectMapper mapper, GCMEncryptor encryptor) {
    this.mapper = mapper;
    this.encryptor = encryptor;
  }

  @Override public Optional<User> authenticate(Cookie cookie) {
    User user = null;

    if (cookie != null) {
      Optional<UserCookieData> cookieData = getUserCookieData(cookie);
      if (cookieData.isPresent()) {
        user = cookieData.get().getUser();
      }
    }

    return Optional.ofNullable(user);
  }

  private Optional<UserCookieData> getUserCookieData(Cookie sessionCookie) {
    byte[] ciphertext = Base64.getDecoder().decode(sessionCookie.getValue());
    UserCookieData cookieData = null;

    try {
      cookieData = mapper.readValue(encryptor.decrypt(ciphertext), UserCookieData.class);
      if (cookieData.getExpiration().isBefore(ZonedDateTime.now())) {
        cookieData = null;
      }
    } catch (AEADBadTagException e) {
      logger.warn("Cookie with bad MAC detected");
    } catch (Exception e) { /* this cookie ain't gettin decrypted, it's bad */ }

    return Optional.ofNullable(cookieData);
  }
}
