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

package keywhiz.service.providers;

import com.google.common.base.Throwables;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import java.util.Optional;
import javax.inject.Inject;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.Cookie;
import keywhiz.auth.User;
import keywhiz.auth.cookie.CookieAuthenticator;
import keywhiz.auth.cookie.CookieConfig;
import keywhiz.auth.cookie.SessionCookie;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authenticates {@link User}s from requests based on cookies and roles.
 *
 * Modeled similar to io.dropwizard.auth.AuthFactory, however that is not yet usable.
 * See https://github.com/dropwizard/dropwizard/issues/864.
 */
public class UserAuthFactory {
  private static final Logger logger = LoggerFactory.getLogger(UserAuthFactory.class);

  private final Authenticator<Cookie, User> authenticator;
  private final String sessionCookieName;

  @Inject public UserAuthFactory(CookieAuthenticator cookieAuthenticator,
      @SessionCookie CookieConfig cookieConfig) {
    this.authenticator = new MyAuthenticator(cookieAuthenticator);
    this.sessionCookieName = cookieConfig.getName();
  }

  public User provide(ContainerRequest request) {
    Cookie sessionCookie = request.getCookies().get(sessionCookieName);
    if (sessionCookie == null) {
      logger.warn("No session cookie in request.");
      throw new NotAuthorizedException("Bad session");
    }

    try {
      return authenticator.authenticate(sessionCookie)
          .orElseThrow(() -> new NotAuthorizedException("Bad session"));
    } catch (AuthenticationException e) {
      throw Throwables.propagate(e);
    }
  }

  /**
   * Authenticates a user (employee, engineer, etc.) based on a session cookie.
   *
   * In addition, validates that the request has valid XSRF protection {@see XsrfProtection}.
   * If the User cannot be validated then {@code Optional.absent()} is returned.
   */
  private static class MyAuthenticator implements Authenticator<Cookie, User> {
    private final CookieAuthenticator cookieAuthenticator;

    private MyAuthenticator(CookieAuthenticator cookieAuthenticator) {
      this.cookieAuthenticator = cookieAuthenticator;
    }

    @Override
    public Optional<User> authenticate(Cookie sessionCookie) throws AuthenticationException {
      Optional<User> user = cookieAuthenticator.authenticate(sessionCookie);
      if (!user.isPresent()) {
        logger.info("Invalid session cookie");
      }
      return user;
    }
  }
}
