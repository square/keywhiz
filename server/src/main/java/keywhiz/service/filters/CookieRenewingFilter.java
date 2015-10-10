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
package keywhiz.service.filters;

import com.google.common.net.HttpHeaders;
import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Cookie;
import keywhiz.auth.User;
import keywhiz.auth.cookie.CookieAuthenticator;
import keywhiz.auth.cookie.CookieConfig;
import keywhiz.auth.cookie.SessionCookie;
import keywhiz.service.resources.admin.SessionLoginResource;

/** Checks for valid session cookies on requests and sets a newer cookie. */
public class CookieRenewingFilter implements ContainerResponseFilter {
  private final CookieConfig sessionCookieConfig;
  private final CookieAuthenticator authenticator;
  private final SessionLoginResource sessionLoginResource;

  @Inject public CookieRenewingFilter(@SessionCookie CookieConfig sessionCookieConfig,
      CookieAuthenticator authenticator, SessionLoginResource sessionLoginResource) {
    this.sessionCookieConfig = sessionCookieConfig;
    this.authenticator = authenticator;
    this.sessionLoginResource = sessionLoginResource;
  }

  /**
   * If the user has a valid session token, set a new session token. The new one should have a later
   * expiration time.
   */
  @Override public void filter(ContainerRequestContext request, ContainerResponseContext response)
      throws IOException {
    String sessionCookieName = sessionCookieConfig.getName();
    // If the response will be setting a session cookie, don't overwrite it; just let it go.
    if (response.getCookies().containsKey(sessionCookieName)) {
      return;
    }

    // If the request doesn't have a session cookie, we're not going to renew one.
    if (!request.getCookies().containsKey(sessionCookieName)) {
      return;
    }

    Cookie requestCookie = request.getCookies().get(sessionCookieName);
    Optional<User> optionalUser = authenticator.authenticate(requestCookie);
    if (optionalUser.isPresent()) {
      sessionLoginResource.cookiesForUser(optionalUser.get())
          .forEach(c -> response.getHeaders().add(HttpHeaders.SET_COOKIE, c));
    }
  }
}
