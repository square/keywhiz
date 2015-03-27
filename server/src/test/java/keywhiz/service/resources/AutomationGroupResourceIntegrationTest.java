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
import keywhiz.api.CreateGroupRequest;
import keywhiz.api.GroupDetailResponse;
import keywhiz.client.KeywhizClient;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.assertj.core.api.Assertions.assertThat;

public class AutomationGroupResourceIntegrationTest {
  ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
  OkHttpClient mutualSslClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    mutualSslClient = TestClients.mutualSslClient();
  }

  @Test
  public void findGroup() throws Exception {
    Request get = new Request.Builder()
        .get()
        .url("/automation/groups?name=Web")
        .addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .build();

    Response response = mutualSslClient.newCall(get).execute();
    assertThat(response.code()).isEqualTo(200);

    GroupDetailResponse groupedResponse = mapper.readValue(response.body().string(), GroupDetailResponse.class);
    assertThat(groupedResponse.getId()).isEqualTo(918);
  }

  @Test
  public void findGroupNotFound() throws Exception {
    Request get = new Request.Builder()
        .get()
        .url("/automation/groups?name=non-existent-group")
        .build();

    Response response = mutualSslClient.newCall(get).execute();
    assertThat(response.code()).isEqualTo(404);
  }

  @Test
  public void createGroup() throws Exception {
    CreateGroupRequest request = new CreateGroupRequest("newgroup","group-description");
    String body = mapper.writeValueAsString(request);
    Request post = new Request.Builder()
        .post(RequestBody.create(KeywhizClient.JSON, body))
        .url("/automation/groups")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .build();

    Response response = mutualSslClient.newCall(post).execute();
    assertThat(response.code()).isEqualTo(200);
  }

  @Test
  public void createGroupRedundant() throws Exception {
    CreateGroupRequest request = new CreateGroupRequest("Web","group-description");
    String body = mapper.writeValueAsString(request);
    Request post = new Request.Builder()
        .post(RequestBody.create(KeywhizClient.JSON, body))
        .url("/automation/groups")
        .addHeader("Content-Type", MediaType.APPLICATION_JSON)
        .addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .build();

    Response response = mutualSslClient.newCall(post).execute();
    assertThat(response.code()).isEqualTo(409);
  }
}
