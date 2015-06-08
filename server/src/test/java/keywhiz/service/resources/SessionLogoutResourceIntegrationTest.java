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

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import java.util.Date;
import java.util.List;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.NewCookie;
import keywhiz.IntegrationTestRule;
import keywhiz.TestClients;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static keywhiz.testing.HttpClients.testUrl;
import static org.assertj.core.api.Assertions.assertThat;

public class SessionLogoutResourceIntegrationTest {
  OkHttpClient client;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    client = TestClients.unauthenticatedClient();
  }

  @Test public void sendsExpiredCookie() throws Exception {
    Request request = new Request.Builder()
        .post(RequestBody.create(MediaType.parse("text/plain"), ""))
        .url(testUrl("/admin/logout"))
        .build();

    Response response = client.newCall(request).execute();
    assertThat(response.code()).isEqualTo(303);

    List<String> cookies = response.headers(HttpHeaders.SET_COOKIE);
    assertThat(cookies).hasSize(1);

    NewCookie cookie = NewCookie.valueOf(cookies.get(0));
    assertThat(cookie.getName()).isEqualTo("session");
    assertThat(cookie.getValue()).isEqualTo("expired");
    assertThat(cookie.getVersion()).isEqualTo(1);
    assertThat(cookie.getPath()).isEqualTo("/admin");
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getExpiry()).isEqualTo(new Date(0));
  }
}
