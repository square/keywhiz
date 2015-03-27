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

package keywhiz.service.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.dropwizard.auth.basic.BasicCredentials;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.LoginRequest;
import keywhiz.auth.User;
import keywhiz.auth.cookie.AuthenticatedEncryptedCookieFactory;
import keywhiz.auth.cookie.CookieAuthenticator;
import keywhiz.auth.cookie.CookieConfig;
import keywhiz.auth.cookie.GCMEncryptor;
import keywhiz.auth.cookie.SessionCookie;
import keywhiz.auth.ldap.LdapAuthenticator;
import keywhiz.auth.xsrf.Xsrf;
import keywhiz.auth.xsrf.XsrfProtection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import static javax.ws.rs.core.Response.Status.SEE_OTHER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(KeywhizTestRunner.class)
public class SessionLoginResourceTest {
  @Mock LdapAuthenticator ldapAuthenticator;

  @Inject ObjectMapper mapper;
  @Inject GCMEncryptor GCMEncryptor;
  @Inject @SessionCookie CookieConfig sessionCookieConfig;
  @Inject @Xsrf CookieConfig xsrfCookieConfig;

  SessionLoginResource sessionLoginResource;
  CookieAuthenticator cookieAuthenticator;
  BasicCredentials goodCredentials = new BasicCredentials("good","credentials");
  BasicCredentials badCredentials = new BasicCredentials("bad","credentials");

  @Before
  public void setUp() throws Exception {
    AuthenticatedEncryptedCookieFactory cookieFactory =
        new AuthenticatedEncryptedCookieFactory(Clock.systemUTC(), mapper, GCMEncryptor, sessionCookieConfig);
    XsrfProtection xsrfProtection = new XsrfProtection(xsrfCookieConfig);

    sessionLoginResource = new SessionLoginResource(ldapAuthenticator, cookieFactory, xsrfProtection);
    cookieAuthenticator = new CookieAuthenticator(mapper, GCMEncryptor);
  }

  @Test(expected = NotAuthorizedException.class)
  public void badCredentialsThrowUnauthorized() throws Exception {
    when(ldapAuthenticator.authenticate(badCredentials)).thenReturn(Optional.empty());

    sessionLoginResource.login(new LoginRequest("bad", "credentials"));
  }

  @Test
  public void goodCredentialsSetsCookie() throws Exception {
    User user = User.named("goodUser");
    when(ldapAuthenticator.authenticate(goodCredentials)).thenReturn(Optional.of(user));

    Response response = sessionLoginResource.login(new LoginRequest("good", "credentials"));
    assertThat(response.getStatus()).isEqualTo(SEE_OTHER.getStatusCode());

    Map<String, NewCookie> responseCookies = response.getCookies();
    assertThat(responseCookies).hasSize(2).containsOnlyKeys("session", "XSRF-TOKEN");

    User authUser = cookieAuthenticator.authenticate(responseCookies.get("session"))
        .orElseThrow(RuntimeException::new);
    assertThat(authUser).isEqualTo(user);
  }

  @Test(expected = NullPointerException.class)
  public void missingUsernameThrowsException() throws Exception {
    sessionLoginResource.login(new LoginRequest(null, "password"));
  }

  @Test(expected = NullPointerException.class)
  public void missingPasswordThrowsException() throws Exception {
    sessionLoginResource.login(new LoginRequest("username", null));
  }
}
