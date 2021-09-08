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

package keywhiz.service.resources.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import io.dropwizard.jackson.Jackson;
import java.util.List;
import java.util.UUID;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import keywhiz.IntegrationTestRule;
import keywhiz.KeywhizService;
import keywhiz.TestClients;
import keywhiz.api.AutomationSecretResponse;
import keywhiz.api.CreateGroupRequest;
import keywhiz.api.CreateSecretRequest;
import keywhiz.client.KeywhizClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static keywhiz.testing.HttpClients.testUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class AutomationSecretResourceIntegrationTest {
  ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
  OkHttpClient mutualSslClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    mutualSslClient = TestClients.mutualSslClient();
  }

  @Test
  public void createsSecretWithOwner() throws Exception {
    String secretName = UUID.randomUUID().toString();
    String ownerName = UUID.randomUUID().toString();

    createGroup(ownerName);

    CreateSecretRequest request = new CreateSecretRequest(
        secretName,
        ownerName,
        "description",
        "content",
        ImmutableMap.of(),
        0);
    createSecret(request);

    AutomationSecretResponse details = getSecretByName(secretName);
    assertEquals(ownerName, details.owner());
  }

  @Test
  public void addSecrets() throws Exception {
    CreateSecretRequest request = new CreateSecretRequest("new_secret", null, "desc", "superSecret",
        ImmutableMap.of(), 0);
    String body = mapper.writeValueAsString(request);
    Request post = new Request.Builder()
        .post(RequestBody.create(KeywhizClient.JSON, body))
        .url(testUrl("/automation/secrets"))
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    Response response = mutualSslClient.newCall(post).execute();
    assertThat(response.code()).isEqualTo(200);
  }

  @Test
  public void addInvalidSecrets() throws Exception {
    CreateSecretRequest request = new CreateSecretRequest("empty_secret", null, "desc", "", null, 0);
    String body = mapper.writeValueAsString(request);
    Request post = new Request.Builder()
        .post(RequestBody.create(KeywhizClient.JSON, body))
        .url(testUrl("/automation/secrets"))
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    Response response = mutualSslClient.newCall(post).execute();
    assertThat(response.code()).isEqualTo(422);
  }

  @Test
  public void addConflictingSecrets() throws Exception {
    CreateSecretRequest request = new CreateSecretRequest("dup_secret", null, "desc", "content",
        ImmutableMap.of(), 0);
    String body = mapper.writeValueAsString(request);
    Request post = new Request.Builder()
        .post(RequestBody.create(KeywhizClient.JSON, body))
        .url(testUrl("/automation/secrets"))
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    Response response = mutualSslClient.newCall(post).execute();
    assertThat(response.code()).isEqualTo(200);
    response = mutualSslClient.newCall(post).execute();
    assertThat(response.code()).isEqualTo(409);
  }

  @Test
  public void readAllSecrets() throws Exception {
    Request get = new Request.Builder()
        .get()
        .url(testUrl("/automation/secrets"))
        .build();

    Response response = mutualSslClient.newCall(get).execute();
    assertThat(response.code()).isEqualTo(200);
  }

  @Test
  public void readValidSecret() throws Exception {
    CreateSecretRequest request = new CreateSecretRequest("readable", null, "desc", "c3VwZXJTZWNyZXQK",
        ImmutableMap.of(), 0);
    String body = mapper.writeValueAsString(request);
    Request post = new Request.Builder()
        .post(RequestBody.create(KeywhizClient.JSON, body))
        .url(testUrl("/automation/secrets"))
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    Response response = mutualSslClient.newCall(post).execute();
    assertThat(response.code()).isEqualTo(200);

    Request get = new Request.Builder()
        .get()
        .url(testUrl("/automation/secrets?name=readable"))
        .build();
    response = mutualSslClient.newCall(get).execute();
    assertThat(response.code()).isEqualTo(200);
  }

  @Test
  public void readInvalidSecret() throws Exception {
    Request get = new Request.Builder()
        .get()
        .url(testUrl("/automation/secrets?name=invalid"))
        .build();
    Response response = mutualSslClient.newCall(get).execute();
    assertThat(response.code()).isEqualTo(404);
  }

  @Test
  public void deleteSecrets() throws Exception {
    CreateSecretRequest request = new CreateSecretRequest("deletable", null, "desc", "c3VwZXJTZWNyZXQK",
        ImmutableMap.of(), 0);
    String body = mapper.writeValueAsString(request);
    Request post = new Request.Builder()
        .post(RequestBody.create(KeywhizClient.JSON, body))
        .url(testUrl("/automation/secrets"))
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    Response response = mutualSslClient.newCall(post).execute();
    assertThat(response.code()).isEqualTo(200);

    Request delete = new Request.Builder()
        .delete()
        .url(testUrl("/automation/secrets/deletable"))
        .build();

    response = mutualSslClient.newCall(delete).execute();
    assertThat(response.code()).isEqualTo(200);
  }

  private Response createSecret(CreateSecretRequest request) throws Exception {
    String body = mapper.writeValueAsString(request);
    Request post = new Request.Builder()
        .post(RequestBody.create(KeywhizClient.JSON, body))
        .url(testUrl("/automation/secrets"))
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .build();

    return mutualSslClient.newCall(post).execute();
  }

  private Response createGroup(String name) throws Exception {
    CreateGroupRequest
        request = new CreateGroupRequest(name, "description", ImmutableMap.of());
    String body = mapper.writeValueAsString(request);
    Request post = new Request.Builder()
        .post(RequestBody.create(KeywhizClient.JSON, body))
        .url(testUrl("/automation/groups"))
        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
        .addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .build();

    return mutualSslClient.newCall(post).execute();
  }

  private AutomationSecretResponse getSecretByName(String name) throws Exception {
    Request get = new Request.Builder()
        .get()
        .url(testUrl(String.format("/automation/secrets?name=%s", name)))
        .build();
    Response response = mutualSslClient.newCall(get).execute();
    List<AutomationSecretResponse>
        secrets = mapper.readerForListOf(AutomationSecretResponse.class).readValue(response.body().bytes());
    return Iterables.getOnlyElement(secrets);
  }
}
