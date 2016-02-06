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

import keywhiz.IntegrationTestRule;
import keywhiz.TestClients;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static keywhiz.testing.HttpClients.testUrl;
import static org.assertj.core.api.Assertions.assertThat;

public class SecurityHeadersFilterTest {
  OkHttpClient client;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    client = TestClients.unauthenticatedClient();
  }

  @Test public void checkSecurityHeaders() throws Exception {
    Request request = new Request.Builder()
        .url(testUrl("/ui/"))
        .get()
        .build();

    Response response = client.newCall(request).execute();

    assertThat(response.header("X-Content-Security-Policy")).isEqualTo("default-src 'self'");
    assertThat(response.header("X-XSS-Protection")).isEqualTo("0");
    assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.header("X-Frame-Options")).isEqualTo("DENY");
    assertThat(response.header("Strict-Transport-Security")).isEqualTo("max-age=31536000; includeSubDomains");
  }
}
