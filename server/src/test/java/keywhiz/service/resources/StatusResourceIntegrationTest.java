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

import keywhiz.IntegrationTestRule;
import keywhiz.TestClients;
import okhttp3.*;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static keywhiz.testing.HttpClients.testUrl;
import static org.assertj.core.api.Assertions.assertThat;

public class StatusResourceIntegrationTest {
  OkHttpClient httpClient, httpsClient;

  @ClassRule
  public static final RuleChain chain = IntegrationTestRule.rule();

  @Before
  public void setUp() throws Exception {
    httpClient = new OkHttpClient();
    httpsClient = TestClients.unauthenticatedClient();
  }

  private boolean isHealthy() throws Exception {
    Request get = new Request.Builder()
        .get()
        .url(testUrl("/_status"))
        .build();
    okhttp3.Response statusResponse = httpsClient.newCall(get).execute();
    return statusResponse.code() == 200;
  }

  @Test
  public void successWhenHealthy() throws Exception {
    Request request = new Request.Builder()
        .url("http://localhost:8081/status/enable")
        .post(RequestBody.create(MediaType.parse("text/plain"), ""))
        .build();
    Response disableResponse = httpClient.newCall(request).execute();
    assertThat(disableResponse.code()).isEqualTo(200);
    Thread.sleep(3500);
    assertThat(isHealthy()).isTrue();
  }

  @Test
  public void failsWhenUnhealthy() throws Exception {
    Request request = new Request.Builder()
        .url("http://localhost:8081/status/disable")
        .post(RequestBody.create(MediaType.parse("text/plain"), ""))
        .build();
    Response disableResponse = httpClient.newCall(request).execute();
    assertThat(disableResponse.code()).isEqualTo(200);

    assertThat(isHealthy()).isFalse();
  }

  @Test
  public void cachesStatusCheck() throws Exception {
    // Start healthy
    Request request = new Request.Builder()
        .url("http://localhost:8081/status/enable")
        .post(RequestBody.create(MediaType.parse("text/plain"), ""))
        .build();
    Response enableResponse = httpClient.newCall(request).execute();
    assertThat(enableResponse.code()).isEqualTo(200);
    Thread.sleep(3500);
    assertThat(isHealthy()).isTrue();

    // Make the service unhealthy
    request = new Request.Builder()
        .url("http://localhost:8081/status/disable")
        .post(RequestBody.create(MediaType.parse("text/plain"), ""))
        .build();
    Response disableResponse = httpClient.newCall(request).execute();
    assertThat(disableResponse.code()).isEqualTo(200);

    // We should get back the cached healthy result. Sleep and query again should return unhealthy.
    assertThat(isHealthy()).isTrue();
    Thread.sleep(3500);
    assertThat(isHealthy()).isFalse();

    // Make the service healthy
    request = new Request.Builder()
        .url("http://localhost:8081/status/enable")
        .post(RequestBody.create(MediaType.parse("text/plain"), ""))
        .build();
    enableResponse = httpClient.newCall(request).execute();
    assertThat(enableResponse.code()).isEqualTo(200);

    // We should get back the cached unhealthy result. Sleep and query again should return healthy.
    assertThat(isHealthy()).isFalse();
    Thread.sleep(3500);
    assertThat(isHealthy()).isTrue();
  }
}
