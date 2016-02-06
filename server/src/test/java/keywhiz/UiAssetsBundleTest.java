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

package keywhiz;

import javax.ws.rs.core.HttpHeaders;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static keywhiz.testing.HttpClients.testUrl;
import static org.assertj.core.api.Assertions.assertThat;

public class UiAssetsBundleTest {
  OkHttpClient client;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    client = TestClients.unauthenticatedClient();
  }

  @Test public void uiContentAccessible() throws Exception {
    Request request = new Request.Builder()
        .url(testUrl("/ui/"))
        .get()
        .build();

    Response response = client.newCall(request).execute();
    assertThat(response.code()).isEqualTo(200);
  }

  @Test public void rootRedirects() throws Exception {
    Request request = new Request.Builder()
        .url(testUrl("/"))
        .get()
        .build();

    Response response = client.newCall(request).execute();
    assertThat(response.code()).isEqualTo(302);
    String locationHeader = response.header(HttpHeaders.LOCATION);
    assertThat(locationHeader).endsWith("/ui/");
  }

  @Test public void incompleteUiRedirects() throws Exception {
    Request request = new Request.Builder()
        .url(testUrl("/ui"))
        .get()
        .build();

    Response response = client.newCall(request).execute();
    assertThat(response.code()).isEqualTo(302);
    String locationHeader = response.header(HttpHeaders.LOCATION);
    assertThat(locationHeader).endsWith("/ui/");
  }
}
