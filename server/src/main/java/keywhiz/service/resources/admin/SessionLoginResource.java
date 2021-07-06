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

package keywhiz.service.resources.admin;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableList;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import java.time.ZonedDateTime;
import java.util.Optional;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import keywhiz.api.LoginRequest;
import keywhiz.auth.User;
import keywhiz.auth.cookie.AuthenticatedEncryptedCookieFactory;
import keywhiz.service.config.Readonly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static keywhiz.Tracing.setTag;

/**
 * parentEndpointName login
 *
 * resourceDescription Login to Keywhiz admin interface
 */
@Path("/admin/login")
public class SessionLoginResource {
  private static final Logger logger = LoggerFactory.getLogger(SessionLoginResource.class);

  private final Authenticator<BasicCredentials, User> userAuthenticator;
  private final AuthenticatedEncryptedCookieFactory cookieFactory;

  @Inject
  public SessionLoginResource(@Readonly Authenticator<BasicCredentials, User> userAuthenticator,
      AuthenticatedEncryptedCookieFactory cookieFactory) {
    this.userAuthenticator = userAuthenticator;
    this.cookieFactory = cookieFactory;
  }

  /**
   * Login and set a session cookie
   *
   * @param request validated json request object
   * @return 200 on success, 401 for invalid or unauthorized credentials
   *
   * description Logs in using LDAP and sets session cookies to authorize further requests
   * responseMessage 200 Logged in successfully
   * responseMessage 401 Incorrect credentials or not authorized
   */
  @Timed @ExceptionMetered
  @POST
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public Response login(@Valid LoginRequest request) {

    String username = request.username();
    String password = String.copyValueOf(request.password());

    setTag("username", username);

    Optional<User> optionalUser = Optional.empty();
    try {
      optionalUser = userAuthenticator.authenticate(new BasicCredentials(username, password));
    } catch (AuthenticationException e) {
      logger.warn("User authenticator threw something weird.", e);
    }

    if (!optionalUser.isPresent()) {
      logger.info("User authentication failed at login for {}", username);
      throw new NotAuthorizedException("");
    }

    logger.info("User logged in: {}", username);

    Response.ResponseBuilder response = Response.ok();
    cookiesForUser(optionalUser.get())
        .forEach(response::cookie);
    return response.build();
  }

  public ImmutableList<NewCookie> cookiesForUser(User user) {
    ZonedDateTime expiration = ZonedDateTime.now().plusMinutes(15);
    String session = cookieFactory.getSession(user, expiration);

    NewCookie cookie = cookieFactory.cookieFor(session, expiration);

    return ImmutableList.of(cookie);
  }
}
