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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.auth.User;
import keywhiz.auth.cookie.AuthenticatedEncryptedCookieFactory;
import keywhiz.auth.cookie.CookieAuthenticator;
import keywhiz.auth.cookie.CookieConfig;
import keywhiz.auth.cookie.GCMEncryptor;
import keywhiz.auth.cookie.SessionCookie;
import keywhiz.service.resources.admin.SessionMeResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(KeywhizTestRunner.class)
public class SessionMeResourceTest {
  @Inject ObjectMapper mapper;
  @Inject GCMEncryptor GCMEncryptor;
  @Inject @SessionCookie CookieConfig sessionCookieConfig;

  AuthenticatedEncryptedCookieFactory cookieFactory;
  CookieAuthenticator cookieAuthenticator;
  SessionMeResource sessionMeResource;

  @Before
  public void setUp() throws Exception {
    cookieFactory = new AuthenticatedEncryptedCookieFactory(Clock.systemUTC(), mapper, GCMEncryptor, sessionCookieConfig);
    cookieAuthenticator = new CookieAuthenticator(mapper, GCMEncryptor);
    sessionMeResource = new SessionMeResource();
  }

  @Test
  public void returnsTheCorrectUser() throws Exception {
    User user = User.named("Me");
    User returnedUser = sessionMeResource.getInformation(user);

    assertThat(returnedUser).isEqualTo(user);
  }
}
