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
package keywhiz.auth.xsrf;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import keywhiz.IntegrationTestRule;
import keywhiz.TestClients;
import keywhiz.commands.DbSeedCommand;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static keywhiz.AuthHelper.buildLoginPost;
import static keywhiz.testing.HttpClients.testUrl;
import static org.assertj.core.api.Assertions.assertThat;

public class XsrfServletFilterIntegrationTest {
  OkHttpClient client;
  OkHttpClient noXsrfClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    client = TestClients.unauthenticatedClient();
    noXsrfClient = TestClients.noCertNoXsrfClient();
  }

  @Test public void xsrfNotRequiredForLogin() throws Exception {
    Response response = client.newCall(buildLoginPost(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword)).execute();
    assertThat(response.code()).isNotEqualTo(401);
  }

  @Test public void xsrfNotRequiredForLogout() throws Exception {
    Request request = new Request.Builder()
        .post(RequestBody.create(MediaType.parse("text/plain"), ""))
        .url(testUrl("/admin/logout"))
        .build();

    Response response = client.newCall(request).execute();
    assertThat(response.code()).isNotEqualTo(401);
  }

  @Test public void rejectsForAdminUrlWithoutXsrf() throws Exception {
    noXsrfClient.newCall(buildLoginPost(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword)).execute();
    Request request = new Request.Builder()
        .url(testUrl("/admin/clients"))
        .get()
        .build();

    Response response = noXsrfClient.newCall(request).execute();
    assertThat(response.code()).isEqualTo(401);
  }

  @Test public void allowsForAdminUrlWithXsrf() throws Exception {
    client.newCall(buildLoginPost(DbSeedCommand.defaultUser, DbSeedCommand.defaultPassword)).execute();
    Request request = new Request.Builder()
        .url(testUrl("/admin/clients"))
        .get()
        .build();

    Response response = client.newCall(request).execute();
    assertThat(response.code()).isNotEqualTo(401);
  }
}
