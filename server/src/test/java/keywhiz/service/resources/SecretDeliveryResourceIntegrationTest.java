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
import io.dropwizard.jackson.Jackson;
import keywhiz.IntegrationTestRule;
import keywhiz.KeywhizService;
import keywhiz.TestClients;
import keywhiz.api.ApiDate;
import keywhiz.api.SecretDeliveryResponse;
import keywhiz.api.model.Secret;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static keywhiz.testing.HttpClients.testUrl;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretDeliveryResourceIntegrationTest {
  ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
  OkHttpClient client;
  Secret generalPassword;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() throws Exception {
    client = TestClients.mutualSslClient();
    generalPassword = new Secret(0, "General_Password", null, () -> "YXNkZGFz", "",
        ApiDate.parse("2011-09-29T15:46:00Z"), null,
        ApiDate.parse("2011-09-29T15:46:00Z"), null, null, "upload",
        null, 0, 1L, ApiDate.parse("2011-09-29T15:46:00Z"), null);
  }

  @Test public void returnsSecretWhenAllowed() throws Exception {
    Request get = new Request.Builder()
        .get()
        .url(testUrl("/secret/General_Password"))
        .build();

    Response response = client.newCall(get).execute();
    assertThat(response.code()).isEqualTo(200);
    assertThat(response.body().string())
        .isEqualTo(mapper.writeValueAsString(SecretDeliveryResponse.fromSecret(generalPassword)));
  }

  @Test public void returnsNotFoundWhenSecretUnspecified() throws Exception {
    Request get = new Request.Builder()
        .get()
        .url(testUrl("/secret/"))
        .build();

    Response response = client.newCall(get).execute();
    assertThat(response.code()).isEqualTo(404);
  }

  @Test public void returnsNotFoundWhenSecretDoesNotExist() throws Exception {
    Request get = new Request.Builder()
        .get()
        .url(testUrl("/secret/nonexistent"))
        .build();

    Response response = client.newCall(get).execute();
    assertThat(response.code()).isEqualTo(404);
  }

  @Test public void returnsUnauthorizedWhenDenied() throws Exception {
    Request get = new Request.Builder()
        .get()
        .url(testUrl("/secret/Hacking_Password"))
        .build();

    Response response = client.newCall(get).execute();
    assertThat(response.code()).isEqualTo(403);
  }
}
