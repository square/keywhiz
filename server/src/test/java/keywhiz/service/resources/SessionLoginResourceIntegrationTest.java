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

import com.google.common.collect.Lists;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import java.util.List;
import java.util.regex.Pattern;
import javax.ws.rs.core.HttpHeaders;
import keywhiz.IntegrationTestRule;
import keywhiz.TestClients;
import keywhiz.commands.DbSeedCommand;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static keywhiz.AuthHelper.buildLoginPost;
import static org.assertj.core.api.Assertions.assertThat;

public class SessionLoginResourceIntegrationTest {
  OkHttpClient client;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    client = TestClients.unauthenticatedClient();
  }

  @Test
  public void respondsToLogin() throws Exception {
    Request post = new Request.Builder()
        .post(null)
        .url("/admin/login")
        .build();

    Response response = client.newCall(post).execute();
    assertThat(response.code()).isNotEqualTo(404);
  }

  @Test
  public void setsValidCookieForValidCredentials() throws Exception {
    Request post = buildLoginPost(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword);

    Response response = client.newCall(post).execute();
    assertThat(response.code()).isEqualTo(303);

    List<String> cookieNames = Lists.newArrayList();
    String sessionCookie = null;

    for (String cookieString : response.headers(HttpHeaders.SET_COOKIE)) {
      cookieString = cookieString.substring(0, cookieString.indexOf(";"));
      String cookieName = cookieString.substring(0, cookieString.indexOf("="));
      cookieNames.add(cookieName);

      if (cookieName.equals("session")) {
        sessionCookie = cookieString;
      }
    }

    assertThat(cookieNames).containsOnly("session", "XSRF-TOKEN");

    Pattern pattern = Pattern.compile("^session=(.+)$");
    assertThat(sessionCookie).matches(pattern);
  }

  @Test
  public void invalidCredentialsAreUnauthorized() throws Exception {
    Request request = buildLoginPost("username", "badpassword");

    Response response = client.newCall(request).execute();
    assertThat(response.code()).isEqualTo(401);
  }

  @Test
  public void insufficientRolesAreUnauthorized() throws Exception {
    Request request = buildLoginPost("username", "password");

    Response response = client.newCall(request).execute();
    assertThat(response.code()).isEqualTo(401);
  }

  @Test
  public void noFormDataIsBadRequest() throws Exception {
    Request request = buildLoginPost(null, null);

    Response response = client.newCall(request).execute();
    assertThat(response.code()).isEqualTo(422);
  }

  @Test
  public void missingUsernameIsBadRequest() throws Exception {
    Request request = buildLoginPost(null, "password");

    Response response = client.newCall(request).execute();
    assertThat(response.code()).isEqualTo(422);
  }

  @Test
  public void missingPasswordIsBadRequest() throws Exception {
    Request request = buildLoginPost("username", null);

    Response response = client.newCall(request).execute();
    assertThat(response.code()).isEqualTo(422);
  }
}
