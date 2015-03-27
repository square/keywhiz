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
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import io.dropwizard.jackson.Jackson;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import keywhiz.IntegrationTestRule;
import keywhiz.KeywhizService;
import keywhiz.TestClients;
import keywhiz.api.CreateClientRequest;
import keywhiz.client.KeywhizClient;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.assertj.core.api.Assertions.assertThat;

public class AutomationClientResourceIntegrationTest {
  ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
  OkHttpClient mutualSslClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    mutualSslClient = TestClients.mutualSslClient();
  }

  @Test public void listClients() throws Exception {
    Request get = new Request.Builder()
        .get()
        .url("/automation/clients")
        .addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .build();

    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
  }

  @Test public void addClients() throws Exception {
    CreateClientRequest request = new CreateClientRequest("User1");
    String requestJSON = mapper.writeValueAsString(request);
    RequestBody body = RequestBody.create(KeywhizClient.JSON, requestJSON);
    Request post = new Request.Builder()
        .post(body)
        .url("/automation/clients")
        .addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    Response httpResponse = mutualSslClient.newCall(post).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
  }

  @Test public void addClientRedundant() throws Exception {
    CreateClientRequest request = new CreateClientRequest("CN=User1");
    String requestJSON = mapper.writeValueAsString(request);
    RequestBody body = RequestBody.create(KeywhizClient.JSON, requestJSON);
    Request post = new Request.Builder()
        .post(body)
        .url("/automation/clients")
        .addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    Response httpResponse = mutualSslClient.newCall(post).execute();
    assertThat(httpResponse.code()).isEqualTo(409);
  }
}
